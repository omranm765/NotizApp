
package com.omran.notizapp.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.omran.notizapp.data.local.AppDatabase
import com.omran.notizapp.data.local.AudioAttachmentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

class AudioAttachmentRepository(
    context: Context,
    private val database: AppDatabase
) {
    private val tempDirectory =
        File(context.applicationContext.filesDir, "recordings")

    private val audioDirectory =
        File(context.applicationContext.filesDir, "note_audio")

    fun observe(
        noteId: Long
    ): Flow<List<AudioAttachmentEntity>> =
        database.audioAttachmentDao().observeForNote(noteId)

    fun fileFor(
        attachment: AudioAttachmentEntity
    ): File {
        require(
            attachment.fileName == File(attachment.fileName).name &&
                    attachment.fileName.endsWith(".m4a")
        ) {
            "Ungültiger Audiodateiname."
        }

        return File(audioDirectory, attachment.fileName)
    }

    suspend fun attach(
        noteId: Long,
        categoryId: Long,
        recordedFile: File,
        durationMs: Long
    ): Long = withContext(NonCancellable + Dispatchers.IO) {

        check(recordedFile.isFile && recordedFile.length() > 0L) {
            "Die Aufnahme ist leer oder fehlt."
        }

        check(
            recordedFile.parentFile?.canonicalFile ==
                    tempDirectory.canonicalFile
        ) {
            "Ungültiger Aufnahmeordner."
        }

        check(audioDirectory.isDirectory || audioDirectory.mkdirs()) {
            "Audioordner konnte nicht erstellt werden."
        }

        val name = "${UUID.randomUUID()}.m4a"
        val target = File(audioDirectory, name)

        if (!recordedFile.renameTo(target)) {
            throw IOException(
                "Audiodatei konnte nicht dauerhaft abgelegt werden."
            )
        }

        try {
            database.withTransaction {
                val note = database.noteDao().getNoteById(noteId)

                check(
                    note != null &&
                            note.categoryId == categoryId
                ) {
                    "Die Notiz ist nicht mehr verfügbar."
                }

                database.audioAttachmentDao().insert(
                    AudioAttachmentEntity(
                        noteId = noteId,
                        fileName = name,
                        createdAt = System.currentTimeMillis(),
                        durationMs = durationMs.coerceAtLeast(0L),
                        byteCount = target.length()
                    )
                )
            }
        } catch (exception: Exception) {
            // Bei einem Fehler prüfen, ob die DB-Zuordnung
            // eventuell bereits erfolgreich gespeichert wurde.
            val referenced = runCatching {
                database.audioAttachmentDao()
                    .allFileNames()
                    .contains(name)
            }.getOrElse {
                // Bei unklarem Zustand keine Datei löschen.
                true
            }

            if (!referenced) {
                // Aufnahme für einen erneuten Versuch erhalten.
                target.renameTo(recordedFile)
            }

            throw exception
        }
    }

    suspend fun delete(
        attachmentId: Long,
        noteId: Long
    ) = withContext(NonCancellable + Dispatchers.IO) {

        val attachment = database.withTransaction {
            val item = database.audioAttachmentDao()
                .getById(attachmentId)
                ?: return@withTransaction null

            check(item.noteId == noteId) {
                "Aufnahme gehört zu einer anderen Notiz."
            }

            check(database.noteDao().getNoteById(noteId) != null) {
                "Notiz ist nicht mehr verfügbar."
            }

            database.audioAttachmentDao()
                .deleteById(attachmentId)

            item
        }

        if (attachment != null) {
            // Nur nach erfolgreicher DB-Änderung löschen.
            // Falls das Löschen fehlschlägt, räumt die
            // spätere Bereinigung die Datei auf.
            runCatching {
                fileFor(attachment).delete()
            }
        }
    }

    suspend fun cleanupOrphanedFiles() =
        withContext(Dispatchers.IO) {
            if (!audioDirectory.isDirectory) {
                return@withContext
            }

            // Wenn die DB-Abfrage fehlschlägt, nichts löschen.
            val referencedNames = database.audioAttachmentDao()
                .allFileNames()
                .toHashSet()

            val now = System.currentTimeMillis()
            val safetyWindow = 24L * 60 * 60 * 1000

            audioDirectory.listFiles()?.forEach { file ->
                if (
                    file.isFile &&
                    file.name.endsWith(".m4a") &&
                    file.name !in referencedNames &&
                    file.lastModified() > 0L &&
                    now - file.lastModified() > safetyWindow
                ) {
                    file.delete()
                }
            }
        }
}
