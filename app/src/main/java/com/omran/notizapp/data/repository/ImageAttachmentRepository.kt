package com.omran.notizapp.data.repository

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.room.withTransaction
import com.omran.notizapp.data.local.AppDatabase
import com.omran.notizapp.data.local.ImageAttachmentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID

class ImageAttachmentRepository(
    context: Context,
    private val database: AppDatabase
) {
    private val appContext = context.applicationContext
    private val directory = File(appContext.filesDir, "note_images")
    private val dao = database.imageAttachmentDao()
    private val cleaner = ImageFileCleaner(appContext, database)

    companion object {
        private const val MAX_BYTES = 25L * 1024L * 1024L
        private const val CLEANUP_GRACE_PERIOD_MS = 24L * 60L * 60L * 1000L
        private val validName = Regex("[0-9a-fA-F-]{36}\\.(jpg|png|webp|heic|avif)")
    }

    fun observe(noteId: Long): Flow<List<ImageAttachmentEntity>> =
        dao.observeForNote(noteId)

    fun fileFor(image: ImageAttachmentEntity): File {
        require(validName.matches(image.fileName)) {
            "Ungültiger Bilddateiname."
        }
        return File(directory, image.fileName)
    }

    /**
     * Kopiert das vom System ausgewählte Bild dauerhaft in den privaten
     * App-Speicher. Das Original in der Handygalerie bleibt unverändert.
     * Der Photo-Picker-URI muss dafür nicht dauerhaft gespeichert werden.
     */
    suspend fun addFromUri(
        noteId: Long,
        categoryId: Long,
        uri: Uri
    ): Long = withContext(Dispatchers.IO + NonCancellable) {
        val resolver = appContext.contentResolver
        val mime = resolver.getType(uri)?.lowercase(Locale.ROOT)
            ?: throw IOException("Bildformat konnte nicht ermittelt werden.")

        val extension = when (mime) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png", "image/x-png" -> "png"
            "image/webp" -> "webp"
            "image/heic", "image/heif" -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                    throw IOException("HEIC-Bilder werden auf diesem Android-Gerät nicht unterstützt.")
                }
                "heic"
            }
            "image/avif" -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    throw IOException("AVIF-Bilder werden auf diesem Android-Gerät nicht unterstützt.")
                }
                "avif"
            }
            else -> throw IOException("Dieses Bildformat wird noch nicht unterstützt ($mime).")
        }

        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Bildordner konnte nicht erstellt werden.")
        }

        val stem = UUID.randomUUID().toString()
        val filename = "$stem.$extension"
        val temp = File(directory, "$stem.part")
        val target = File(directory, filename)
        var moved = false

        try {
            var total = 0L
            val input = resolver.openInputStream(uri)
                ?: throw IOException("Bild konnte nicht geöffnet werden.")
            input.use { source ->
                temp.outputStream().buffered().use { destination ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = source.read(buffer)
                        if (read == -1) break
                        total += read
                        if (total > MAX_BYTES) {
                            throw IOException("Bild ist größer als 25 MB.")
                        }
                        destination.write(buffer, 0, read)
                    }
                }
            }
            if (total == 0L) throw IOException("Bilddatei ist leer.")

            // Vor dem Eintragen in Room verifizieren, dass Android die
            // Datei wirklich als Bild erkennt. Nur Bildmaße, kein Voll-Decode.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temp.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                throw IOException("Bilddatei ist beschädigt oder nicht lesbar.")
            }

            if (!temp.renameTo(target)) {
                throw IOException("Bild konnte nicht dauerhaft gespeichert werden.")
            }
            moved = true

            try {
                database.withTransaction {
                    val note = database.noteDao().getNoteById(noteId)
                    check(note != null && note.categoryId == categoryId) {
                        "Die Notiz ist nicht mehr verfügbar."
                    }
                    dao.insert(
                        ImageAttachmentEntity(
                            noteId = noteId,
                            fileName = filename,
                            mimeType = mime,
                            createdAt = System.currentTimeMillis(),
                            byteCount = total
                        )
                    )
                }
            } catch (e: Exception) {
                // Bei unklarem DB-Zustand Datei lieber erhalten.
                val referenced = runCatching {
                    filename in dao.allFileNames()
                }.getOrDefault(true)
                if (!referenced) target.delete()
                throw e
            }
        } finally {
            temp.delete()
            // Vor dem Verschieben verursachte Fehler: keine DB-Referenz.
            if (!moved) target.delete()
        }
    }

    suspend fun delete(imageId: Long, noteId: Long) =
        withContext(Dispatchers.IO + NonCancellable) {
            val deleted = database.withTransaction {
                val item = dao.getById(imageId) ?: return@withTransaction null
                check(item.noteId == noteId) {
                    "Das Bild gehört zu einer anderen Notiz."
                }
                check(database.noteDao().getNoteById(noteId) != null) {
                    "Die Notiz ist nicht mehr verfügbar."
                }
                dao.deleteById(imageId)
                item
            }
            if (deleted != null) {
                // Nach DB-Commit und nur wenn die Datei unreferenziert ist.
                cleaner.removeIfUnreferenced(listOf(deleted.fileName))
            }
        }

    /** Fallback nach einem Prozessabsturz zwischen Datei- und DB-Änderung. */
    suspend fun cleanupOrphanedFiles() = withContext(Dispatchers.IO) {
        if (!directory.isDirectory) return@withContext
        val referenced = dao.allFileNames().toHashSet()
        val now = System.currentTimeMillis()
        directory.listFiles()?.forEach { file ->
            if (!file.isFile) return@forEach
            val managed = validName.matches(file.name) ||
                Regex("[0-9a-fA-F-]{36}\\.part").matches(file.name)
            if (managed && file.name !in referenced &&
                file.lastModified() > 0L &&
                now - file.lastModified() > CLEANUP_GRACE_PERIOD_MS
            ) {
                file.delete()
            }
        }
    }
}
