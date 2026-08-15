package com.moneyhistory.app.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.moneyhistory.app.Categories
import com.moneyhistory.app.MoneyUtils
import com.moneyhistory.app.R
import com.moneyhistory.app.RecurringExpense
import com.moneyhistory.app.Transaction
import com.moneyhistory.app.ui.theme.ExpenseRed
import com.moneyhistory.app.ui.theme.IncomeGreen
import java.util.Calendar
import kotlinx.coroutines.delay

/**
 * 记账 / 编辑 全屏底部对话框 —— 九宫格输金额 → 选分类 → 点「保存」。
 *
 * 布局（参考支付宝）：固定头部 = 标题 / 收支切换+金额同排 / 紧凑备注框；
 * 中间整块（分类宫格 + 更多选项）独占剩余空间并滚动，不挤压；
 * 底部数字键盘固定。窄屏 / 大字号下分类区始终可见可滚，不会缩成一条缝。
 * 备注聚焦时隐藏收支/金额行与数字键盘（给键盘让位），分类区保留可滚动；
 * 键盘收起后自动退出备注态，恢复完整布局。
 *
 * 备注 / 日期 / 周期账单 / 收支切换折叠在「更多选项」里（默认收起）。
 * 用全屏 Dialog 而非 ModalBottomSheet：M3 1.2.1 的 sheet 是 Popup 窗口，
 * 实测与输入法互相踩坏（键盘收起后 sheet 卡成底部一条 / 键盘弹出时窗口
 * 不调整大小），Dialog 配合 decorFitsSystemWindows=false 能收到 IME inset，
 * 再靠 imePadding 正确收缩，规避整条问题链。
 * 输入状态用 rememberSaveable，进程重建不丢。
 *
 * @param initial  编辑模式：预填原数据，保存为更新
 * @param prefill  「再记一笔」模式：以该条金额/分类/类型/备注预填，时间默认现在
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionSheet(
    initial: Transaction?,
    prefill: Transaction?,
    expenseCategories: List<String>,
    incomeCategories: List<String>,
    recentCategories: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (Transaction) -> Unit,
    onDelete: () -> Unit,
    onAddRecurring: (RecurringExpense) -> Unit
) {
    // 预填来源：编辑 > 再记一笔 > 无
    val template = initial ?: prefill

    // 金额表达式：每段一个十进制字符串，「＋」分段，保存时求和
    var segments by rememberSaveable {
        mutableStateOf(
            listOf(template?.let { MoneyUtils.formatCentsPlain(it.amountCents) } ?: "")
        )
    }
    var type by rememberSaveable {
        mutableStateOf(template?.type ?: Transaction.Type.EXPENSE)
    }

    fun defaultCategoryFor(t: Transaction.Type): String =
        (if (t == Transaction.Type.EXPENSE) expenseCategories else incomeCategories)
            .first()

    // 分类宫格：最近用过的分类置顶（稳定排序，其余按原顺序），减少翻找
    val categories = remember(type, expenseCategories, incomeCategories, recentCategories) {
        val base =
            if (type == Transaction.Type.EXPENSE) expenseCategories else incomeCategories
        val recent = recentCategories.filter { it in base }
        if (recent.isEmpty()) base else recent + base.filter { it !in recent }
    }
    var category by rememberSaveable {
        mutableStateOf(
            template?.category
                ?: defaultCategoryFor(template?.type ?: Transaction.Type.EXPENSE)
        )
    }
    var note by rememberSaveable { mutableStateOf(template?.note ?: "") }
    // 记录日期：编辑保留原时分；「再记一笔」默认现在；全新记账默认中午 12:00
    var dateMillis by rememberSaveable {
        mutableStateOf(
            Calendar.getInstance().apply {
                when {
                    initial != null -> timeInMillis = initial.timestamp
                    prefill != null -> timeInMillis = System.currentTimeMillis()
                    else -> {
                        set(Calendar.HOUR_OF_DAY, 12)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                }
            }.timeInMillis
        )
    }
    var amountError by rememberSaveable { mutableStateOf(false) }
    var moreOpen by rememberSaveable { mutableStateOf(false) }
    // 编辑态删除：确认后由外部执行（走撤销 Toast）
    var deleteConfirm by rememberSaveable { mutableStateOf(false) }
    // 数字键盘：默认展开，打开即可输金额（记账核心路径少一步，参考支付宝）；
    // 点箭头收起让分类区独占空间，点金额输入框再次展开
    var numpadExpanded by rememberSaveable { mutableStateOf(true) }

    // 周期账单（仅新增支出时可用）
    var recurringEnabled by rememberSaveable { mutableStateOf(false) }
    var cycle by rememberSaveable { mutableStateOf(RecurringExpense.Cycle.MONTHLY) }
    var dueMillis by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }

    // 备注聚焦（键盘弹起）时隐藏金额区，给键盘让出空间
    var noteFocused by remember { mutableStateOf(false) }

    val liveCents = segments.mapNotNull { MoneyUtils.parseToCents(it) }.sum()

    fun onNumKey(key: String) {
        amountError = false
        val list = segments.toMutableList()
        val last = list.last()
        when (key) {
            "⌫" -> {
                if (last.isEmpty()) {
                    if (list.size > 1) list.removeAt(list.size - 1)
                } else {
                    list[list.size - 1] = last.dropLast(1)
                }
            }
            "+" -> if (last.isNotEmpty()) list.add("")
            "." -> when {
                last.isEmpty() -> list[list.size - 1] = "0."
                !last.contains(".") -> list[list.size - 1] = last + "."
            }
            else -> when {
                last == "0" -> list[list.size - 1] = key
                last.contains(".") && last.substringAfter(".").length >= 2 -> Unit
                !last.contains(".") && last.length >= 7 -> Unit
                else -> list[list.size - 1] = last + key
            }
        }
        segments = list
    }

    fun doSave() {
        val cents = segments.mapNotNull { MoneyUtils.parseToCents(it) }.sum()
        if (cents <= 0) {
            amountError = true
            return
        }
        val trimmedNote = note.trim()
        val t = initial?.copy(
            type = type,
            amountCents = cents,
            category = category,
            note = trimmedNote,
            timestamp = dateMillis
        ) ?: Transaction(
            type = type,
            amountCents = cents,
            category = category,
            note = trimmedNote,
            timestamp = dateMillis
        )
        onSave(t)
        if (initial == null && recurringEnabled && type == Transaction.Type.EXPENSE) {
            onAddRecurring(
                RecurringExpense(
                    amountCents = cents,
                    category = category,
                    note = trimmedNote,
                    cycle = cycle,
                    nextDue = dueMillis
                )
            )
        }
    }

    // 关闭（点外部/返回）：一律直接收起，不残留窗口、不卡死。
    // 保存动作由底部「保存」按钮显式完成。
    Dialog(
        onDismissRequest = { onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            // 让 Dialog 收到 IME inset：键盘弹起/收起有感知，备注态才能自动退出
            decorFitsSystemWindows = false
        )
    ) {
        // Dialog 是独立窗口（内部 Popup 布局），有自己的一套焦点系统：
        // 焦点操作必须取本窗口的 LocalFocusManager / LocalView（主页窗口的
        // FocusOwner 管不到 Dialog 里的文本域，实测 clearFocus 无效果）
        val dialogView = LocalView.current
        val dialogFocusManager = LocalFocusManager.current

        fun exitNoteMode() {
            if (noteFocused) dialogFocusManager.clearFocus(true)
            val imm = dialogView.context.getSystemService(
                Context.INPUT_METHOD_SERVICE
            ) as? android.view.inputmethod.InputMethodManager
            imm?.hideSoftInputFromWindow(dialogView.windowToken, 0)
        }

        // 键盘收起（IME inset 归零）→ 自动退出备注态，恢复完整布局。
        // 信号读自窗口 rootView 的 rootWindowInsets（键盘弹出 1236px / 收起 0，
        // 实测可靠）；Compose 的 WindowInsets.ime 在 Dialog 里组合期读取恒为 0，
        // 不可用。M3 sheet 的 Popup 窗口收不到任何 inset，键盘收起后无从感知，
        // 是「备注态卡死」的根因。
        // 用 delay 轮询而非 withFrameNanos：Dialog 窗口不保证逐帧回调（实测键盘
        // 弹出期间帧回调停摆），delay 与帧生产无关，100ms 粒度足够捕捉收键盘。
        LaunchedEffect(Unit) {
            var lastIme = -1
            while (true) {
                val ime = dialogView.rootView.rootWindowInsets
                    ?.getInsets(android.view.WindowInsets.Type.ime())?.bottom ?: -1
                // 先判断「有键盘 → 无键盘」的跳变，再更新 lastIme（顺序不能反）
                if (lastIme > 0 && ime == 0 && noteFocused) {
                    exitNoteMode()
                }
                lastIme = ime
                delay(100)
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp
        ) {
        Column(Modifier.fillMaxSize()) {
            // 拖动手柄：下滑（超过 96dp）或点击关闭弹窗（与 GoalCreateSheet 共用）
            SheetDragHandle(onDismiss = onDismiss)
            // 固定头部：标题（编辑备注时右侧带 完成/保存）+（收支/金额）+ 备注
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp)
            ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        when {
                            initial != null -> R.string.sheet_title_edit
                            prefill != null -> R.string.sheet_title_duplicate
                            else -> R.string.sheet_title_new
                        }
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                if (noteFocused) {
                    TextButton(
                        onClick = { exitNoteMode() }
                    ) {
                        Text(stringResource(R.string.sheet_done))
                    }
                    Spacer(Modifier.width(4.dp))
                    Button(
                        onClick = { doSave() },
                        // 金额为空禁用：灰色按钮比红字报错更先一步给出反馈
                        enabled = liveCents > 0,
                        shape = MaterialTheme.shapes.large,
                        // 与底部键盘的保存按钮同高，规格统一
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text(stringResource(R.string.common_save))
                    }
                    Spacer(Modifier.width(4.dp))
                }
                // 右上角显式关闭：返回键之外的退出入口
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.sheet_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 收支切换 + 金额：同排（参考支付宝），编辑备注时隐藏给键盘让位
            if (!noteFocused) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SheetTypePill(
                            selected = type == Transaction.Type.EXPENSE,
                            label = stringResource(R.string.sheet_type_expense),
                            accent = ExpenseRed,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                type = Transaction.Type.EXPENSE
                                if (category !in expenseCategories) {
                                    category = defaultCategoryFor(Transaction.Type.EXPENSE)
                                }
                            }
                        )
                        SheetTypePill(
                            selected = type == Transaction.Type.INCOME,
                            label = stringResource(R.string.sheet_type_income),
                            accent = IncomeGreen,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                type = Transaction.Type.INCOME
                                if (category !in incomeCategories) {
                                    category = defaultCategoryFor(Transaction.Type.INCOME)
                                }
                                recurringEnabled = false
                            }
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    // 金额输入框：点击展开/收起键盘，空时占位 0
                    Surface(
                        onClick = { numpadExpanded = !numpadExpanded },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                Modifier.weight(1f),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = segments.joinToString(" + ").ifEmpty { "0" },
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = if (amountError) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            Text(
                                text = if (amountError) {
                                    stringResource(R.string.sheet_amount_error)
                                } else {
                                    "≈ ${MoneyUtils.formatCents(liveCents)}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (amountError) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                    }
                }
                }
                Spacer(Modifier.height(8.dp))
            }

            // 备注：非聚焦时一行入口（点击展开），聚焦时展开输入框。
            // 键盘弹出时金额行隐藏给键盘让位，分类区始终独占剩余空间。
            if (!noteFocused) {
                NoteEntryRow(
                    note = note,
                    onClick = { noteFocused = true }
                )
            } else {
                CompactNoteField(
                    value = note,
                    onValueChange = { note = it },
                    focused = noteFocused,
                    onFocusChanged = { noteFocused = it }
                )
            }
        }

        // 可滚动中部：分类宫格 + 更多选项，独占剩余高度。
        // 备注聚焦时也保留（不隐藏）：weight 撑满让窗口始终全高，
        // 键盘弹出时窗口随 IME 收缩、中间区吸收高度差，布局不跳变
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp)
        ) {
                // 分类
                Text(
                    text = stringResource(R.string.sheet_category),
                    style = MaterialTheme.typography.titleSmall
                )
                if (liveCents <= 0) {
                    Text(
                        text = stringResource(R.string.sheet_hint_save),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                CategoryGrid(
                    categories = categories,
                    selected = category,
                    onSelect = { category = it }
                )

                // 更多选项（默认收起）
                TextButton(onClick = { moreOpen = !moreOpen }) {
                    Text(
                        stringResource(
                            if (moreOpen) R.string.sheet_less else R.string.sheet_more
                        )
                    )
                }
                if (moreOpen) {
                    DatePickerButton(
                        label = stringResource(R.string.sheet_date),
                        millis = dateMillis,
                        onDateSelected = { dateMillis = it }
                    )

                    // 周期账单开关（仅新增支出）
                    if (initial == null && type == Transaction.Type.EXPENSE) {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.recurring_toggle),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = recurringEnabled,
                                onCheckedChange = { recurringEnabled = it }
                            )
                        }
                        if (recurringEnabled) {
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                RecurringExpense.Cycle.entries.forEach { c ->
                                    FilterChip(
                                        selected = cycle == c,
                                        onClick = { cycle = c },
                                        label = { Text(cycleLabel(c)) }
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            DatePickerButton(
                                label = stringResource(R.string.recurring_next_due),
                                millis = dueMillis,
                                onDateSelected = { dueMillis = it }
                            )
                        }
                    }
                    // 编辑态删除：不想翻回列表左滑时，在这里直接删
                    if (initial != null) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = { deleteConfirm = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.home_item_delete),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
            }
        }

        // 固定底部：数字键盘（数字键 + 连加 + 保存视为一个整体）。
        // 默认收起让分类区独占空间，点金额输入框展开/收起；备注编辑态隐藏。
        AnimatedVisibility(
            visible = !noteFocused && numpadExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            NumPad(
                onKey = { onNumKey(it) },
                onCollapse = { numpadExpanded = false },
                // 当前段为空时连加无意义，禁用并压暗
                plusEnabled = segments.last().isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
                footer = {
                        Button(
                            onClick = { doSave() },
                            enabled = liveCents > 0,
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.common_save),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                )
            }
        }
        }

        // 编辑态删除确认（复用首页同款文案）
        if (deleteConfirm) {
            AlertDialog(
                onDismissRequest = { deleteConfirm = false },
                title = { Text(stringResource(R.string.home_delete_confirm_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.home_delete_confirm_msg,
                            Categories.displayName(initial?.category ?: ""),
                            MoneyUtils.formatCents(initial?.amountCents ?: 0L)
                        )
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        deleteConfirm = false
                        onDelete()
                    }) {
                        Text(
                            stringResource(R.string.common_delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteConfirm = false }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            )
        }
    }
}

/** 备注输入框：圆角浅底 + 占位提示，比 M3 OutlinedTextField 矮一截，给分类区让高度。 */
@Composable
private fun CompactNoteField(
    value: String,
    onValueChange: (String) -> Unit,
    focused: Boolean,
    onFocusChanged: (Boolean) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    // 输入框仅在备注态才进入组合：进入即自动聚焦拉起键盘
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { onFocusChanged(it.isFocused) }
            )
            if (value.isEmpty() && !focused) {
                Text(
                    text = stringResource(R.string.sheet_note_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 备注入口行：非聚焦态的轻量入口，有内容时高亮显示预览。 */
@Composable
private fun NoteEntryRow(note: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (note.isNotEmpty()) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "📝", fontSize = 15.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = note.ifEmpty { stringResource(R.string.sheet_note_hint) },
                style = MaterialTheme.typography.bodyMedium,
                color = if (note.isNotEmpty()) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 分类宫格（参考支付宝：圆底图标 + 小字标签，紧凑多列）。 */
@Composable
private fun CategoryGrid(
    categories: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    categories.chunked(4).forEach { row ->
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            row.forEach { c ->
                CategoryTile(
                    category = c,
                    selected = selected == c,
                    onClick = { onSelect(c) },
                    modifier = Modifier.weight(1f)
                )
            }
            repeat(4 - row.size) {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CategoryTile(
    category: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 选中态颜色过渡 + 按压缩放（与全局 pressScale 体系一致），最常点的元素不「干跳」
    val circleColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        },
        label = "catCircle"
    )
    val iconTint by animateColorAsState(
        targetValue = if (selected) Color.White else MaterialTheme.colorScheme.primary,
        label = "catIcon"
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "catLabel"
    )
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .pressScale()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(circleColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = categoryIcon(category),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = Categories.displayName(category),
            style = MaterialTheme.typography.bodySmall,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = labelColor
        )
    }
}

/** 收支切换胶囊：选中态用语义色浅底 + 同色文字，未选中浅灰。 */
@Composable
private fun SheetTypePill(
    selected: Boolean,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val container by animateColorAsState(
        targetValue = if (selected) {
            accent.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        label = "pillContainer"
    )
    val content by animateColorAsState(
        targetValue = if (selected) {
            accent
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "pillContent"
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .pressScale()
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = content,
            maxLines = 1
        )
    }
}
