package com.yuanman.app.ui.screens.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yuanman.app.data.local.entity.AccountEntity
import com.yuanman.app.data.local.entity.RecordWithCategory
import com.yuanman.app.data.model.*
import com.yuanman.app.data.repository.AccountRepository
import com.yuanman.app.data.repository.PreferencesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AccountsUiState(
    val accounts: List<AccountEntity> = emptyList(),
    /** All active accounts used by operations such as transfer and reconciliation. */
    val allAccounts: List<AccountEntity> = emptyList(),
    val archivedAccounts: List<AccountEntity> = emptyList(),
    val isArchivedListOpen: Boolean = false,
    val periodType: AccountPeriodType = AccountPeriodType.MONTH,
    val startDay: Int = 1,
    val comparison: AccountPeriodComparison = AccountPeriodComparison(
        currentPeriod = AccountPeriodType.getPeriodInfo(periodType = AccountPeriodType.MONTH)
    ),
    val incomeRules: List<IncomeAllocationRule> = emptyList(),
    val selectedAccountForDetail: AccountEntity? = null,
    val selectedAccountRecords: List<RecordWithCategory> = emptyList(),
    val isAddEditOpen: Boolean = false,
    val accountToEdit: AccountEntity? = null,
    val isTransferOpen: Boolean = false,
    val transferInitialFromId: Long? = null,
    val transferInitialToId: Long? = null,
    val isReconciliationOpen: Boolean = false,
    val isAllocationOpen: Boolean = false,
    val isPeriodSettingsOpen: Boolean = false,
    val privacyMode: Boolean = false,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val feedbackMessage: String? = null,
    val defaultExpenseAccountId: Long? = null
)

class AccountsViewModel(
    private val accountRepository: AccountRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _periodType = MutableStateFlow(AccountPeriodType.MONTH)
    private val _startDay = MutableStateFlow(1)
    private val _selectedAccountForDetail = MutableStateFlow<AccountEntity?>(null)
    private val _isAddEditOpen = MutableStateFlow(false)
    private val _accountToEdit = MutableStateFlow<AccountEntity?>(null)
    private val _isTransferOpen = MutableStateFlow(false)
    private val _transferInitialFromId = MutableStateFlow<Long?>(null)
    private val _transferInitialToId = MutableStateFlow<Long?>(null)
    private val _isReconciliationOpen = MutableStateFlow(false)
    private val _isAllocationOpen = MutableStateFlow(false)
    private val _isPeriodSettingsOpen = MutableStateFlow(false)
    private val _isArchivedListOpen = MutableStateFlow(false)
    private val _privacyMode = MutableStateFlow(false)
    private val _searchQuery = MutableStateFlow("")
    private val _feedbackMessage = MutableStateFlow<String?>(null)
    private val _isLoading = MutableStateFlow(false)
    private var writeOperationInFlight = false

    init {
        viewModelScope.launch {
            preferencesRepository.accountPeriodType.collectLatest { type ->
                _periodType.value = type
            }
        }
        viewModelScope.launch {
            preferencesRepository.accountPeriodStartDay.collectLatest { day ->
                _startDay.value = day
            }
        }
        viewModelScope.launch {
            preferencesRepository.privacyMode.collectLatest { enabled ->
                _privacyMode.value = enabled
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val comparisonFlow: Flow<AccountPeriodComparison> = combine(
        _periodType,
        _startDay
    ) { type, day ->
        Pair(type, day)
    }.flatMapLatest { (type, day) ->
        accountRepository.observePeriodComparison(type, day)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val selectedAccountRecordsFlow: Flow<List<RecordWithCategory>> = _selectedAccountForDetail.flatMapLatest { account ->
        if (account == null) {
            flowOf(emptyList())
        } else {
            accountRepository.getRecordsByAccountId(account.id)
        }
    }

    private data class AccountBusinessData(
        val accounts: List<AccountEntity>,
        val archivedAccounts: List<AccountEntity>,
        val periodType: AccountPeriodType,
        val startDay: Int,
        val comparison: AccountPeriodComparison,
        val incomeRules: List<IncomeAllocationRule>,
        val selectedAccountForDetail: AccountEntity?,
        val selectedAccountRecords: List<RecordWithCategory>,
        val privacyMode: Boolean,
        val searchQuery: String,
        val defaultExpenseAccountId: Long?
    )

    private data class AccountDialogsData(
        val isAddEditOpen: Boolean,
        val accountToEdit: AccountEntity?,
        val isTransferOpen: Boolean,
        val transferInitialFromId: Long?,
        val transferInitialToId: Long?,
        val isReconciliationOpen: Boolean,
        val isAllocationOpen: Boolean,
        val isPeriodSettingsOpen: Boolean,
        val isArchivedListOpen: Boolean,
        val feedback: String?
    )

    private data class AccountExtraPrefs(
        val rules: List<IncomeAllocationRule>,
        val privacy: Boolean,
        val query: String,
        val defaultExpenseAccountId: Long?
    )

    private val periodInfoFlow = combine(_periodType, _startDay) { type, day -> Pair(type, day) }
    private val accountsPairFlow = combine(
        accountRepository.activeAccounts,
        accountRepository.archivedAccounts
    ) { active, archived -> Pair(active, archived) }

    private val extraPrefsFlow = combine(
        preferencesRepository.incomeAllocationRules,
        _privacyMode,
        _searchQuery,
        preferencesRepository.defaultExpenseAccountId
    ) { rules, privacy, query, defaultExpenseId ->
        AccountExtraPrefs(rules, privacy, query, defaultExpenseId)
    }
    private val detailFlow = combine(_selectedAccountForDetail, selectedAccountRecordsFlow) { acc, records -> Pair(acc, records) }

    private val businessDataFlow: Flow<AccountBusinessData> = combine(
        accountsPairFlow,
        periodInfoFlow,
        comparisonFlow,
        extraPrefsFlow,
        detailFlow
    ) { accountsPair, periodInfo, comparison, extra, detail ->
        AccountBusinessData(
            accounts = accountsPair.first,
            archivedAccounts = accountsPair.second,
            periodType = periodInfo.first,
            startDay = periodInfo.second,
            comparison = comparison,
            incomeRules = extra.rules,
            selectedAccountForDetail = detail.first,
            selectedAccountRecords = detail.second,
            privacyMode = extra.privacy,
            searchQuery = extra.query,
            defaultExpenseAccountId = extra.defaultExpenseAccountId
        )
    }

    private val accountEditFlow = combine(_isAddEditOpen, _accountToEdit) { open, edit -> Pair(open, edit) }
    private val transferFlow = combine(_isTransferOpen, _transferInitialFromId, _transferInitialToId) { open, from, to -> Triple(open, from, to) }
    private val sheetFlow = combine(
        _isReconciliationOpen,
        _isAllocationOpen,
        _isPeriodSettingsOpen,
        _isArchivedListOpen,
        _feedbackMessage
    ) { recon, alloc, settings, archivedOpen, msg ->
        AccountSheetExtra(recon, alloc, settings, archivedOpen, msg)
    }

    private data class AccountSheetExtra(
        val isRecon: Boolean,
        val isAlloc: Boolean,
        val isSettings: Boolean,
        val isArchivedOpen: Boolean,
        val msg: String?
    )

    private val dialogsDataFlow: Flow<AccountDialogsData> = combine(
        accountEditFlow,
        transferFlow,
        sheetFlow
    ) { editPair, transferTriple, sheetExtra ->
        AccountDialogsData(
            isAddEditOpen = editPair.first,
            accountToEdit = editPair.second,
            isTransferOpen = transferTriple.first,
            transferInitialFromId = transferTriple.second,
            transferInitialToId = transferTriple.third,
            isReconciliationOpen = sheetExtra.isRecon,
            isAllocationOpen = sheetExtra.isAlloc,
            isPeriodSettingsOpen = sheetExtra.isSettings,
            isArchivedListOpen = sheetExtra.isArchivedOpen,
            feedback = sheetExtra.msg
        )
    }

    val uiState: StateFlow<AccountsUiState> = combine(
        businessDataFlow,
        dialogsDataFlow,
        _isLoading
    ) { business, dialogs, isLoading ->
        val filteredAccounts = if (business.searchQuery.isBlank()) {
            business.accounts
        } else {
            business.accounts.filter {
                it.name.contains(business.searchQuery, ignoreCase = true) ||
                    it.remark.contains(business.searchQuery, ignoreCase = true)
            }
        }

        AccountsUiState(
            accounts = filteredAccounts,
            allAccounts = business.accounts,
            archivedAccounts = business.archivedAccounts,
            isArchivedListOpen = dialogs.isArchivedListOpen,
            periodType = business.periodType,
            startDay = business.startDay,
            comparison = business.comparison,
            incomeRules = business.incomeRules,
            selectedAccountForDetail = business.selectedAccountForDetail,
            selectedAccountRecords = business.selectedAccountRecords,
            isAddEditOpen = dialogs.isAddEditOpen,
            accountToEdit = dialogs.accountToEdit,
            isTransferOpen = dialogs.isTransferOpen,
            transferInitialFromId = dialogs.transferInitialFromId,
            transferInitialToId = dialogs.transferInitialToId,
            isReconciliationOpen = dialogs.isReconciliationOpen,
            isAllocationOpen = dialogs.isAllocationOpen,
            isPeriodSettingsOpen = dialogs.isPeriodSettingsOpen,
            privacyMode = business.privacyMode,
            searchQuery = business.searchQuery,
            isLoading = isLoading,
            feedbackMessage = dialogs.feedback,
            defaultExpenseAccountId = business.defaultExpenseAccountId
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AccountsUiState()
    )

    fun togglePrivacyMode() {
        val next = !_privacyMode.value
        _privacyMode.value = next
        viewModelScope.launch {
            preferencesRepository.setPrivacyMode(next)
        }
    }

    fun initializeDefaultAccounts() {
        launchWriteOperation {
            accountRepository.ensureDefaultAccounts()
            "已初始化推荐账户体系"
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setPeriodType(type: AccountPeriodType) {
        viewModelScope.launch {
            _periodType.value = type
            preferencesRepository.setAccountPeriodType(type)
        }
    }

    fun setPeriodStartDay(day: Int) {
        viewModelScope.launch {
            _startDay.value = day
            preferencesRepository.setAccountPeriodStartDay(day)
            _isPeriodSettingsOpen.value = false
            _feedbackMessage.value = "已设置周期起始日为每月 ${day} 日"
        }
    }

    fun openAccountDetail(account: AccountEntity) {
        _selectedAccountForDetail.value = account
    }

    fun closeAccountDetail() {
        _selectedAccountForDetail.value = null
    }

    fun openPeriodSettings() {
        _isPeriodSettingsOpen.value = true
    }

    fun closePeriodSettings() {
        _isPeriodSettingsOpen.value = false
    }

    fun openArchivedList() {
        _isArchivedListOpen.value = true
    }

    fun closeArchivedList() {
        _isArchivedListOpen.value = false
    }

    fun setAccountArchived(id: Long, isArchived: Boolean) {
        launchWriteOperation {
            accountRepository.setAccountArchived(id, isArchived)
            if (_selectedAccountForDetail.value?.id == id) {
                closeAccountDetail()
            }
            if (isArchived) "已归档该账户" else "已取消归档并恢复账户"
        }
    }

    fun openAddAccount() {
        _accountToEdit.value = null
        _isAddEditOpen.value = true
    }

    fun openEditAccount(account: AccountEntity) {
        _accountToEdit.value = account
        _isAddEditOpen.value = true
    }

    fun closeAddEdit() {
        _isAddEditOpen.value = false
        _accountToEdit.value = null
    }

    fun saveAccount(
        name: String,
        type: AccountType,
        balanceCents: Long,
        includeInNetWorth: Boolean,
        icon: String,
        colorHex: String,
        remark: String,
        balanceAdjustmentRemark: String
    ) {
        val toEdit = _accountToEdit.value
        launchWriteOperation {
            if (toEdit == null) {
                accountRepository.addAccount(
                    AccountEntity(
                        name = name,
                        type = type.name,
                        balanceCents = balanceCents,
                        initialBalanceCents = balanceCents,
                        includeInNetWorth = includeInNetWorth,
                        icon = icon,
                        colorHex = colorHex,
                        remark = remark
                    )
                )
                closeAddEdit()
                "已新增账户「$name」"
            } else {
                val updated = toEdit.copy(
                    name = name,
                    type = type.name,
                    balanceCents = balanceCents,
                    includeInNetWorth = includeInNetWorth,
                    icon = icon,
                    colorHex = colorHex,
                    remark = remark
                )
                accountRepository.updateAccount(updated, balanceAdjustmentRemark)
                // If detail panel is open for this account, update it
                if (_selectedAccountForDetail.value?.id == toEdit.id) {
                    _selectedAccountForDetail.value = updated
                }
                closeAddEdit()
                "已更新账户「$name」"
            }
        }
    }

    fun deleteAccount(id: Long) {
        launchWriteOperation {
            accountRepository.deleteAccount(id)
            if (_selectedAccountForDetail.value?.id == id) {
                closeAccountDetail()
            }
            closeAddEdit()
            "已删除账户"
        }
    }

    fun openTransfer(fromId: Long? = null, toId: Long? = null) {
        _transferInitialFromId.value = fromId
        _transferInitialToId.value = toId
        _isTransferOpen.value = true
    }

    fun closeTransfer() {
        _isTransferOpen.value = false
        _transferInitialFromId.value = null
        _transferInitialToId.value = null
    }

    fun executeTransfer(fromId: Long, toId: Long, amountCents: Long, remark: String) {
        launchWriteOperation {
            val success = accountRepository.transfer(fromId, toId, amountCents, remark)
            if (success) {
                closeTransfer()
                "转账成功"
            } else {
                "转账失败，请检查账户状态"
            }
        }
    }

    fun openReconciliation() {
        _isReconciliationOpen.value = true
    }

    fun closeReconciliation() {
        _isReconciliationOpen.value = false
    }

    fun executeReconciliation(
        items: List<AccountReconciliationItem>,
        createAdjustmentRecords: Boolean
    ) {
        launchWriteOperation {
            val periodInfo = uiState.value.comparison.currentPeriod
            accountRepository.executeReconciliation(periodInfo, items, createAdjustmentRecords)
            _isReconciliationOpen.value = false
            "已完成「${periodInfo.periodName}」对账平账与资产快照归档"
        }
    }

    fun openAllocation() {
        _isAllocationOpen.value = true
    }

    fun closeAllocation() {
        _isAllocationOpen.value = false
    }

    fun saveIncomeRules(rules: List<IncomeAllocationRule>) {
        launchWriteOperation {
            preferencesRepository.setIncomeAllocationRules(rules)
            "分配规则已保存"
        }
    }

    fun executeAllocation(
        sourceAccountId: Long,
        incomeCents: Long,
        results: List<IncomeAllocationResultItem>
    ) {
        launchWriteOperation {
            val success = accountRepository.executeIncomeAllocation(sourceAccountId, incomeCents, results)
            if (success) {
                _isAllocationOpen.value = false
                "已按规则完成收入划转分流"
            } else {
                "收入分配未执行：目标账户可能已归档或删除"
            }
        }
    }

    fun setDefaultExpenseAccount(accountId: Long?) {
        launchWriteOperation {
            preferencesRepository.setDefaultExpenseAccountId(accountId)
            if (accountId != null) {
                val acc = accountRepository.getAccountByIdSync(accountId)
                "已将「${acc?.name ?: "账户"}」设为默认支出账户"
            } else {
                "已取消默认支出账户"
            }
        }
    }

    /**
     * 账户页的所有写操作共用一个闸门，避免异步提交期间重复修改余额或快照。
     */
    private fun launchWriteOperation(operation: suspend () -> String?) {
        synchronized(this) {
            if (writeOperationInFlight) return
            writeOperationInFlight = true
        }
        _isLoading.value = true
        viewModelScope.launch {
            try {
                operation()?.let { _feedbackMessage.value = it }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _feedbackMessage.value = "操作失败，请稍后重试"
            } finally {
                synchronized(this@AccountsViewModel) {
                    writeOperationInFlight = false
                }
                _isLoading.value = false
            }
        }
    }

    fun clearFeedbackMessage() {
        _feedbackMessage.value = null
    }

    class Factory(
        private val accountRepository: AccountRepository,
        private val preferencesRepository: PreferencesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AccountsViewModel::class.java)) {
                return AccountsViewModel(accountRepository, preferencesRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
