
package com.omran.notizapp.data.repository

import androidx.room.withTransaction
import com.omran.notizapp.data.local.AppDatabase
import com.omran.notizapp.data.local.NoteEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NoteRepository(
    private val database: AppDatabase
) {
    private val noteDao = database.noteDao()
    private val categoryDao = database.categoryDao()

    fun observeNotes(
        categoryId: Long
    ): Flow<List<NoteEntity>> {
        return noteDao.observeNotes(categoryId)
    }

    fun observeCategoryAvailability(
        categoryId: Long
    ): Flow<Boolean> {
        return categoryDao.observeCategory(categoryId)
            .map { category -> category != null }
    }

    suspend fun getNote(
        noteId: Long
    ): NoteEntity? {
        return noteDao.getNoteById(noteId)
    }

    suspend fun saveNote(
        categoryId: Long,
        noteId: Long?,
        title: String,
        content: String
    ): Long? = database.withTransaction {

        // Gesamte Elternkette prüfen.
        if (!categoryDao.isCategoryAccessible(categoryId)) {
            return@withTransaction null
        }

        val now = System.currentTimeMillis()

        if (noteId == null) {

            // Keine komplett leere neue Notiz anlegen.
            if (title.isBlank() && content.isBlank()) {
                return@withTransaction null
            }

            return@withTransaction noteDao.insert(
                NoteEntity(
                    categoryId = categoryId,
                    title = title,
                    content = content,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }

        // Bereits gelöschte Notizen sind hier nicht ladbar.
        val existingNote =
            noteDao.getNoteById(noteId)
                ?: return@withTransaction null

        if (existingNote.categoryId != categoryId) {
            return@withTransaction null
        }

        if (existingNote.title == title &&
            existingNote.content == content
        ) {
            return@withTransaction existingNote.id
        }

        noteDao.update(
            existingNote.copy(
                title = title,
                content = content,
                updatedAt = now
            )
        )

        existingNote.id
    }
}
