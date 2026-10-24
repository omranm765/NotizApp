package com.omran.notizapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("""
        SELECT * FROM categories
        WHERE parentId IS NULL
          AND deletedAt IS NULL
        ORDER BY name COLLATE NOCASE
    """)
    fun observeRootCategories(): Flow<List<CategoryEntity>>

    @Query("""
        WITH RECURSIVE ancestors(id, parentId, deletedAt) AS (
            SELECT id, parentId, deletedAt
            FROM categories
            WHERE id = :parentId

            UNION

            SELECT c.id, c.parentId, c.deletedAt
            FROM categories c
            JOIN ancestors a ON c.id = a.parentId
        )
        SELECT c.* FROM categories c
        WHERE c.parentId = :parentId
          AND c.deletedAt IS NULL
          AND EXISTS (SELECT 1 FROM ancestors)
          AND NOT EXISTS (
              SELECT 1 FROM ancestors
              WHERE deletedAt IS NOT NULL
          )
        ORDER BY c.name COLLATE NOCASE
    """)
    fun observeChildren(parentId: Long): Flow<List<CategoryEntity>>

    @Query("""
        WITH RECURSIVE ancestors(id, parentId, deletedAt) AS (
            SELECT id, parentId, deletedAt
            FROM categories
            WHERE id = :id

            UNION

            SELECT c.id, c.parentId, c.deletedAt
            FROM categories c
            JOIN ancestors a ON c.id = a.parentId
        )
        SELECT c.* FROM categories c
        WHERE c.id = :id
          AND c.deletedAt IS NULL
          AND NOT EXISTS (
              SELECT 1 FROM ancestors
              WHERE deletedAt IS NOT NULL
          )
        LIMIT 1
    """)
    fun observeCategory(id: Long): Flow<CategoryEntity?>

    // Zentrale Sicherheitsprüfung für bereits vorhandene Schreiboperationen.
    @Query("""
        WITH RECURSIVE ancestors(id, parentId, deletedAt) AS (
            SELECT id, parentId, deletedAt
            FROM categories
            WHERE id = :id

            UNION

            SELECT c.id, c.parentId, c.deletedAt
            FROM categories c
            JOIN ancestors a ON c.id = a.parentId
        )
        SELECT
            EXISTS (SELECT 1 FROM ancestors)
            AND NOT EXISTS (
                SELECT 1 FROM ancestors
                WHERE deletedAt IS NOT NULL
            )
    """)
    suspend fun isCategoryAccessible(id: Long): Boolean

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM categories
            WHERE name = :name COLLATE NOCASE
              AND deletedAt IS NULL
              AND (
                  (parentId IS NULL AND :parentId IS NULL)
                  OR parentId = :parentId
              )
        )
    """)
    suspend fun existsInParent(name: String, parentId: Long?): Boolean

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    // NEU: Auch gelöschte Kategorien laden, damit der Repository-Check
    // zwischen "fehlt" und "im Papierkorb" unterscheiden kann.
    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CategoryEntity?

    // NEU: Nur vom Hauptverzeichnis aus erreichbare, aktive Kategorien.
    // Unterkategorien von Papierkorb-Kategorien erscheinen nicht als Ziel.
    @Query("""
        WITH RECURSIVE accessible(id, name, parentId, deletedAt) AS (
            SELECT id, name, parentId, deletedAt
            FROM categories
            WHERE parentId IS NULL AND deletedAt IS NULL

            UNION ALL

            SELECT c.id, c.name, c.parentId, c.deletedAt
            FROM categories c
            JOIN accessible a ON c.parentId = a.id
            WHERE c.deletedAt IS NULL
        )
        SELECT * FROM accessible
        ORDER BY name COLLATE NOCASE
    """)
    fun observeAccessibleCategories(): Flow<List<CategoryEntity>>

    // NEU: Nur den Zielordner ändern, niemals Notizen/Audio-Dateien anfassen.
    @Query("""
        UPDATE categories
        SET parentId = :newParentId
        WHERE id = :id AND deletedAt IS NULL
    """)
    suspend fun moveToParent(id: Long, newParentId: Long?): Int
}
