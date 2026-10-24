
package com.omran.notizapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query("""
        WITH RECURSIVE ancestors(id, parentId, deletedAt) AS (
            SELECT id, parentId, deletedAt
            FROM categories
            WHERE id = :categoryId

            UNION

            SELECT c.id, c.parentId, c.deletedAt
            FROM categories c
            JOIN ancestors a ON c.id = a.parentId
        )
        SELECT n.* FROM notes n
        WHERE n.categoryId = :categoryId
          AND n.deletedAt IS NULL
          AND EXISTS (SELECT 1 FROM ancestors)
          AND NOT EXISTS (
              SELECT 1 FROM ancestors
              WHERE deletedAt IS NOT NULL
          )
        ORDER BY n.updatedAt DESC, n.id DESC
    """)
    fun observeNotes(
        categoryId: Long
    ): Flow<List<NoteEntity>>

    @Query("""
        WITH RECURSIVE ancestors(id, parentId, deletedAt) AS (
            SELECT c.id, c.parentId, c.deletedAt
            FROM categories c
            JOIN notes n ON n.categoryId = c.id
            WHERE n.id = :noteId

            UNION

            SELECT c.id, c.parentId, c.deletedAt
            FROM categories c
            JOIN ancestors a ON c.id = a.parentId
        )
        SELECT n.* FROM notes n
        WHERE n.id = :noteId
          AND n.deletedAt IS NULL
          AND EXISTS (SELECT 1 FROM ancestors)
          AND NOT EXISTS (
              SELECT 1 FROM ancestors
              WHERE deletedAt IS NOT NULL
          )
        LIMIT 1
    """)
    fun observeNote(
        noteId: Long
    ): Flow<NoteEntity?>

    @Query("""
        WITH RECURSIVE ancestors(id, parentId, deletedAt) AS (
            SELECT c.id, c.parentId, c.deletedAt
            FROM categories c
            JOIN notes n ON n.categoryId = c.id
            WHERE n.id = :noteId

            UNION

            SELECT c.id, c.parentId, c.deletedAt
            FROM categories c
            JOIN ancestors a ON c.id = a.parentId
        )
        SELECT n.* FROM notes n
        WHERE n.id = :noteId
          AND n.deletedAt IS NULL
          AND EXISTS (SELECT 1 FROM ancestors)
          AND NOT EXISTS (
              SELECT 1 FROM ancestors
              WHERE deletedAt IS NOT NULL
          )
        LIMIT 1
    """)
    suspend fun getNoteById(
        noteId: Long
    ): NoteEntity?

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)
}
