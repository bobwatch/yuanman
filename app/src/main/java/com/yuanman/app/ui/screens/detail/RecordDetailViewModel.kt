package com.yuanman.app.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yuanman.app.data.local.entity.RecordWithCategory
import com.yuanman.app.data.repository.RecordRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class RecordDetailUiState(
    val recordWithCategory: RecordWithCategory? = null,
    val deletedRecord: RecordWithCategory? = null,
    val isCopiedSuccess: Boolean = false,
    val isCopying: Boolean = false,
    val isDeleting: Boolean = false,
    val isUndoing: Boolean = false,
    val isLoading: Boolean = true
) {
    val displayRecord: RecordWithCategory?
        get() = recordWithCategory ?: deletedRecord

    val isDeleted: Boolean
        get() = deletedRecord != null
}

class RecordDetailViewModel(
    private val recordId: Long,
    private val recordRepository: RecordRepository
) : ViewModel() {

    private val _deletedRecord = MutableStateFlow<RecordWithCategory?>(null)
    private val _isCopiedSuccess = MutableStateFlow(false)
    private val _isCopying = MutableStateFlow(false)
    private val _isDeleting = MutableStateFlow(false)
    private val _isUndoing = MutableStateFlow(false)
    private val _operationErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)

    val operationErrors: SharedFlow<String> = _operationErrors.asSharedFlow()

    val uiState: StateFlow<RecordDetailUiState> = combine(
        recordRepository.getRecordById(recordId),
        _deletedRecord,
        _isCopiedSuccess,
        _isCopying,
        _isDeleting
    ) { recordWithCategory, deletedRecord, isCopied, isCopying, isDeleting ->
        RecordDetailUiState(
            recordWithCategory = recordWithCategory,
            deletedRecord = deletedRecord,
            isCopiedSuccess = isCopied,
            isCopying = isCopying,
            isDeleting = isDeleting,
            isLoading = false
        )
    }.combine(_isUndoing) { state, isUndoing ->
        state.copy(isUndoing = isUndoing)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RecordDetailUiState(isLoading = true)
    )

    fun deleteRecord() {
        if (_isDeleting.value || uiState.value.isDeleted) return
        val current = uiState.value.recordWithCategory ?: return

        viewModelScope.launch {
            _isDeleting.value = true
            try {
                recordRepository.deleteRecordById(recordId)
                _deletedRecord.value = current
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _operationErrors.emit("删除失败，请稍后重试")
            } finally {
                _isDeleting.value = false
            }
        }
    }

    fun undoDelete() {
        val deleted = _deletedRecord.value ?: return
        if (_isUndoing.value) return

        viewModelScope.launch {
            _isUndoing.value = true
            try {
                recordRepository.insertRecord(deleted.record)
                _deletedRecord.value = null
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _operationErrors.emit("撤销失败，请稍后重试")
            } finally {
                _isUndoing.value = false
            }
        }
    }

    fun copyRecord() {
        if (_isCopying.value) return
        val current = uiState.value.recordWithCategory?.record ?: return

        viewModelScope.launch {
            _isCopying.value = true
            try {
                val now = System.currentTimeMillis()
                val duplicate = current.copy(
                    id = 0L,
                    recordTime = now,
                    createdAt = now,
                    updatedAt = now
                )
                recordRepository.insertRecord(duplicate)
                _isCopiedSuccess.value = true
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _operationErrors.emit("复制失败，请稍后重试")
            } finally {
                _isCopying.value = false
            }
        }
    }

    fun resetCopiedFlag() {
        _isCopiedSuccess.value = false
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
