package com.yuanman.app.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yuanman.app.data.local.entity.RecordWithCategory
import com.yuanman.app.data.repository.RecordRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class RecordDetailUiState(
    val recordWithCategory: RecordWithCategory? = null,
    val isDeleted: Boolean = false,
    val isLoading: Boolean = true
)

class RecordDetailViewModel(
    private val recordId: Long,
    private val recordRepository: RecordRepository
) : ViewModel() {

    private val _isDeleted = MutableStateFlow(false)

    val uiState: StateFlow<RecordDetailUiState> = combine(
        recordRepository.getRecordById(recordId),
        _isDeleted
    ) { recordWithCategory, isDeleted ->
        RecordDetailUiState(
            recordWithCategory = recordWithCategory,
            isDeleted = isDeleted,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RecordDetailUiState(isLoading = true)
    )

    fun deleteRecord() {
        viewModelScope.launch {
            recordRepository.deleteRecordById(recordId)
            _isDeleted.value = true
        }
    }

    class Factory(
        private val recordId: Long,
        private val recordRepository: RecordRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RecordDetailViewModel(recordId, recordRepository) as T
        }
    }
}
