package com.omran.notizapp.data.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.omran.notizapp.data.local.AppDatabase
import com.omran.notizapp.data.local.AudioAttachmentEntity
import com.omran.notizapp.data.local.CategoryEntity
import com.omran.notizapp.data.local.ImageAttachmentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Nicht-destruktiver Import des Formats von BackupExporter (formatVersion 1 / Room v5).
 *
 * Alle importierten Kategorien liegen unter einer NEUEN Kategorie
 * "Wiederhergestellt ...". Bisherige Notizen, Medien und Papierkorbeinträge
 * werden weder überschrieben noch gelöscht.
 *
 * Sicherungsdateien sind nicht verschlüsselt. SHA-256 prüft die Unversehrtheit
 * innerhalb des Backups, nicht die Echtheit eines fremden Backups.
 */
class BackupImporter(
    context: Context,
    private val database: AppDatabase
) {
    private val appContext = context.applicationContext

    private val audioName = Regex("[0-9a-fA-F-]{36}\\.m4a")
    private val imageName = Regex("[0-9a-fA-F-]{36}\\.(jpg|png|webp|heic|avif)")
    private val hashPattern = Regex("[0-9a-fA-F]{64}")

    // Grenzen gegen unkontrollierte Speicherbelegung / ZIP-Bomben.
    private val maxArchiveBytes = 2L * 1024 * 1024 * 1024
    private val maxMediaBytes = 2L * 1024 * 1024 * 1024
    private val maxJsonBytes = 32L * 1024 * 1024
    private val maxEntries = 20_000

    data class Result(
        val folderName: String,
        val categories: Int,
        val notes: Int,
        val audio: Int,
        val images: Int
    )

    private data class CategoryRow(
        val id: Long,
        val name: String,
        val parentId: Long?,
        val deletedAt: Long?
    )

    private data class NoteRow(
        val id: Long,
        val categoryId: Long,
        val title: String,
        val content: String,
        val createdAt: Long,
        val updatedAt: Long,
        val deletedAt: Long?
    )

    private data class AudioRow(
        val noteId: Long,
        val fileName: String,
        val createdAt: Long,
        val durationMs: Long
    )

    private data class ImageRow(
        val noteId: Long,
        val fileName: String,
        val mimeType: String,
        val createdAt: Long
    )

    private data class ValidatedBackup(
        val categories: LinkedHashMap<Long, CategoryRow>,
        val notes: LinkedHashMap<Long, NoteRow>,
        val audio: List<AudioRow>,
        val images: List<ImageRow>,
        val paths: Map<String, Long>
    )

    private data class CopiedMedia(
        val originalPath: String,
        val newName: String,
        val file: File
    )

    /**
     * Zuerst vollständige Prüfung, dann Medien kopieren, zuletzt Room-Transaktion.
     * Während des kritischen Imports verhindern wir Coroutine-Abbruch zwischen
     * dem Kopieren der Medien und dem DB-Commit.
     */
    suspend fun importFrom(source: Uri): Result =
        withContext(Dispatchers.IO + NonCancellable) {
            val archiveFile = File(
                appContext.cacheDir,
                "notizapp-import-${UUID.randomUUID()}.zip"
            )
            val copied = mutableListOf<CopiedMedia>()
            var committed = false
            try {
                appContext.contentResolver.openInputStream(source)?.use { input ->
                    archiveFile.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            total += n.toLong()
                            if (total > maxArchiveBytes) {
                                throw IOException("Backup ist zu groß (maximal 2 GiB).")
                            }
                            output.write(buffer, 0, n)
                        }
                    }
                } ?: throw IOException("Die ausgewählte Datei kann nicht geöffnet werden.")

                ZipFile(archiveFile).use { zip ->
                    val backup = validate(zip)
                    val audioDir = File(appContext.filesDir, "note_audio")
                    val imageDir = File(appContext.filesDir, "note_images")
                    if (!audioDir.isDirectory && !audioDir.mkdirs()) {
                        throw IOException("Audioordner konnte nicht erstellt werden.")
                    }
                    if (!imageDir.isDirectory && !imageDir.mkdirs()) {
                        throw IOException("Bildordner konnte nicht erstellt werden.")
                    }

                    // Nach erfolgreicher Prüfung neue, eindeutige Namen verwenden.
                    for (path in backup.paths.keys) {
                        if (path == "metadata.json") continue
                        val isAudio = path.startsWith("media/audio/")
                        val extension = path.substringAfterLast('.')
                        val targetDir = if (isAudio) audioDir else imageDir
                        var target: File
                        var newName: String
                        do {
                            newName = "${UUID.randomUUID()}.$extension"
                            target = File(targetDir, newName)
                        } while (target.exists())
                        val entry = zip.getEntry(path)
                            ?: throw IOException("Fehlender ZIP-Eintrag: $path")

                        // Das Anlegen wird erfasst, auch wenn der Kopiervorgang abbricht.
                        if (!target.createNewFile()) {
                            throw IOException("Zieldatei konnte nicht erstellt werden.")
                        }
                        copied.add(CopiedMedia(path, newName, target))
                        zip.getInputStream(entry).use { input ->
                            target.outputStream().buffered().use { output ->
                                input.copyTo(output, 64 * 1024)
                            }
                        }
                        if (target.length() != backup.paths[path]) {
                            throw IOException("Dateigröße hat sich beim Import geändert: $path")
                        }
                    }

                    val nameMap = copied.associate { it.originalPath to it.newName }
                    val sizeMap = copied.associate { it.originalPath to it.file.length() }
                    val folderName = database.withTransaction {
                        val dao = database.categoryDao()
                        val stamp = SimpleDateFormat(
                            "yyyy-MM-dd HH-mm", Locale.ROOT
                        ).format(Date())
                        val base = "Wiederhergestellt $stamp"
                        var name = base
                        var suffix = 2
                        while (dao.existsInParent(name, null)) {
                            name = "$base ($suffix)"
                            suffix++
                        }
                        val rootId = dao.insert(CategoryEntity(name = name))

                        // Eltern zuerst einfügen; keine alten Primärschlüssel überschreiben.
                        val categoryIds = mutableMapOf<Long, Long>()
                        val waiting = backup.categories.toMutableMap()
                        while (waiting.isNotEmpty()) {
                            var progressed = false
                            val iterator = waiting.iterator()
                            while (iterator.hasNext()) {
                                val (oldId, row) = iterator.next()
                                val parent = row.parentId
                                if (parent == null || categoryIds.containsKey(parent)) {
                                    categoryIds[oldId] = dao.insert(
                                        CategoryEntity(
                                            name = row.name,
                                            parentId = if (parent == null) rootId else categoryIds[parent],
                                            deletedAt = row.deletedAt
                                        )
                                    )
                                    iterator.remove()
                                    progressed = true
                                }
                            }
                            if (!progressed) {
                                throw IOException("Ungültige oder zyklische Kategoriehierarchie.")
                            }
                        }

                        val noteIds = mutableMapOf<Long, Long>()
                        val sql = database.openHelper.writableDatabase
                        for ((oldId, row) in backup.notes) {
                            val values = ContentValues().apply {
                                put("categoryId", categoryIds[row.categoryId]
                                    ?: throw IOException("Notiz ohne gültige Kategorie."))
                                put("title", row.title)
                                put("content", row.content)
                                put("createdAt", row.createdAt)
                                put("updatedAt", row.updatedAt)
                                if (row.deletedAt == null) putNull("deletedAt")
                                else put("deletedAt", row.deletedAt)
                            }
                            val newId = sql.insert("notes", 0, values)
                            if (newId <= 0L) throw IOException("Notizimport fehlgeschlagen.")
                            noteIds[oldId] = newId
                        }

                        for (row in backup.audio) {
                            val path = "media/audio/${row.fileName}"
                            database.audioAttachmentDao().insert(
                                AudioAttachmentEntity(
                                    noteId = noteIds[row.noteId]
                                        ?: throw IOException("Audio ohne gültige Notiz."),
                                    fileName = nameMap[path]
                                        ?: throw IOException("Audiodatei fehlt."),
                                    createdAt = row.createdAt,
                                    durationMs = row.durationMs,
                                    byteCount = sizeMap[path]
                                        ?: throw IOException("Audiodateigröße fehlt.")
                                )
                            )
                        }
                        for (row in backup.images) {
                            val path = "media/images/${row.fileName}"
                            database.imageAttachmentDao().insert(
                                ImageAttachmentEntity(
                                    noteId = noteIds[row.noteId]
                                        ?: throw IOException("Bild ohne gültige Notiz."),
                                    fileName = nameMap[path]
                                        ?: throw IOException("Bilddatei fehlt."),
                                    mimeType = row.mimeType,
                                    createdAt = row.createdAt,
                                    byteCount = sizeMap[path]
                                        ?: throw IOException("Bilddateigröße fehlt.")
                                )
                            )
                        }
                        name
                    }
                    committed = true
                    Result(folderName, backup.categories.size, backup.notes.size,
                        backup.audio.size, backup.images.size)
                }
            } finally {
                archiveFile.delete()
                if (!committed && copied.isNotEmpty()) {
                    // Nur Dateien löschen, deren Nichtreferenzierung wir
                    // erfolgreich prüfen können. Bei unklarem DB-Status behalten.
                    val references = runCatching {
                        database.audioAttachmentDao().allFileNames().toSet() to
                            database.imageAttachmentDao().allFileNames().toSet()
                    }.getOrNull()
                    if (references != null) {
                        for (media in copied) {
                            val referenced = if (media.originalPath.startsWith("media/audio/")) {
                                media.newName in references.first
                            } else {
                                media.newName in references.second
                            }
                            if (!referenced) media.file.delete()
                        }
                    }
                }
            }
        }

    private fun validate(zip: ZipFile): ValidatedBackup {
        val entries = linkedMapOf<String, ZipEntry>()
        val enumeration = zip.entries()
        while (enumeration.hasMoreElements()) {
            val entry = enumeration.nextElement()
            if (entry.isDirectory || entries.put(entry.name, entry) != null) {
                throw IOException("Ungültige oder doppelte Einträge im Backup.")
            }
            if (entries.size > maxEntries || !isAllowedPath(entry.name)) {
                throw IOException("Backup enthält unerwartete Dateien.")
            }
        }
        val metadataBytes = readSmall(zip, entries["metadata.json"], maxJsonBytes)
        val manifestBytes = readSmall(zip, entries["manifest.json"], maxJsonBytes)
        val metadata = JSONObject(String(metadataBytes, Charsets.UTF_8))
        val manifest = JSONObject(String(manifestBytes, Charsets.UTF_8))
        for (json in listOf(metadata, manifest)) {
            if (json.optString("format") != "com.omran.notizapp.backup" ||
                json.optInt("formatVersion", -1) != 1
            ) throw IOException("Unbekanntes Backup-Format.")
        }
        if (metadata.optInt("databaseVersion", -1) != 5) {
            throw IOException("Diese Backup-Version wird nicht unterstützt (erwartet DB v5).")
        }

        val records = manifest.getJSONArray("files")
        val expected = linkedMapOf<String, Pair<Long, String>>()
        for (i in 0 until records.length()) {
            val item = records.getJSONObject(i)
            val path = item.getString("path")
            val size = number(item, "size")
            val hash = item.getString("sha256")
            if (path == "manifest.json" || !isAllowedPath(path) ||
                size < 0L || !hashPattern.matches(hash) ||
                expected.put(path, size to hash.lowercase(Locale.ROOT)) != null
            ) throw IOException("Ungültiges Backup-Dateiverzeichnis.")
        }
        if (expected.keys != entries.keys - "manifest.json") {
            throw IOException("Dateien fehlen oder sind nicht im Manifest aufgeführt.")
        }
        if (expected["metadata.json"]?.first != metadataBytes.size.toLong()) {
            throw IOException("Metadatenlänge stimmt nicht.")
        }

        val tables = metadata.getJSONObject("tables")
        val categories = linkedMapOf<Long, CategoryRow>()
        val notes = linkedMapOf<Long, NoteRow>()
        val audios = mutableListOf<AudioRow>()
        val images = mutableListOf<ImageRow>()

        fun rows(table: String): JSONArray = tables.getJSONArray(table)
        val categoryItems = rows("categories")
        for (i in 0 until categoryItems.length()) {
            val r = categoryItems.getJSONObject(i)
            val id = number(r, "id")
            val name = r.getString("name")
            if (id <= 0L || name.isBlank() || categories.put(
                    id, CategoryRow(id, name, nullableNumber(r, "parentId"),
                        nullableNumber(r, "deletedAt"))
                ) != null
            ) throw IOException("Ungültige Kategorien im Backup.")
        }
        val noteItems = rows("notes")
        for (i in 0 until noteItems.length()) {
            val r = noteItems.getJSONObject(i)
            val id = number(r, "id")
            if (id <= 0L || notes.put(id, NoteRow(
                    id, number(r, "categoryId"), r.getString("title"),
                    r.getString("content"), number(r, "createdAt"),
                    number(r, "updatedAt"), nullableNumber(r, "deletedAt")
                )) != null
            ) throw IOException("Ungültige Notizen im Backup.")
        }
        val paths = linkedSetOf<String>()
        val audioItems = rows("audio_attachments")
        for (i in 0 until audioItems.length()) {
            val r = audioItems.getJSONObject(i)
            val name = r.getString("fileName")
            val path = "media/audio/$name"
            if (!audioName.matches(name) || !paths.add(path)) {
                throw IOException("Ungültige oder doppelte Audiodatei.")
            }
            audios.add(AudioRow(number(r, "noteId"), name,
                number(r, "createdAt"), number(r, "durationMs")))
        }
        val imageItems = rows("image_attachments")
        for (i in 0 until imageItems.length()) {
            val r = imageItems.getJSONObject(i)
            val name = r.getString("fileName")
            val path = "media/images/$name"
            if (!imageName.matches(name) || !paths.add(path)) {
                throw IOException("Ungültige oder doppelte Bilddatei.")
            }
            images.add(ImageRow(number(r, "noteId"), name,
                r.getString("mimeType"), number(r, "createdAt")))
        }
        if (categories.values.any { it.parentId != null && !categories.containsKey(it.parentId) } ||
            notes.values.any { !categories.containsKey(it.categoryId) } ||
            audios.any { !notes.containsKey(it.noteId) } ||
            images.any { !notes.containsKey(it.noteId) }
        ) throw IOException("Backup enthält ungültige Verknüpfungen.")

        if ((expected.keys - "metadata.json") != paths) {
            throw IOException("Medien stimmen nicht mit den Datenbankeinträgen überein.")
        }

        var total = 0L
        for ((path, value) in expected) {
            val (size, hash) = value
            if (path != "metadata.json" && (size <= 0L || size > maxMediaBytes)) {
                throw IOException("Ungültige Mediendateigröße.")
            }
            total += size
            if (total > maxArchiveBytes) throw IOException("Backup-Inhalt zu groß.")
            val actual = if (path == "metadata.json") {
                MessageDigest.getInstance("SHA-256").digest(metadataBytes).hex()
            } else {
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(64 * 1024)
                var readBytes = 0L
                zip.getInputStream(entries[path]!!).use { input ->
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        readBytes += n.toLong()
                        if (readBytes > size) {
                            throw IOException("Anhang ist größer als angegeben: $path")
                        }
                        digest.update(buffer, 0, n)
                    }
                }
                if (readBytes != size) throw IOException("Falsche Größe: $path")
                digest.digest().hex()
            }
            if (actual != hash) throw IOException("Prüfsumme falsch: $path")
        }
        return ValidatedBackup(categories, notes, audios, images,
            expected.mapValues { it.value.first })
    }

    private fun isAllowedPath(path: String): Boolean =
        path == "metadata.json" || path == "manifest.json" ||
            (path.startsWith("media/audio/") &&
                audioName.matches(path.removePrefix("media/audio/"))) ||
            (path.startsWith("media/images/") &&
                imageName.matches(path.removePrefix("media/images/")))

    private fun readSmall(zip: ZipFile, entry: ZipEntry?, maxBytes: Long): ByteArray {
        if (entry == null) throw IOException("metadata.json oder manifest.json fehlt.")
        val bytes = ByteArrayOutputStream()
        zip.getInputStream(entry).use { input ->
            val buffer = ByteArray(8192)
            var count = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                count += n.toLong()
                if (count > maxBytes) throw IOException("Backup-Metadaten zu groß.")
                bytes.write(buffer, 0, n)
            }
        }
        return bytes.toByteArray()
    }

    private fun number(json: JSONObject, key: String): Long {
        val value = json.opt(key)
        if (value !is Number) throw IOException("Zahl fehlt: $key")
        return value.toLong()
    }

    private fun nullableNumber(json: JSONObject, key: String): Long? {
        if (!json.has(key)) throw IOException("Spalte fehlt: $key")
        return if (json.isNull(key)) null else number(json, key)
    }

    private fun ByteArray.hex(): String = joinToString("") {
        String.format(Locale.ROOT, "%02x", it.toInt() and 0xff)
    }
}
