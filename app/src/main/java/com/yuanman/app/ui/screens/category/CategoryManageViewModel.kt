package com.yuanman.app.ui.screens.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yuanman.app.data.local.entity.CategoryEntity
import com.yuanman.app.data.model.RecordType
import com.yuanman.app.data.repository.CategoryRepository
import com.yuanman.app.data.repository.PreferencesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class ManageTab(val title: String) {
    EXPENSE("支出分类"),
    INCOME("收入分类"),
    TAGS("快捷标签")
}

data class CategoryWithUsage(
    val category: CategoryEntity,
    val usageCount: Int
)

data class CategoryUiState(
    val currentTab: ManageTab = ManageTab.EXPENSE,
    val categoriesWithUsage: List<CategoryWithUsage> = emptyList(),
    val customTags: List<String> = emptyList(),
    val errorDialogMessage: String? = null,
    val isLoading: Boolean = false
)

class CategoryManageViewModel(
    private val categoryRepository: CategoryRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _currentTab = MutableStateFlow(ManageTab.EXPENSE)
    private val _errorDialogMessage = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val categoriesFlow = _currentTab.flatMapLatest { tab ->
        val recordType = when (tab) {
            ManageTab.EXPENSE -> RecordType.EXPENSE
            ManageTab.INCOME -> RecordType.INCOME
            ManageTab.TAGS -> RecordType.EXPENSE
        }
        categoryRepository.getCategoriesByType(recordType)
    }

    val uiState: StateFlow<CategoryUiState> = combine(
        _currentTab,
        categoriesFlow,
        preferencesRepository.customTags,
        _errorDialogMessage
    ) { tab, categories, tags, errorMsg ->
        val listWithUsage = categories.map { cat ->
            val count = categoryRepository.getCategoryUsageCount(cat.id)
            CategoryWithUsage(cat, count)
        }
        CategoryUiState(
            currentTab = tab,
            categoriesWithUsage = listWithUsage,
            customTags = tags,
            errorDialogMessage = errorMsg,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CategoryUiState(isLoading = true)
    )

    fun switchTab(tab: ManageTab) {
        _currentTab.value = tab
    }

    fun addCategory(name: String, iconName: String, colorHex: Long) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return

        val type = if (_currentTab.value == ManageTab.INCOME) RecordType.INCOME else RecordType.EXPENSE
        viewModelScope.launch {
            val newCategory = CategoryEntity(
                name = trimmed,
                type = type.name,
                iconName = iconName,
                colorHex = colorHex,
                isDefault = false,
                sortOrder = 99
            )
            categoryRepository.insertCategory(newCategory)
        }
    }

    fun updateCategory(category: CategoryEntity, newName: String, newIconName: String, newColorHex: Long) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            val updated = category.copy(
                name = trimmed,
                iconName = newIconName,
                colorHex = newColorHex
            )
            categoryRepository.updateCategory(updated)
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

    fun addTag(tag: String) {
        viewModelScope.launch {
            preferencesRepository.addCustomTag(tag)
        }
    }

    fun updateTag(oldTag: String, newTag: String) {
        viewModelScope.launch {
            preferencesRepository.updateCustomTag(oldTag, newTag)
        }
    }

    fun deleteTag(tag: String) {
        viewModelScope.launch {
            preferencesRepository.deleteCustomTag(tag)
        }
    }

    fun clearErrorDialog() {
        _errorDialogMessage.value = null
    }

    class Factory(
        private val categoryRepository: CategoryRepository,
        private val preferencesRepository: PreferencesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CategoryManageViewModel(categoryRepository, preferencesRepository) as T
        }
    }
}
