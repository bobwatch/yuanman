package com.yuanman.app.ui.screens.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yuanman.app.data.local.entity.CategoryEntity
import com.yuanman.app.data.model.CategoryIconHelper
import com.yuanman.app.data.model.RecordType
import com.yuanman.app.data.repository.CategoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddEditCategoryUiState(
    val categoryId: Long = 0L,
    val name: String = "",
    val type: RecordType = RecordType.EXPENSE,
    val selectedIcon: String = "food",
    val selectedColor: Long = 0xFFFF5722L,
    val tagList: List<String> = emptyList(),
    val selectedIconGroup: String = "全部",
    val isEditMode: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null
)

class AddEditCategoryViewModel(
    private val categoryId: Long,
    private val initialType: RecordType?,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AddEditCategoryUiState(
            categoryId = categoryId,
            type = initialType ?: RecordType.EXPENSE,
            isEditMode = categoryId > 0L
        )
    )
    val uiState: StateFlow<AddEditCategoryUiState> = _uiState.asStateFlow()

    init {
        if (categoryId > 0L) {
            viewModelScope.launch {
                val cat = categoryRepository.getCategoryById(categoryId)
                if (cat != null) {
                    _uiState.update {
                        it.copy(
                            name = cat.name,
                            type = RecordType.fromString(cat.type),
                            selectedIcon = cat.iconName,
                            selectedColor = cat.colorHex,
                            tagList = cat.getTagList(),
                            isEditMode = true
                        )
                    }
                }
            }
        } else {
            // 新增模式：设置默认推荐标签
            val defaultTags = CategoryIconHelper.getPresetRemarks("餐饮")
            _uiState.update { it.copy(tagList = defaultTags) }
        }
    }

    fun setName(name: String) {
        val trimmed = name.take(8)
        _uiState.update { state ->
            val newTags = if (!state.isEditMode && state.tagList.isEmpty() && trimmed.isNotBlank()) {
                CategoryIconHelper.getPresetRemarks(trimmed)
            } else {
                state.tagList
            }
            state.copy(name = trimmed, tagList = newTags, errorMessage = null)
        }
    }

    fun setType(type: RecordType) {
        _uiState.update { it.copy(type = type) }
    }

    fun setIcon(iconKey: String) {
        _uiState.update { it.copy(selectedIcon = iconKey) }
    }

    fun setColor(colorHex: Long) {
        _uiState.update { it.copy(selectedColor = colorHex) }
    }

    fun setIconGroup(group: String) {
        _uiState.update { it.copy(selectedIconGroup = group) }
    }

    fun addTag(tag: String) {
        val trimmed = tag.trim().take(8)
        if (trimmed.isEmpty()) return
        _uiState.update { state ->
            if (state.tagList.contains(trimmed)) state
            else state.copy(tagList = state.tagList + trimmed)
        }
    }

    fun removeTag(tag: String) {
        _uiState.update { state ->
            state.copy(tagList = state.tagList.filterNot { it == tag })
        }
    }

    fun saveCategory() {
        val state = _uiState.value
        val trimmedName = state.name.trim()
        if (trimmedName.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "请输入分类名称") }
            return
        }

        val tagsJoined = state.tagList
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(",")

        viewModelScope.launch {
            if (state.isEditMode) {
                val existing = categoryRepository.getCategoryById(state.categoryId)
                if (existing != null) {
                    val updated = existing.copy(
                        name = trimmedName,
                        type = state.type.name,
                        iconName = state.selectedIcon,
                        colorHex = state.selectedColor,
                        tags = tagsJoined
                    )
                    categoryRepository.updateCategory(updated)
                }
            } else {
                val newCategory = CategoryEntity(
                    name = trimmedName,
                    type = state.type.name,
                    iconName = state.selectedIcon,
                    colorHex = state.selectedColor,
                    tags = tagsJoined,
                    isDefault = false,
                    sortOrder = 99
                )
                categoryRepository.insertCategory(newCategory)
            }
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    class Factory(
        private val categoryId: Long,
        private val initialType: RecordType?,
        private val categoryRepository: CategoryRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AddEditCategoryViewModel(categoryId, initialType, categoryRepository) as T
        }
    }
}
