package com.moneyhistory.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moneyhistory.app.Categories
import com.moneyhistory.app.MoneyUtils
import com.moneyhistory.app.R
import com.moneyhistory.app.RecurringExpense
import com.moneyhistory.app.Transaction
import com.moneyhistory.app.ui.theme.ExpenseRed
import com.moneyhistory.app.ui.theme.IncomeGreen
import java.util.Calendar

/**
 * 记账 / 编辑 BottomSheet —— 九宫格输金额 → 选分类 → 点「保存」。
 *
 * 备注 / 日期 / 周期账单 / 收支切换折叠在「更多选项」里（默认收起）。
 * 有已输入内容时，关闭（下滑/点外部/返回）弹「放弃 / 继续编辑」确认，
 * 确认弹窗的每条出路都会收起或重新展开 Sheet，不会残留卡死的弹窗。
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
    onDismiss: () -> Unit,
    onSave: (Transaction) -> Unit,
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

    val categories =
        if (type == Transaction.Type.EXPENSE) expenseCategories else incomeCategories
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

    // 关闭（下滑/点外部/返回）：一律直接收起，不残留窗口、不卡死。
    // 保存动作由底部「保存」按钮显式完成。
    ModalBottomSheet(
        onDismissRequest = { onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        // 整块内容随键盘上移，备注输入不被遮挡
        Column(Modifier.fillMaxWidth().imePadding()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp)
            ) {
            Text(
                text = stringResource(
                    when {
                        initial != null -> R.string.sheet_title_edit
                        prefill != null -> R.string.sheet_title_duplicate
                        else -> R.string.sheet_title_new
                    }
                ),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(12.dp))

            // 收支切换：置顶一屏可见，减少进「更多选项」的步骤
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            Spacer(Modifier.height(12.dp))

            // 金额大号等宽显示 + 连加实时合计；键盘弹起（编辑备注）时隐藏省空间
            if (!noteFocused) {
                Row(
                    Modifier.fillMaxWidth(),
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
                Spacer(Modifier.height(12.dp))
            }

            // 备注：常驻可见，聚焦时随键盘上移不遮挡
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.sheet_note_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { noteFocused = it.isFocused }
            )
            Spacer(Modifier.height(12.dp))

            // 分类（选中即可，不再点分类即保存）
            Text(
                text = stringResource(R.string.sheet_category),
                style = MaterialTheme.typography.titleSmall
            )
        }

        // 可滚动中部：分类 + 更多选项
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp)
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                categories.forEach { c ->
                    FilterChip(
                        selected = category == c,
                        onClick = { category = c },
                        label = { Text(Categories.nameOf(c)) },
                        leadingIcon = {
                            Icon(
                                categoryIcon(c),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }

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
            }
        }

        // 固定底部：提示 + 键盘 + 保存（始终可见，不被长分类列表挤出）
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            if (initial == null) {
                Text(
                    text = stringResource(R.string.sheet_hint_save),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(8.dp))

            // 「＋ 连加」与「保存」并排一行
            NumPad(
                onKey = { onNumKey(it) },
                footer = {
                    Button(
                        onClick = { doSave() },
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
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
    val container = if (selected) {
        accent.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val content = if (selected) {
        accent
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = content
        )
    }
}
