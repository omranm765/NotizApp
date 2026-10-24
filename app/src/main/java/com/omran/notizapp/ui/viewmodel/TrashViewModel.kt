
package com.omran.notizapp.ui.viewmodel

import android.content.res.Resources
import com.omran.notizapp.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.omran.notizapp.data.repository.TrashRepository
import com.omran.notizapp.data.repository.TrashResult

class TrashViewModel(
    private val repository: TrashRepository
) : ViewModel() {

    val trashedCategories = repository.trashedCategories
    val trashedNotes = repository.trashedNotes

    suspend fun trashCategory(id: Long): TrashResult {
        return repository.trashCategory(id)
    }

    suspend fun trashNote(id: Long): TrashResult {
        return repository.trashNote(id)
    }

    suspend fun restoreCategory(id: Long): TrashResult {
        return repository.restoreCategory(id)
    }

    suspend fun restoreNote(id: Long): TrashResult {
        return repository.restoreNote(id)
    }

    suspend fun permanentlyDeleteCategory(
        id: Long
    ): TrashResult {
        return repository.permanentlyDeleteCategory(id)
    }

    suspend fun permanentlyDeleteNote(
        id: Long
    ): TrashResult {
        return repository.permanentlyDeleteNote(id)
    }


    suspend fun deleteAllTrashedNotes(): TrashResult {
        return repository.deleteAllTrashedNotes()
    }

    suspend fun deleteAllTrashedCategories(): TrashResult {
        return repository.deleteAllTrashedCategories()
    }

    suspend fun emptyTrash(): TrashResult {
        return repository.emptyTrash()
    }


    companion object {
        fun factory(repository: TrashRepository) =
            viewModelFactory {
                initializer {
                    TrashViewModel(repository)
                }
            }
    }
}

/**
 * Gemeinsame Fehlermeldungen für unsere Oberfläche.
 */
fun trashResultMessage(
    result: TrashResult,
    successMessage: String,
    resources: Resources
): String {
    if (result == TrashResult.SUCCESS) return successMessage
    val resId = when (result) {
        TrashResult.SUCCESS -> return successMessage
        TrashResult.NOT_FOUND -> R.string.trash_result_not_found
        TrashResult.ALREADY_IN_TRASH -> R.string.trash_result_already
        TrashResult.NOT_IN_TRASH -> R.string.trash_result_not_in_trash
        TrashResult.PARENT_UNAVAILABLE -> R.string.trash_result_parent
        TrashResult.NAME_CONFLICT -> R.string.trash_result_name_conflict
    }
    return resources.getString(resId)
}
