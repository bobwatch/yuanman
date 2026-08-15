package com.moneyhistory.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.with
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneyhistory.app.MoneyUtils
import com.moneyhistory.app.R
import com.moneyhistory.app.RecurringExpense
import com.moneyhistory.app.YearMonth
import com.moneyhistory.app.ui.theme.YuanmanGradientBottom
import com.moneyhistory.app.ui.theme.YuanmanGradientTop
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** 月份切换条（‹ 2026年8月 ›），月份文字带滑动动画。[contentColor] 默认跟随主题。
 *  [onTitleClick] 非空时月份标题可点击（加下划线提示），用于「一键回到本月」。 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MonthSelector(
    month: YearMonth,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    nextEnabled: Boolean = true,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    onTitleClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.month_prev),
                tint = contentColor
            )
        }
        AnimatedContent(
            targetState = month,
            transitionSpec = {
                val forward =
                    targetState.year * 12 + targetState.month >
                        initialState.year * 12 + initialState.month
                if (forward) {
                    slideInHorizontally { it } + fadeIn() with
                        slideOutHorizontally { -it } + fadeOut()
                } else {
                    slideInHorizontally { -it } + fadeIn() with
                        slideOutHorizontally { it } + fadeOut()
                }
            },
            label = "month"
        ) { m ->
            val titleModifier = if (onTitleClick != null) {
                Modifier
                    .clickable(onClick = onTitleClick)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            } else {
                Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            }
            Text(
                text = stringResource(R.string.month_format, m.year, m.month),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                textDecoration = if (onTitleClick != null) {
                    TextDecoration.Underline
                } else {
                    TextDecoration.None
                },
                modifier = titleModifier
            )
        }
        IconButton(onClick = onNext, enabled = nextEnabled) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.month_next),
                // 显式 tint 覆盖了 M3 的 disabled 色，禁用时手动压暗提示不可点
                tint = if (nextEnabled) {
                    contentColor
                } else {
                    contentColor.copy(alpha = 0.35f)
                }
            )
        }
    }
}

/** 日期选择按钮：点击弹出 M3 DatePicker，选择后保留原时分。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerButton(
    label: String,
    millis: Long,
    onDateSelected: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var show by remember { mutableStateOf(false) }
    val datePattern = stringResource(R.string.date_pattern)

    OutlinedButton(onClick = { show = true }, modifier = modifier) {
        Icon(Icons.Filled.DateRange, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(
                R.string.date_label_format,
                label,
                formatSheetDate(millis, datePattern)
            )
        )
    }

    if (show) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = utcMidnightOf(millis)
        )
        DatePickerDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { sel ->
                        onDateSelected(applyUtcDate(sel, millis))
                    }
                    show = false
                }) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { show = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/** 月度预算输入对话框；[onSave] 传入 0 表示清除预算。 */
@Composable
fun BudgetDialog(
    currentCents: Long,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit
) {
    var text by remember {
        mutableStateOf(
            if (currentCents > 0) MoneyUtils.formatCentsPlain(currentCents) else ""
        )
    }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.budget_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    error = false
                },
                label = { Text(stringResource(R.string.budget_amount_hint)) },
                isError = error,
                supportingText = {
                    if (error) Text(stringResource(R.string.sheet_amount_error))
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val cents = MoneyUtils.parseToCents(text)
                if (cents == null) {
                    error = true
                } else {
                    onSave(cents)
                    onDismiss()
                }
            }) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            Row {
                if (currentCents > 0) {
                    TextButton(onClick = {
                        onSave(0)
                        onDismiss()
                    }) {
                        Text(stringResource(R.string.budget_clear))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        }
    )
}

/** 周期文案（每周/每月/每年）。 */
@Composable
fun cycleLabel(cycle: RecurringExpense.Cycle): String = stringResource(
    when (cycle) {
        RecurringExpense.Cycle.WEEKLY -> R.string.cycle_weekly
        RecurringExpense.Cycle.MONTHLY -> R.string.cycle_monthly
        RecurringExpense.Cycle.YEARLY -> R.string.cycle_yearly
    }
)

internal fun formatSheetDate(millis: Long, pattern: String): String =
    SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))

/** 本地日期对应的 UTC 零点毫秒（DatePicker 的语义）。 */
internal fun utcMidnightOf(millis: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = millis }
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    utc.clear()
    utc.set(
        local.get(Calendar.YEAR),
        local.get(Calendar.MONTH),
        local.get(Calendar.DAY_OF_MONTH)
    )
    return utc.timeInMillis
}

/** 把 DatePicker 返回的 UTC 零点换算回本地年月日，保留 [baseMillis] 的时分。 */
internal fun applyUtcDate(utcMillis: Long, baseMillis: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        .apply { timeInMillis = utcMillis }
    val cal = Calendar.getInstance().apply { timeInMillis = baseMillis }
    cal.set(Calendar.YEAR, utc.get(Calendar.YEAR))
    cal.set(Calendar.MONTH, utc.get(Calendar.MONTH))
    cal.set(Calendar.DAY_OF_MONTH, utc.get(Calendar.DAY_OF_MONTH))
    return cal.timeInMillis
}

// ---------- 全局设计组件 ----------

/** 品牌蓝渐变（页头自上而下压深）。 */
@Composable
fun brandHeaderBrush(): Brush =
    Brush.verticalGradient(listOf(YuanmanGradientTop, YuanmanGradientBottom))

/** 按压缩放微反馈：点击类卡片统一用它获得「按下缩小」的触感。
 *  用 pointerInput 监听按下/抬起，不消费事件，不影响链路里的 clickable。 */
fun Modifier.pressScale(pressedScale: Float = 0.96f): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        label = "pressScale"
    )
    this
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown()
                pressed = true
                waitForUpOrCancellation()
                pressed = false
            }
        }
        .scale(scale)
}

/** 统一卡片：品牌蓝圆角 + 纯色面（surface），带可选点击。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    if (onClick != null) {
        val cardModifier = modifier
            .fillMaxWidth()
            .pressScale()
        Card(
            onClick = onClick,
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = cardModifier
        ) { content() }
    } else {
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = modifier.fillMaxWidth()
        ) { content() }
    }
}

/** 分区标题（用于列表分组，与卡片 16dp 外边距同宽对齐）。 */
@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

/** 全局空状态：大 emoji 圆底（轻浮动呼吸感）+ 标题 + 副文案 + 可选主按钮。 */
@Composable
fun EmptyState(
    emoji: String,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    // emoji 轻浮动：空状态也有呼吸感，不「死板」
    val floatTransition = rememberInfiniteTransition(label = "emptyFloat")
    val floatY by floatTransition.animateFloat(
        initialValue = 0f,
        targetValue = -7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "emptyFloatY"
    )
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(92.dp)
                .clip(CircleShape)
                .background(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                )
                .graphicsLayer { translationY = floatY },
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 42.sp)
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(22.dp))
            Button(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

/** 矢量图标圆底（替代 emoji 文本图标）。 */@Composable
fun IconTile(
    icon: ImageVector,
    tint: Color,
    container: Color,
    size: Dp = 42.dp,
    iconSize: Dp = 20.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

/** 底部弹层拖拽手柄：点击关闭，或下滑超过 96dp 关闭（不拦截无位移点击）。
 *  TransactionSheet 与 GoalCreateSheet 共用的收口入口。 */
@Composable
fun SheetDragHandle(onDismiss: () -> Unit) {
    val closeLabel = stringResource(R.string.sheet_close)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp)
            // 无障碍：整条手柄是一个「关闭」按钮，TalkBack 可聚焦并读出声
            .semantics {
                role = Role.Button
                contentDescription = closeLabel
            }
            .pointerInput(Unit) {
                var dragTotal = 0f
                var dismissed = false
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        if (!dismissed) {
                            dragTotal += dragAmount
                            if (dragTotal > 96.dp.toPx()) {
                                dismissed = true
                                onDismiss()
                            }
                        }
                    }
                )
            }
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(width = 32.dp, height = 4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
    }
}

/** 一级页头（Tab 页）：品牌蓝渐变 + 状态栏融合 + 圆角收底。 */
@Composable
fun YuanmanHeader(
    title: String,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(brandHeaderBrush())
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            actions()
        }
        subtitle?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

/** 二级页头（子页面）：返回箭头 + 标题 + 可选操作。 */
@Composable
fun SubPageHeader(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(brandHeaderBrush())
            .statusBarsPadding()
            .padding(horizontal = 8.dp)
            .padding(top = 4.dp, bottom = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                    tint = Color.White
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
            actions()
        }
    }
}
