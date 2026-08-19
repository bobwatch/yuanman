package com.moneyhistory.app.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyhistory.app.Categories
import com.moneyhistory.app.MainViewModel
import com.moneyhistory.app.MessageVariant
import com.moneyhistory.app.MoneyUtils
import com.moneyhistory.app.R
import com.moneyhistory.app.RecurringExpense
import kotlinx.coroutines.launch

/** 周期账单管理：每月预计合计 + 列表（编辑/暂停/恢复/删除）+ 新建/编辑弹层。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecurringScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val recurring by viewModel.recurring.collectAsStateWithLifecycle()
    val categories by viewModel.expenseCategories.collectAsStateWithLifecycle()
    val datePattern = stringResource(R.string.date_pattern)
    val deletedText = stringResource(R.string.common_deleted)
    val createdText = stringResource(R.string.recurring_created)
    val updatedText = stringResource(R.string.recurring_updated)
    var deleteTarget by remember { mutableStateOf<RecurringExpense?>(null) }
    var editTarget by remember { mutableStateOf<RecurringExpense?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    val view = LocalView.current
    // 自定义分类为空时兜底到默认支出分类，保证新建弹层一定有的选
    val sheetCategories = remember(categories) {
        if (categories.isEmpty()) Categories.expense else categories
    }

    // 每月预计：周付 ×13/3（52/12 约分）、月付 ×1、年付 ÷12；暂停中的账单不算
    val monthlyTotal = remember(recurring) {
        recurring.filter { !it.paused }.sumOf { r ->
            when (r.cycle) {
                RecurringExpense.Cycle.WEEKLY -> r.amountCents * 13 / 3
                RecurringExpense.Cycle.MONTHLY -> r.amountCents
                RecurringExpense.Cycle.YEARLY -> r.amountCents / 12
            }
        }
    }
    val pausedCount = remember(recurring) { recurring.count { it.paused } }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        SubPageHeader(
            title = stringResource(R.string.recurring_title),
            onBack = onBack,
            actions = {
                IconButton(onClick = { showCreate = true }) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(R.string.recurring_add),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        )

        if (recurring.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    emoji = "🔁",
                    title = stringResource(R.string.recurring_empty_title),
                    subtitle = stringResource(R.string.recurring_empty_sub),
                    // 页头已有「新建」按钮，空态再给一个直达入口
                    actionLabel = stringResource(R.string.recurring_empty_action),
                    onAction = { showCreate = true }
                )
            }
        } else {
            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 4.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item(key = "total") {
                    AppCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.recurring_monthly_total),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = MoneyUtils.formatCents(monthlyTotal),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.recurring_monthly_total_sub),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (pausedCount > 0) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.recurring_paused_count, pausedCount),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                items(recurring, key = { it.id }) { r ->
                    AppCard(
                        onClick = { editTarget = r },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconTile(
                                icon = categoryIcon(r.category),
                                tint = MaterialTheme.colorScheme.primary,
                                container = MaterialTheme.colorScheme.primaryContainer,
                                size = 42.dp,
                                iconSize = 20.dp
                            )
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                // 备注为空时不显示「分类 · 」的裸分隔符
                                val rowTitle = if (r.note.isEmpty()) {
                                    Categories.displayName(r.category)
                                } else {
                                    stringResource(
                                        R.string.recurring_row_title,
                                        Categories.displayName(r.category),
                                        r.note
                                    )
                                }
                                Text(
                                    text = rowTitle,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (r.paused) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                Text(
                                    text = if (r.paused) {
                                        stringResource(R.string.recurring_paused_sub)
                                    } else {
                                        stringResource(
                                            R.string.recurring_row_subtitle,
                                            cycleLabel(r.cycle),
                                            formatSheetDate(r.nextDue, datePattern)
                                        )
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = MoneyUtils.formatCents(r.amountCents),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (r.paused) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            // 暂停/恢复：暂停后到期不再自动记账（列表可见性给足，不用进编辑页）
                            IconButton(onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                viewModel.updateRecurring(r.copy(paused = !r.paused))
                            }) {
                                Icon(
                                    if (r.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                    contentDescription = stringResource(
                                        if (r.paused) {
                                            R.string.recurring_resume_action
                                        } else {
                                            R.string.recurring_pause_action
                                        }
                                    ),
                                    tint = if (r.paused) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                            IconButton(onClick = { deleteTarget = r }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.recurring_delete),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        RecurringEditSheet(
            initial = null,
            categories = sheetCategories,
            onDismiss = { showCreate = false },
            onSave = { r ->
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                viewModel.addRecurring(r)
                viewModel.postMessage(createdText, MessageVariant.SUCCESS)
                showCreate = false
            }
        )
    }

    editTarget?.let { r ->
        RecurringEditSheet(
            initial = r,
            categories = sheetCategories,
            onDismiss = { editTarget = null },
            onSave = { updated ->
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                viewModel.updateRecurring(updated)
                viewModel.postMessage(updatedText, MessageVariant.SUCCESS)
                editTarget = null
            }
        )
    }

    deleteTarget?.let { r ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.recurring_delete_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.recurring_delete_confirm_msg,
                        Categories.displayName(r.category),
                        MoneyUtils.formatCents(r.amountCents)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    // 删除成功反馈与全 App 一致：轻震动确认
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    viewModel.removeRecurring(r.id)
                    viewModel.postMessage(deletedText, MessageVariant.INFO)
                    deleteTarget = null
                }) {
                    Text(
                        stringResource(R.string.common_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

/** 新建/编辑周期账单弹层：金额 + 分类 + 周期 + 下次扣款 + 备注（编辑时含暂停开关）。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RecurringEditSheet(
    initial: RecurringExpense?,
    categories: List<String>,
    onDismiss: () -> Unit,
    onSave: (RecurringExpense) -> Unit
) {
    var amountText by remember(initial) {
        mutableStateOf(
            if (initial != null) MoneyUtils.formatCentsPlain(initial.amountCents) else ""
        )
    }
    var category by remember(initial) {
        mutableStateOf(initial?.category ?: categories.firstOrNull().orEmpty())
    }
    var cycle by remember(initial) {
        mutableStateOf(initial?.cycle ?: RecurringExpense.Cycle.MONTHLY)
    }
    var nextDue by remember(initial) {
        mutableLongStateOf(initial?.nextDue ?: System.currentTimeMillis())
    }
    var note by remember(initial) { mutableStateOf(initial?.note ?: "") }
    var paused by remember(initial) { mutableStateOf(initial?.paused ?: false) }
    var amountError by remember { mutableStateOf(false) }
    val view = LocalView.current
    // 收起动画：点遮罩 / 下滑手柄 / 返回都先下滑收起再真正关闭，与 TransactionSheet 同语言
    val sheetOffset = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    // 全屏 Dialog 而非 M3 1.2 的 ModalBottomSheet（Popup 窗口收不到 IME inset，
    // 键盘弹出时会在键盘上方漏出背景色块——「莫名奇妙的方框」的根因）。
    // Dialog + decorFitsSystemWindows=false 能收到 IME inset，内容靠 imePadding
    // 随键盘收缩，与记账弹层同一套已验证的机制。
    Dialog(
        onDismissRequest = {
            scope.launch {
                sheetOffset.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
                onDismiss()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        DialogEdgeToEdge()
        // 关掉平台 Dialog 默认的淡入缩放，入场由 sheetOffset 滑动驱动
        (view.context as? android.app.Dialog)?.window?.setWindowAnimations(0)
        LaunchedEffect(Unit) {
            sheetOffset.animateTo(0f, tween(340, easing = FastOutSlowInEasing))
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val sheetHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
            // 遮罩：半透明黑 + 点击关闭
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(onClick = {
                        scope.launch {
                            sheetOffset.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
                            onDismiss()
                        }
                    })
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .graphicsLayer { translationY = sheetOffset.value * sheetHeightPx },
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 3.dp
            ) {
                // 内容可滚动：编辑态 + 键盘弹出时内容超高，不滚动会把底部保存按钮
                // 挤出屏幕（保存按钮显示不完整的原因）；imePadding 让内容随键盘收缩
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SheetDragHandle(onDismiss = {
                        scope.launch {
                            sheetOffset.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
                            onDismiss()
                        }
                    })
                    Text(
                        text = stringResource(
                            if (initial != null) R.string.recurring_edit_title
                            else R.string.recurring_create_title
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = {
                            amountText = it
                            amountError = false
                        },
                        label = { Text(stringResource(R.string.recurring_amount)) },
                        isError = amountError,
                        supportingText = {
                            if (amountError) Text(stringResource(R.string.sheet_amount_error))
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(R.string.recurring_category),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        categories.forEach { c ->
                            FilterChip(
                                selected = category == c,
                                onClick = { category = c },
                                label = { Text(Categories.displayName(c)) }
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.recurring_cycle),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        RecurringExpense.Cycle.entries.forEach { c ->
                            FilterChip(
                                selected = cycle == c,
                                onClick = { cycle = c },
                                label = { Text(cycleLabel(c)) }
                            )
                        }
                    }
                    DatePickerButton(
                        label = stringResource(R.string.recurring_next_due),
                        millis = nextDue,
                        onDateSelected = { nextDue = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text(stringResource(R.string.recurring_note)) },
                        placeholder = { Text(stringResource(R.string.recurring_note_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (initial != null) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.recurring_pause_action),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = stringResource(R.string.recurring_paused_sub),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = paused, onCheckedChange = { paused = it })
                        }
                    }
                    Button(
                        onClick = {
                            val cents = MoneyUtils.parseToCents(amountText)
                            if (cents == null) {
                                amountError = true
                                return@Button
                            }
                            val base = initial ?: RecurringExpense(
                                amountCents = cents,
                                category = category,
                                note = note.trim(),
                                cycle = cycle,
                                nextDue = nextDue
                            )
                            onSave(
                                base.copy(
                                    amountCents = cents,
                                    category = category,
                                    note = note.trim(),
                                    cycle = cycle,
                                    nextDue = nextDue,
                                    paused = paused
                                )
                            )
                        },
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.common_save),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}
