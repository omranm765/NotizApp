package com.omran.notizapp.data.repository

import android.content.Context
import com.omran.notizapp.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File

class AudioFileCleaner(
    context: Context,
    private val database: AppDatabase
) {
    private val audioDirectory = File(
        context.applicationContext.filesDir,
        "note_audio"
    )

    private val validFileName = Regex("[0-9a-fA-F-]{36}\\.m4a")

    /** Nur nicht mehr referenzierte, eigens erzeugte M4A-Dateien löschen. */
    suspend fun removeIfUnreferenced(fileNames: Collection<String>): Int =
        withContext(Dispatchers.IO + NonCancellable) {
            // DB-Abfrage muss erfolgreich sein, bevor eine Datei gelöscht wird.
            val referencedNames = database.audioAttachmentDao()
                .allFileNames()
                .toHashSet()

            var deletedCount = 0
            for (fileName in fileNames.distinct()) {
                if (!validFileName.matches(fileName)) continue
                if (fileName in referencedNames) continue

                val file = File(audioDirectory, fileName)
                if (file.isFile && file.delete()) deletedCount++
            }
            deletedCount
        }
}
