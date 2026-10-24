package com.omran.notizapp.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.omran.notizapp.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Nur Export! Diese Klasse verändert weder Datenbank noch Anhänge.
 * Import wird in einem eigenen, gesondert zu testenden Schritt ergänzt.
 *
 * Das Format enthält metadata.json und media/... in einer ZIP-Datei.
 * Die optionalen, prüfbaren SHA-256-Werte erlauben später die Validierung.
 */
class BackupExporter(
    context: Context,
    private val database: AppDatabase
) {
    private val appContext = context.applicationContext

    private val audioFileName = Regex("[0-9a-fA-F-]{36}\\.m4a")
    private val imageFileName = Regex("[0-9a-fA-F-]{36}\\.(jpg|png|webp|heic|avif)")

    private data class MediaInfo(
        val path: String,
        val file: File
    )

    suspend fun exportTo(destination: Uri) = withContext(Dispatchers.IO) {
        // Verschieben nach dem erfolgreichen ZIP-Bau reduziert das Risiko,
        // dass eine unvollständige Sicherung als erfolgreich erscheint.
        val workFile = File(appContext.cacheDir, "notizapp-backup-${UUID.randomUUID()}.zip")

        try {
            val snapshot = database.withTransaction {
                val tables = JSONObject()
                for (name in listOf("categories", "notes", "audio_attachments", "image_attachments")) {
                    tables.put(name, snapshotTable(name))
                }
                JSONObject().apply {
                    put("format", "com.omran.notizapp.backup")
                    put("formatVersion", 1)
                    put("databaseVersion", 5)
                    put("createdAt", System.currentTimeMillis())
                    put("tables", tables)
                }
            }

            val media = mutableListOf<MediaInfo>()
            val foundPaths = mutableSetOf<String>()
            val tables = snapshot.getJSONObject("tables")

            fun collect(tableName: String, directoryName: String, entryPrefix: String, validName: Regex) {
                val items = tables.getJSONArray(tableName)
                val dir = File(appContext.filesDir, directoryName)
                for (i in 0 until items.length()) {
                    val fileName = items.getJSONObject(i).getString("fileName")
                    if (!validName.matches(fileName)) {
                        throw IOException("Ungültiger Dateiname im Datenbestand: $fileName")
                    }
                    val path = "$entryPrefix/$fileName"
                    if (!foundPaths.add(path)) {
                        throw IOException("Doppelte Datei in der Datenbank: $path")
                    }
                    val file = File(dir, fileName)
                    if (!file.isFile || !file.canRead()) {
                        throw IOException("Anhang fehlt oder ist nicht lesbar: $fileName")
                    }
                    media.add(MediaInfo(path, file))
                }
            }

            collect("audio_attachments", "note_audio", "media/audio", audioFileName)
            collect("image_attachments", "note_images", "media/images", imageFileName)

            val hashList = JSONArray()
            val snapshotBytes = snapshot.toString().toByteArray(Charsets.UTF_8)

            ZipOutputStream(workFile.outputStream().buffered()).use { zip ->
                val metadataHash = MessageDigest.getInstance("SHA-256")
                    .digest(snapshotBytes).hexString()
                zip.putNextEntry(ZipEntry("metadata.json"))
                zip.write(snapshotBytes)
                zip.closeEntry()
                hashList.put(JSONObject().apply {
                    put("path", "metadata.json")
                    put("size", snapshotBytes.size)
                    put("sha256", metadataHash)
                })

                val buffer = ByteArray(64 * 1024)
                media.forEach { info ->
                    val digest = MessageDigest.getInstance("SHA-256")
                    var count = 0L
                    zip.putNextEntry(ZipEntry(info.path))
                    info.file.inputStream().buffered().use { input ->
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            zip.write(buffer, 0, n)
                            digest.update(buffer, 0, n)
                            count += n.toLong()
                        }
                    }
                    zip.closeEntry()
                    if (count == 0L) throw IOException("Leerer Anhang: ${info.path}")
                    hashList.put(JSONObject().apply {
                        put("path", info.path)
                        put("size", count)
                        put("sha256", digest.digest().hexString())
                    })
                }

                val manifest = JSONObject().apply {
                    put("format", "com.omran.notizapp.backup")
                    put("formatVersion", 1)
                    put("files", hashList)
                }
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            // Kontrolle vor dem Export: Archiv muss lesbar sein und alle
            // registrierten Anhänge plus JSON-Dateien enthalten.
            ZipFile(workFile).use { archive ->
                if (archive.getEntry("metadata.json") == null ||
                    archive.getEntry("manifest.json") == null ||
                    archive.size() != media.size + 2
                ) {
                    throw IOException("Erstelltes Backup ist unvollständig.")
                }
            }

            val output = appContext.contentResolver.openOutputStream(destination, "w")
                ?: throw IOException("Backup-Ziel konnte nicht geöffnet werden.")
            output.use { destinationStream ->
                workFile.inputStream().buffered().use { source ->
                    source.copyTo(destinationStream)
                }
                destinationStream.flush()
            }
        } finally {
            workFile.delete()
        }
    }

    /** SQL-Tabellennamen sind ausschließlich aus der festen internen Whitelist. */
    private fun snapshotTable(tableName: String): JSONArray {
        require(tableName in setOf("categories", "notes", "audio_attachments", "image_attachments"))
        val result = JSONArray()
        database.query(SimpleSQLiteQuery("SELECT * FROM `$tableName` ORDER BY id ASC")).use { cursor ->
            val columns = cursor.columnNames
            while (cursor.moveToNext()) {
                val row = JSONObject()
                for (col in columns.indices) {
                    val value: Any = when (cursor.getType(col)) {
                        android.database.Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                        android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(col)
                        android.database.Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(col)
                        android.database.Cursor.FIELD_TYPE_STRING -> cursor.getString(col)
                        else -> throw IOException("Nicht unterstützter Datentyp in $tableName")
                    }
                    row.put(columns[col], value)
                }
                result.put(row)
            }
        }
        return result
    }

    private fun ByteArray.hexString(): String = joinToString("") {
        String.format(Locale.ROOT, "%02x", it.toInt() and 0xff)
    }
}
