package com.omran.notizapp.data.local

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrashDao {

    // Direkte Papierkorb-Einträge.
    @Query(
        """
        SELECT * FROM categories
        WHERE deletedAt IS NOT NULL
        ORDER BY deletedAt DESC
    """
    )
    fun observeTrashedCategories(): Flow<List<CategoryEntity>>

    @Query(
        """
        SELECT * FROM notes
        WHERE deletedAt IS NOT NULL
        ORDER BY deletedAt DESC
    """
    )
    fun observeTrashedNotes(): Flow<List<NoteEntity>>

    // Auch gelöschte Datensätze müssen
    // innerhalb des Papierkorbs auffindbar sein.
    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategory(id: Long): CategoryEntity?

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNote(id: Long): NoteEntity?

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM categories
            WHERE deletedAt IS NULL
            AND name = :name COLLATE NOCASE
            AND id != :excludedId
            AND (
                (parentId IS NULL AND :parentId IS :parentId)
                OR parentId = :parentId
            )
        )
    """
    )
    suspend fun hasActiveNameConflict(
        name: String,
        parentId: Long?,
        excludedId: Long
    ): Boolean

    // Nur den gewählten Datensatz als gelöscht markieren.
    @Query(
        """
        UPDATE categories SET deletedAt = :timestamp
        WHERE id = :id AND deletedAt IS NULL
    """
    )
    suspend fun trashCategory(
        id: Long,
        timestamp: Long
    ): Int

    @Query(
        """
        UPDATE notes SET deletedAt = :timestamp
        WHERE id = :id AND deletedAt IS NULL
    """
    )
    suspend fun trashNote(
        id: Long,
        timestamp: Long
    ): Int

    @Query(
        """
        UPDATE categories SET deletedAt = NULL
        WHERE id = :id AND deletedAt IS NOT NULL
    """
    )
    suspend fun restoreCategory(id: Long): Int

    @Query(
        """
        UPDATE notes SET deletedAt = NULL
        WHERE id = :id AND deletedAt IS NOT NULL
    """
    )
    suspend fun restoreNote(id: Long): Int

    // Endgültiges Löschen ist nur im Papierkorb erlaubt.
    @Query(
        """
        DELETE FROM categories
        WHERE id = :id AND deletedAt IS NOT NULL
    """
    )
    suspend fun permanentlyDeleteCategory(id: Long): Int

    @Query(
        """
        DELETE FROM notes
        WHERE id = :id AND deletedAt IS NOT NULL
    """
    )
    suspend fun permanentlyDeleteNote(id: Long): Int


    @Query("""
    DELETE FROM notes
    WHERE deletedAt IS NOT NULL
""")
    suspend fun permanentlyDeleteAllTrashedNotes(): Int

    @Query("""
    DELETE FROM categories
    WHERE deletedAt IS NOT NULL
""")
    suspend fun permanentlyDeleteAllTrashedCategories(): Int

}
