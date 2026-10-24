package com.omran.notizapp.data.repository

import android.content.Context
import com.omran.notizapp.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File

class ImageFileCleaner(
    context: Context,
    private val database: AppDatabase
) {
    private val directory = File(context.applicationContext.filesDir, "note_images")
    private val filePattern = Regex(
        "[0-9a-fA-F-]{36}\\.(jpg|png|webp|heic|avif)"
    )

    // Erst NACH erfolgreicher Datenbanktransaktion aufrufen.
    suspend fun removeIfUnreferenced(names: Collection<String>): Int =
        withContext(Dispatchers.IO + NonCancellable) {
            // Bei Datenbankfehler: keine Datei anfassen.
            val referencedNames = database.imageAttachmentDao()
                .allFileNames().toHashSet()
            var count = 0
            for (name in names.distinct()) {
                if (!filePattern.matches(name) || name in referencedNames) continue
                val file = File(directory, name)
                if (file.isFile && file.delete()) count++
            }
            count
        }
}
