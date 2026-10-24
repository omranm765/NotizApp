package com.omran.notizapp.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.omran.notizapp.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

enum class TrashResult {
    SUCCESS,
    NOT_FOUND,
    ALREADY_IN_TRASH,
    NOT_IN_TRASH,
    PARENT_UNAVAILABLE,
    NAME_CONFLICT
}

class TrashRepository(
    private val database: AppDatabase,
    private val audioFileCleaner: AudioFileCleaner,
    private val imageFileCleaner: ImageFileCleaner
) {
    private val dao = database.trashDao()

    val trashedCategories = dao.observeTrashedCategories()
    val trashedNotes = dao.observeTrashedNotes()

    /**
     * Führt eine endgültige Löschung atomar in Room durch und entfernt
     * danach ausschließlich unreferenzierte Audio- und Bilddateien.
     *
     * Die Bestandsaufnahme erfolgt absichtlich innerhalb derselben Transaktion.
     * Bei Abbruch einer UI-Coroutine wird der Vorgang zu Ende ausgeführt.
     * Ein Dateisystemfehler macht eine bereits bestätigte DB-Löschung
     * nicht rückgängig; die spätere Waisenbereinigung kann erneut aufräumen.
     */
    private suspend fun permanentlyDeleteAndCleanAttachments(
        deleteOperation: suspend () -> TrashResult
    ): TrashResult = withContext(Dispatchers.IO + NonCancellable) {
        val (result, previousFiles) = database.withTransaction {
            val audioFiles = database.audioAttachmentDao().allFileNames()
            val imageFiles = database.imageAttachmentDao().allFileNames()
            val deletionResult = deleteOperation()
            deletionResult to (audioFiles to imageFiles)
        }

        if (result == TrashResult.SUCCESS) {
            try {
                audioFileCleaner.removeIfUnreferenced(previousFiles.first)
            } catch (exception: Exception) {
                Log.w(
                    "TrashRepository",
                    "Audiodateien konnten nicht vollständig bereinigt werden.",
                    exception
                )
            }
            try {
                imageFileCleaner.removeIfUnreferenced(previousFiles.second)
            } catch (exception: Exception) {
                Log.w(
                    "TrashRepository",
                    "Bilddateien konnten nicht vollständig bereinigt werden.",
                    exception
                )
            }
        }

        result
    }

    /**
     * Prüft auch alle übergeordneten Kategorien.
     * Eine gelöschte oder fehlende Elternkategorie
     * macht den Eintrag momentan nicht zugänglich.
     */
    private suspend fun parentsAreActive(startId: Long?): Boolean {
        var currentId = startId
        val visited = mutableSetOf<Long>()

        while (currentId != null) {
            if (!visited.add(currentId)) return false

            val category = dao.getCategory(currentId) ?: return false
            if (category.deletedAt != null) return false

            currentId = category.parentId
        }

        return true
    }

    suspend fun trashCategory(id: Long): TrashResult = database.withTransaction {
        val category = dao.getCategory(id)
            ?: return@withTransaction TrashResult.NOT_FOUND

        if (category.deletedAt != null) {
            return@withTransaction TrashResult.ALREADY_IN_TRASH
        }

        if (!parentsAreActive(category.parentId)) {
            return@withTransaction TrashResult.PARENT_UNAVAILABLE
        }

        val changed = dao.trashCategory(
            id = id,
            timestamp = System.currentTimeMillis()
        )

        if (changed == 1) TrashResult.SUCCESS else TrashResult.NOT_FOUND
    }

    suspend fun trashNote(id: Long): TrashResult = database.withTransaction {
        val note = dao.getNote(id)
            ?: return@withTransaction TrashResult.NOT_FOUND

        if (note.deletedAt != null) {
            return@withTransaction TrashResult.ALREADY_IN_TRASH
        }

        if (!parentsAreActive(note.categoryId)) {
            return@withTransaction TrashResult.PARENT_UNAVAILABLE
        }

        val changed = dao.trashNote(
            id = id,
            timestamp = System.currentTimeMillis()
        )

        if (changed == 1) TrashResult.SUCCESS else TrashResult.NOT_FOUND
    }

    suspend fun restoreCategory(id: Long): TrashResult = database.withTransaction {
        val category = dao.getCategory(id)
            ?: return@withTransaction TrashResult.NOT_FOUND

        if (category.deletedAt == null) {
            return@withTransaction TrashResult.NOT_IN_TRASH
        }

        if (!parentsAreActive(category.parentId)) {
            return@withTransaction TrashResult.PARENT_UNAVAILABLE
        }

        val conflict = dao.hasActiveNameConflict(
            name = category.name,
            parentId = category.parentId,
            excludedId = category.id
        )

        if (conflict) {
            return@withTransaction TrashResult.NAME_CONFLICT
        }

        val changed = dao.restoreCategory(id)
        if (changed == 1) TrashResult.SUCCESS else TrashResult.NOT_FOUND
    }

    suspend fun restoreNote(id: Long): TrashResult = database.withTransaction {
        val note = dao.getNote(id)
            ?: return@withTransaction TrashResult.NOT_FOUND

        if (note.deletedAt == null) {
            return@withTransaction TrashResult.NOT_IN_TRASH
        }

        if (!parentsAreActive(note.categoryId)) {
            return@withTransaction TrashResult.PARENT_UNAVAILABLE
        }

        val changed = dao.restoreNote(id)
        if (changed == 1) TrashResult.SUCCESS else TrashResult.NOT_FOUND
    }

    suspend fun permanentlyDeleteCategory(id: Long): TrashResult =
        permanentlyDeleteAndCleanAttachments {
            val category = dao.getCategory(id)
                ?: return@permanentlyDeleteAndCleanAttachments TrashResult.NOT_FOUND

            if (category.deletedAt == null) {
                return@permanentlyDeleteAndCleanAttachments TrashResult.NOT_IN_TRASH
            }

            val deleted = dao.permanentlyDeleteCategory(id)
            if (deleted == 1) TrashResult.SUCCESS else TrashResult.NOT_FOUND
        }

    suspend fun permanentlyDeleteNote(id: Long): TrashResult =
        permanentlyDeleteAndCleanAttachments {
            val note = dao.getNote(id)
                ?: return@permanentlyDeleteAndCleanAttachments TrashResult.NOT_FOUND

            if (note.deletedAt == null) {
                return@permanentlyDeleteAndCleanAttachments TrashResult.NOT_IN_TRASH
            }

            val deleted = dao.permanentlyDeleteNote(id)
            if (deleted == 1) TrashResult.SUCCESS else TrashResult.NOT_FOUND
        }

    suspend fun deleteAllTrashedNotes(): TrashResult =
        permanentlyDeleteAndCleanAttachments {
            val deleted = dao.permanentlyDeleteAllTrashedNotes()
            if (deleted > 0) TrashResult.SUCCESS else TrashResult.NOT_FOUND
        }

    suspend fun deleteAllTrashedCategories(): TrashResult =
        permanentlyDeleteAndCleanAttachments {
            val deleted = dao.permanentlyDeleteAllTrashedCategories()
            if (deleted > 0) TrashResult.SUCCESS else TrashResult.NOT_FOUND
        }

    suspend fun emptyTrash(): TrashResult =
        permanentlyDeleteAndCleanAttachments {
            val deletedNotes = dao.permanentlyDeleteAllTrashedNotes()
            val deletedCategories = dao.permanentlyDeleteAllTrashedCategories()
            if (deletedNotes + deletedCategories > 0) {
                TrashResult.SUCCESS
            } else {
                TrashResult.NOT_FOUND
            }
        }
}
