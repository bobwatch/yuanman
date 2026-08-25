package com.yuanman.app.ui.screens.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yuanman.app.data.local.entity.CategoryEntity
import com.yuanman.app.data.model.RecordType
import com.yuanman.app.data.repository.CategoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class CategoryWithUsage(
    val category: CategoryEntity,
    val usageCount: Int
)

data class CategoryUiState(
    val currentType: RecordType = RecordType.EXPENSE,
    val categoriesWithUsage: List<CategoryWithUsage> = emptyList(),
    val errorDialogMessage: String? = null,
    val isLoading: Boolean = false
)

class CategoryManageViewModel(
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _currentType = MutableStateFlow(RecordType.EXPENSE)
    private val _errorDialogMessage = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val categoriesFlow = _currentType.flatMapLatest { type ->
        categoryRepository.getCategoriesByType(type)
    }

    val uiState: StateFlow<CategoryUiState> = combine(
        _currentType,
        categoriesFlow,
        _errorDialogMessage
    ) { type, categories, errorMsg ->
        val listWithUsage = categories.map { cat ->
            val count = categoryRepository.getCategoryUsageCount(cat.id)
            CategoryWithUsage(cat, count)
        }
        CategoryUiState(
            currentType = type,
            categoriesWithUsage = listWithUsage,
            errorDialogMessage = errorMsg,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CategoryUiState(isLoading = true)
    )

    fun switchType(type: RecordType) {
        _currentType.value = type
    }

    fun addCategory(name: String, iconName: String, colorHex: Long, tags: List<String>) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return

        val tagsJoined = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(",")
        viewModelScope.launch {
            val newCategory = CategoryEntity(
                name = trimmed,
                type = _currentType.value.name,
                iconName = iconName,
                colorHex = colorHex,
                tags = tagsJoined,
                isDefault = false,
                sortOrder = 99
            )
            categoryRepository.insertCategory(newCategory)
        }
    }

    fun updateCategory(category: CategoryEntity, newName: String, newIconName: String, newColorHex: Long, tags: List<String>) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return

        val tagsJoined = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(",")
        viewModelScope.launch {
            val updated = category.copy(
                name = trimmed,
                iconName = newIconName,
                colorHex = newColorHex,
                tags = tagsJoined
            )
            categoryRepository.updateCategory(updated)
        }
    }

    fun updateCategoryOrder(categoryIds: List<Long>) {
        if (categoryIds.isEmpty()) return

        viewModelScope.launch {
            categoryRepository.updateCategoryOrder(categoryIds)
        }
    }

    fun deleteCategory(category: CategoryEntity) {
        viewModelScope.launch {
            val result = categoryRepository.deleteCategory(category)
            result.onFailure { error ->
                _errorDialogMessage.value = error.message ?: "删除失败：该分类正在被账单使用"
            }
        }
    }

    fun clearErrorDialog() {
        _errorDialogMessage.value = null
    }

    class Factory(
        private val categoryRepository: CategoryRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CategoryManageViewModel(categoryRepository) as T
        }
    }
}
