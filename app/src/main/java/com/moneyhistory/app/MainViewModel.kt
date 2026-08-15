package com.moneyhistory.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moneyhistory.app.sync.FamilySyncManager
import com.moneyhistory.app.widget.SpendingWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar
import kotlin.math.roundToInt

/** 当前查看的月份（month 为 1~12）。 */
data class YearMonth(val year: Int, val month: Int)

/** 筛选出指定月份的流水（保持时间倒序）。 */
internal fun List<Transaction>.ofMonth(ym: YearMonth): List<Transaction> {
    val cal = Calendar.getInstance()
    return filter {
        cal.timeInMillis = it.timestamp
        cal.get(Calendar.YEAR) == ym.year && cal.get(Calendar.MONTH) + 1 == ym.month
    }
}

/** 日期分组的 key（yyyyMMdd 整数，可比大小）。 */
internal fun dayKeyOf(timestamp: Long): Int {
    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
    return cal.get(Calendar.YEAR) * 10000 +
        (cal.get(Calendar.MONTH) + 1) * 100 +
        cal.get(Calendar.DAY_OF_MONTH)
}

/**
 * 连续记账天数：今天有记录从今天算起；今天没有则从昨天起算；
 * 昨天也没有则为 0。
 */
internal fun streakOf(transactions: List<Transaction>): Int {
    if (transactions.isEmpty()) return 0
    val days = transactions.map { dayKeyOf(it.timestamp) }.toSet()
    val cal = Calendar.getInstance()
    var key = dayKeyOf(cal.timeInMillis)
    if (key !in days) {
        cal.add(Calendar.DAY_OF_YEAR, -1)
        key = dayKeyOf(cal.timeInMillis)
        if (key !in days) return 0
    }
    var streak = 0
    while (key in days) {
        streak++
        cal.add(Calendar.DAY_OF_YEAR, -1)
        key = dayKeyOf(cal.timeInMillis)
    }
    return streak
}

class MainViewModel(private val app: Application) : AndroidViewModel(app) {

    private val store = TransactionStore.getInstance(app)
    private val recurringStore = RecurringStore.getInstance(app)
    private val categoriesStore = CategoriesStore.getInstance(app)
    private val savingsStore = SavingsStore.getInstance(app)
    private val habitsStore = HabitsStore.getInstance(app)
    private val moodStore = MoodStore.getInstance(app)

    /** 应用设置（深色模式 / 月度预算 / 引导标记）。 */
    val settings = SettingsStore.getInstance(app)

    /** 家庭同步（局域网 NSD + TCP，无服务器、无账号）。 */
    val syncManager = FamilySyncManager(app, store, viewModelScope)

    private val _transactions = MutableStateFlow(store.all())
    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()

    private val _month = MutableStateFlow(currentYearMonth())
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    // 首页流水分类筛选（统计页点分类图例直达明细用）；null = 不过滤
    private val _homeFilter = MutableStateFlow<String?>(null)
    val homeFilter: StateFlow<String?> = _homeFilter.asStateFlow()

    private val _recurring = MutableStateFlow(recurringStore.all())
    val recurring: StateFlow<List<RecurringExpense>> = _recurring.asStateFlow()

    private val _customCategories = MutableStateFlow(categoriesStore.customCategories())
    val customCategories: StateFlow<List<String>> = _customCategories.asStateFlow()

    private val _goals = MutableStateFlow(savingsStore.all())
    val goals: StateFlow<List<Goal>> = _goals.asStateFlow()

    private val _habits = MutableStateFlow(habitsStore.all())
    val habits: StateFlow<List<Habit>> = _habits.asStateFlow()

    private val _moods = MutableStateFlow(moodStore.all())
    val moods: StateFlow<Map<String, MoodEntry>> = _moods.asStateFlow()

    /** 记账/存入成功的微动效触发（自增计数，UI 监听变化播放对勾动画）。 */
    private val _successNonce = MutableStateFlow(0)
    val successNonce: StateFlow<Int> = _successNonce.asStateFlow()

    /** 目标达成撒花动画开关。 */
    private val _confettiVisible = MutableStateFlow(false)
    val confettiVisible: StateFlow<Boolean> = _confettiVisible.asStateFlow()

    /** 全局提示消息（Toast），带类型。 */
    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    /** 在线升级状态（GitHub Releases）。 */
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    /** 当前数据条数（导入覆盖前的确认提示用）。 */
    val count: Int get() = store.all().size

    /** 底部 Tab 被重复点击（当前已在该页）：页面收到后滚动回顶部。 */
    private val _tabReclick = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val tabReclick: SharedFlow<String> = _tabReclick.asSharedFlow()

    fun onTabReclick(route: String) {
        _tabReclick.tryEmit(route)
    }

    /** 子页（统计等）「记一笔」直达：首页收到后打开记账面板。 */
    private val _requestAddSheet = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val requestAddSheet: SharedFlow<Unit> = _requestAddSheet.asSharedFlow()

    fun requestAddSheet() {
        _requestAddSheet.tryEmit(Unit)
    }

    init {
        // 流水变更后刷新桌面 Widget
        store.onChanged = { SpendingWidgetProvider.notifyChanged(app) }
        viewModelScope.launch {
            syncManager.events.collect { event ->
                refresh()
                checkBadges()
                _messages.emit(
                    UiMessage(
                        text = if (event.mergedCount > 0) {
                            app.getString(R.string.sync_done_new, event.mergedCount)
                        } else {
                            app.getString(R.string.sync_done_fresh)
                        },
                        variant = MessageVariant.SUCCESS
                    )
                )
            }
        }
    }

    // ---------- 月份 ----------

    fun prevMonth() = shiftMonth(-1)

    /** 切到下个月；已是当前月时不允许查看未来月份。 */
    fun nextMonth() {
        val cur = currentYearMonth()
        val m = _month.value
        if (m.year * 12 + m.month >= cur.year * 12 + cur.month) return
        shiftMonth(1)
    }

    private fun shiftMonth(delta: Int) {
        val m = _month.value
        var month = m.month + delta
        var year = m.year
        if (month < 1) {
            month = 12
            year--
        }
        if (month > 12) {
            month = 1
            year++
        }
        _month.value = YearMonth(year, month)
    }

    /** 一键回到当前月（翻看历史月份后快速返回）。 */
    fun goToCurrentMonth() {
        _month.value = currentYearMonth()
    }

    /** 跳到指定月份（保存流水后定位到该笔所在月，避免「记完看不见」）。 */
    fun goToMonth(target: YearMonth) {
        _month.value = target
    }

    /** 设置/清除首页流水分类筛选（统计页点分类直达明细用）。 */
    fun setHomeFilter(category: String?) {
        _homeFilter.value = category
    }

    // ---------- 流水 ----------

    fun add(t: Transaction) {
        store.add(t)
        refresh()
        checkBadges()
    }

    fun update(t: Transaction) {
        store.update(t)
        refresh()
    }

    fun delete(id: String) {
        store.remove(id)
        refresh()
    }

    /** 撤销删除：清除墓碑标记。 */
    fun restore(t: Transaction) {
        store.update(t.copy(deleted = false))
        refresh()
    }

    // ---------- 导入导出 ----------

    /** 覆盖导入（现有数据自动备份）。 */
    fun importJson(content: String): Boolean {
        val ok = store.importJson(content)
        if (ok) {
            refresh()
            checkBadges()
        }
        return ok
    }

    /** 合并导入（不覆盖），返回合并条数；格式非法返回 null。 */
    fun mergeImportJson(content: String): Int? {
        val merged = store.mergeJson(content)
        if (merged != null) {
            refresh()
            checkBadges()
        }
        return merged
    }

    fun exportTo(dest: File) = store.exportTo(dest)

    fun allTransactions(): List<Transaction> = store.all()

    // ---------- 周期账单 ----------

    fun addRecurring(r: RecurringExpense) {
        recurringStore.add(r)
        _recurring.value = recurringStore.all()
    }

    fun removeRecurring(id: String) {
        recurringStore.remove(id)
        _recurring.value = recurringStore.all()
    }

    // ---------- 自定义分类 ----------

    fun expenseCategories(): List<String> = categoriesStore.expenseCategories()

    fun incomeCategories(): List<String> = categoriesStore.incomeCategories()

    fun addCustomCategory(category: String): Boolean {
        val ok = categoriesStore.add(category)
        if (ok) _customCategories.value = categoriesStore.customCategories()
        return ok
    }

    fun removeCustomCategory(category: String) {
        categoriesStore.remove(category)
        _customCategories.value = categoriesStore.customCategories()
    }

    // ---------- 攒钱目标 ----------

    fun addGoal(goal: Goal) {
        savingsStore.add(goal)
        refreshGoals()
    }

    fun deleteGoal(id: String) {
        savingsStore.remove(id)
        refreshGoals()
    }

    /**
     * 存入/取出。[recordExpense] 开启时（仅存入）同时记一笔「💎 储蓄」支出流水。
     * 触发里程碑庆祝（Snackbar），首次 100% 触发撒花动画。
     */
    fun deposit(
        goalId: String,
        amountCents: Long,
        isWithdraw: Boolean,
        recordExpense: Boolean
    ) {
        val updated = savingsStore.deposit(goalId, amountCents, isWithdraw) ?: return
        if (recordExpense && !isWithdraw) {
            val app = getApplication<Application>()
            store.add(
                Transaction(
                    type = Transaction.Type.EXPENSE,
                    amountCents = amountCents,
                    category = app.getString(R.string.goal_deposit_category),
                    note = app.getString(R.string.goal_deposit_note, updated.name)
                )
            )
            refresh()
        }
        _successNonce.value += 1
        if (!isWithdraw) {
            val percent = (updated.progress * 100).roundToInt()
            goalMilestones.forEach { m ->
                if (percent >= m && m !in updated.celebratedMilestones) {
                    savingsStore.markCelebrated(goalId, m)
                    if (m == 100) _confettiVisible.value = true
                    val textRes = when (m) {
                        25 -> R.string.milestone_25
                        50 -> R.string.milestone_50
                        75 -> R.string.milestone_75
                        else -> R.string.milestone_100
                    }
                    viewModelScope.launch {
                        _messages.emit(
                            UiMessage(app.getString(textRes), MessageVariant.SUCCESS)
                        )
                    }
                }
            }
        }
        refreshGoals()
        checkBadges()
    }

    fun dismissConfetti() {
        _confettiVisible.value = false
    }

    /** 记账保存成功：记录最近使用分类 + 触发对勾微动效。 */
    fun onTransactionSaved(t: Transaction) {
        settings.pushRecentCategory(t.type, t.category)
        _successNonce.value += 1
    }

    // ---------- 打卡 ----------

    fun addHabit(h: Habit) {
        habitsStore.add(h)
        _habits.value = habitsStore.all()
    }

    fun removeHabit(id: String) {
        habitsStore.remove(id)
        _habits.value = habitsStore.all()
    }

    /** build 类打卡/撤销，返回操作后是否已打卡。打卡成功给对勾动效 + 连续天数反馈。 */
    fun toggleCheckin(id: String): Boolean {
        val checked = habitsStore.toggleCheckin(id)
        _habits.value = habitsStore.all()
        if (checked) {
            checkBadges()
            val streak = _habits.value.firstOrNull { it.id == id }
                ?.buildStreak(DateUtils.today()) ?: 0
            _successNonce.value += 1
            viewModelScope.launch {
                _messages.emit(
                    UiMessage(
                        app.getString(R.string.habit_checked_streak, streak),
                        MessageVariant.SUCCESS
                    )
                )
            }
        }
        return checked
    }

    /** quit 类破戒重置。 */
    fun resetHabit(id: String) {
        habitsStore.resetHabit(id)
        _habits.value = habitsStore.all()
    }

    // ---------- 心情 ----------

    fun setMood(day: String, mood: Mood, note: String) {
        moodStore.set(day, mood, note)
        _moods.value = moodStore.all()
        checkBadges()
    }

    /** 供 UI 发全局 Toast 消息（由 MainActivity 顶层宿主统一展示）。 */
    fun postMessage(msg: String, variant: MessageVariant = MessageVariant.INFO) {
        viewModelScope.launch { _messages.emit(UiMessage(msg, variant)) }
    }

    // ---------- 勋章 ----------

    /** 聚合各 store 数据计算勋章；新解锁时持久化 + Snackbar + 撒花。 */
    fun checkBadges() {
        val txs = store.all()
        val habitList = habitsStore.all()
        val moodMap = moodStore.all()
        val input = BadgeInput(
            txCount = txs.size,
            txStreak = streakOf(txs),
            goalAchieved = savingsStore.all()
                .any { it.targetCents > 0 && it.savedCents >= it.targetCents },
            anyCheckin = habitList.any { it.checkins.isNotEmpty() },
            maxBuildStreak = habitList
                .filter { it.type == Habit.Type.BUILD }
                .maxOfOrNull { it.buildStreak() } ?: 0,
            maxQuitDays = habitList
                .filter { it.type == Habit.Type.QUIT }
                .maxOfOrNull { it.quitDays() } ?: 0,
            moodCount = moodMap.size,
            moodStreak = moodStreakOf(moodMap.keys),
            calmMonth = hasCalmMonth(moodMap)
        )
        val unlocked = evaluateBadges(input)
        val known = settings.badgeUnlocks.value
        val newIds = unlocked.filter { it !in known }
        if (newIds.isNotEmpty()) {
            val today = DateUtils.today()
            settings.setBadgeUnlocks(known + newIds.associateWith { today })
            newIds.forEach { id ->
                badgeById(id)?.let { badge ->
                    viewModelScope.launch {
                        _messages.emit(
                            UiMessage(
                                app.getString(
                                    R.string.badge_unlocked,
                                    app.getString(badge.titleRes)
                                ),
                                MessageVariant.SUCCESS
                            )
                        )
                    }
                }
            }
            _confettiVisible.value = true
        }
    }

    // ---------- 在线升级 ----------

    @Volatile
    private var updateChecking = false

    /** 检查 GitHub 最新发布；有新版本且未「稍后再说」时进入 Available。 */
    fun checkForUpdates() {
        if (updateChecking) return
        updateChecking = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val latest = UpdateChecker.checkLatest() ?: return@launch
                if (!UpdateChecker.isNewer(latest.versionName, BuildConfig.VERSION_NAME)) {
                    return@launch
                }
                if (latest.versionName == settings.updateDismissedVersion.value) {
                    return@launch
                }
                _updateState.value = UpdateState.Available(latest)
            } finally {
                updateChecking = false
            }
        }
    }

    /** 用户点「稍后再说」：记住该版本，不再弹窗。 */
    fun dismissUpdate() {
        val info = (_updateState.value as? UpdateState.Available)?.info ?: return
        settings.setUpdateDismissedVersion(info.versionName)
        _updateState.value = UpdateState.Idle
    }

    /** 取消下载：中止网络读取并收起下载弹窗（不提示失败——取消不是错误）。 */
    fun cancelUpdate() {
        updateCancelled = true
        _updateState.value = UpdateState.Idle
    }

    @Volatile
    private var updateCancelled = false

    /** 下载并打开安装器；下载期间弹窗实时显示进度，可取消。 */
    fun downloadUpdate() {
        val info = (_updateState.value as? UpdateState.Available)?.info ?: return
        updateCancelled = false
        viewModelScope.launch(Dispatchers.IO) {
            _updateState.value = UpdateState.Downloading(0, info.sizeBytes)
            val file = UpdateChecker.download(
                getApplication<Application>(),
                info.apkUrl,
                onProgress = { done, total ->
                    // 用户取消：抛异常让下载循环中止（download 捕获后返回 null）
                    if (updateCancelled) {
                        throw kotlin.coroutines.cancellation.CancellationException()
                    }
                    _updateState.value = UpdateState.Downloading(done, total)
                }
            )
            if (file == null) {
                _updateState.value = UpdateState.Idle
                if (!updateCancelled) {
                    _messages.emit(
                        UiMessage(
                            getApplication<Application>()
                                .getString(R.string.update_download_failed),
                            MessageVariant.ERROR
                        )
                    )
                }
            } else {
                _updateState.value = UpdateState.Idle
                try {
                    UpdateChecker.install(getApplication<Application>(), file)
                } catch (e: Exception) {
                    _messages.emit(
                        UiMessage(
                            getApplication<Application>().getString(R.string.update_install_failed),
                            MessageVariant.ERROR
                        )
                    )
                }
            }
        }
    }

    // ---------- 生命周期 ----------

    /** 进入前台：启动家庭同步 + 结算到期的周期账单 + 检查勋章 + 检查更新。 */
    fun onForeground() {
        syncManager.start()
        val settled = recurringStore.settle(
            store,
            defaultNote = getApplication<Application>()
                .getString(R.string.recurring_default_note)
        )
        if (settled > 0) {
            refresh()
            _recurring.value = recurringStore.all()
            viewModelScope.launch {
                _messages.emit(
                    UiMessage(
                        getApplication<Application>()
                            .getString(R.string.recurring_settled, settled),
                        MessageVariant.INFO
                    )
                )
            }
        }
        checkBadges()
        checkForUpdates()
    }

    /** 退后台：停止家庭同步（不做后台常驻）。 */
    fun onBackground() = syncManager.stop()

    override fun onCleared() {
        syncManager.stop()
        super.onCleared()
    }

    private fun refresh() {
        _transactions.value = store.all()
    }

    private fun refreshGoals() {
        _goals.value = savingsStore.all()
    }

    private fun currentYearMonth(): YearMonth {
        val cal = Calendar.getInstance()
        return YearMonth(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }
}
