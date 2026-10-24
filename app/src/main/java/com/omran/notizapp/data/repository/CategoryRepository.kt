package com.omran.notizapp.data.repository

import androidx.room.withTransaction
import com.omran.notizapp.data.local.AppDatabase
import com.omran.notizapp.data.local.CategoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class MoveCategoryResult {
    SUCCESS,
    NOT_FOUND,
    SOURCE_UNAVAILABLE,
    TARGET_UNAVAILABLE,
    INVALID_TARGET,
    ALREADY_THERE,
    NAME_CONFLICT
}

class CategoryRepository(
    private val database: AppDatabase
) {
    private val categoryDao = database.categoryDao()

    val rootCategories: Flow<List<CategoryEntity>> =
        categoryDao.observeRootCategories()

    fun observeChildren(parentId: Long): Flow<List<CategoryEntity>> {
        return categoryDao.observeChildren(parentId)
    }

    fun observeCategory(id: Long): Flow<CategoryEntity?> {
        return categoryDao.observeCategory(id)
    }

    suspend fun createCategory(name: String, parentId: Long? = null) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return

        database.withTransaction {
            // Keine Unterkategorie in einer gelöschten Hierarchie erstellen.
            if (parentId != null &&
                !categoryDao.isCategoryAccessible(parentId)
            ) {
                return@withTransaction
            }

            if (!categoryDao.existsInParent(cleanName, parentId)) {
                categoryDao.insert(
                    CategoryEntity(
                        name = cleanName,
                        parentId = parentId
                    )
                )
            }
        }
    }

    /**
     * NEU: Ziele für die spätere Verschieben-Auswahl.
     * Die verschobene Kategorie und ihre Unterkategorien sind ausgeschlossen.
     * Das Hauptverzeichnis (null) wird später separat in der UI angeboten.
     */
    fun observeMoveTargets(
        movingCategoryId: Long
    ): Flow<List<CategoryEntity>> {
        return categoryDao.observeAccessibleCategories().map { categories ->
            val parents = categories.associate { it.id to it.parentId }

            categories.filter { candidate ->
                var current: Long? = candidate.id
                val visited = mutableSetOf<Long>()
                var allowed = true

                while (current != null) {
                    if (current == movingCategoryId || !visited.add(current)) {
                        allowed = false
                        break
                    }
                    current = parents[current]
                }

                allowed
            }
        }
    }

    /**
     * NEU: Verschiebt eine Kategorie samt ihrer gesamten Unterstruktur.
     * Nur categories.parentId wird verändert. Notizen und Anhänge bleiben.
     */
    suspend fun moveCategory(
        categoryId: Long,
        newParentId: Long?
    ): MoveCategoryResult = database.withTransaction {
        val source = categoryDao.getById(categoryId)
            ?: return@withTransaction MoveCategoryResult.NOT_FOUND

        // Auch Vorfahren der Quellkategorie prüfen. Die Quelle darf nicht
        // unter einer gelöschten, fehlenden oder zyklischen Kategorie liegen.
        var currentSourceId: Long? = source.id
        val checkedSourceIds = mutableSetOf<Long>()
        while (currentSourceId != null) {
            if (!checkedSourceIds.add(currentSourceId)) {
                return@withTransaction MoveCategoryResult.SOURCE_UNAVAILABLE
            }
            val currentSource = categoryDao.getById(currentSourceId)
                ?: return@withTransaction MoveCategoryResult.SOURCE_UNAVAILABLE
            if (currentSource.deletedAt != null) {
                return@withTransaction MoveCategoryResult.SOURCE_UNAVAILABLE
            }
            currentSourceId = currentSource.parentId
        }

        if (source.parentId == newParentId) {
            return@withTransaction MoveCategoryResult.ALREADY_THERE
        }

        // Ziel und dessen Vorfahren prüfen: keine verschachtelten Zyklen,
        // keine gelöschten bzw. fehlenden Zielkategorien.
        var currentTargetId = newParentId
        val checkedTargetIds = mutableSetOf<Long>()
        while (currentTargetId != null) {
            if (currentTargetId == categoryId) {
                return@withTransaction MoveCategoryResult.INVALID_TARGET
            }
            if (!checkedTargetIds.add(currentTargetId)) {
                return@withTransaction MoveCategoryResult.TARGET_UNAVAILABLE
            }
            val target = categoryDao.getById(currentTargetId)
                ?: return@withTransaction MoveCategoryResult.TARGET_UNAVAILABLE
            if (target.deletedAt != null) {
                return@withTransaction MoveCategoryResult.TARGET_UNAVAILABLE
            }
            currentTargetId = target.parentId
        }

        // Keine zwei aktiven Geschwisterkategorien mit demselben Namen.
        if (categoryDao.existsInParent(source.name, newParentId)) {
            return@withTransaction MoveCategoryResult.NAME_CONFLICT
        }

        val changed = categoryDao.moveToParent(categoryId, newParentId)
        if (changed == 1) MoveCategoryResult.SUCCESS
        else MoveCategoryResult.NOT_FOUND
    }
}
