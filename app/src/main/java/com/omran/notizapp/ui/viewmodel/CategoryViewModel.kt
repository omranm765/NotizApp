package com.omran.notizapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.omran.notizapp.data.local.CategoryEntity
import com.omran.notizapp.data.repository.CategoryRepository
import com.omran.notizapp.data.repository.MoveCategoryResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class CategoryViewModel(
    private val repository: CategoryRepository
) : ViewModel() {

    fun observeCategories(parentId: Long?): Flow<List<CategoryEntity>> =
        if (parentId == null) {
            repository.rootCategories
        } else {
            repository.observeChildren(parentId)
        }

    fun observeCategory(id: Long): Flow<CategoryEntity?> =
        repository.observeCategory(id)

    fun observeMoveTargets(movingCategoryId: Long): Flow<List<CategoryEntity>> =
        repository.observeMoveTargets(movingCategoryId)

    suspend fun moveCategory(
        categoryId: Long,
        newParentId: Long?
    ): MoveCategoryResult = repository.moveCategory(categoryId, newParentId)

    fun createCategory(name: String, parentId: Long? = null) {
        viewModelScope.launch {
            repository.createCategory(name, parentId)
        }
    }

    companion object {
        fun factory(repository: CategoryRepository) =
            viewModelFactory {
                initializer {
                    CategoryViewModel(repository)
                }
            }
    }
}
