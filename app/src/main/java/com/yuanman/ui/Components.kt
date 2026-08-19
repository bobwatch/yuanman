package com.yuanman.ui

import android.app.Activity
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
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
import androidx.compose.animation.SizeTransform
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.yuanman.MoneyUtils
import com.yuanman.R
import com.yuanman.RecurringExpense
import com.yuanman.YearMonth
import com.yuanman.ui.theme.LocalDarkTheme
import com.yuanman.ui.theme.YuanmanGradientBottom
import com.yuanman.ui.theme.YuanmanGradientTop
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** 月份切换的整页过渡：前进从右滑入、后退从左滑入；280ms FastOutSlowIn 与淡入淡出同步，
 *  高度变化（月历 5/6 行、统计页高度）用同一 tween 而非默认弹簧，避免回弹；
 *  标题与页面内容共用同一套以保证位移一致。 */
@OptIn(ExperimentalAnimationApi::class)
internal fun <T> AnimatedContentTransitionScope<T>.monthPageTransition(
    keyOf: (T) -> Int
): ContentTransform {
    val forward = keyOf(targetState) > keyOf(initialState)
    val slide = tween<IntOffset>(280, easing = FastOutSlowInEasing)
    val fade = tween<Float>(280, easing = FastOutSlowInEasing)
    val size = SizeTransform { _, _ -> tween(280, easing = FastOutSlowInEasing) }
    return if (forward) {
        ((slideInHorizontally(slide) { it } + fadeIn(fade)) with
            (slideOutHorizontally(slide) { -it } + fadeOut(fade))).using(size)
    } else {
        ((slideInHorizontally(slide) { -it } + fadeIn(fade)) with
            (slideOutHorizontally(slide) { it } + fadeOut(fade))).using(size)
    }
}

/** 月份切换条（‹ 2026年8月 ›），月份文字带滑动动画。[contentColor] 默认跟随主题。
 *  [onTitleClick] 非空时月份标题可点击（加下划线提示），用于「一键回到本月」。
 *  [animateTitle] 为 false 时（整页随月份滑动的首页）标题只做极短淡入，避免双重位移。 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MonthSelector(
    month: YearMonth,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    nextEnabled: Boolean = true,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    onTitleClick: (() -> Unit)? = null,
    /** false 时标题不自带动画（整页随月份滑动时避免双重位移）。 */
    animateTitle: Boolean = true
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
                if (animateTitle) {
                    monthPageTransition { it.year * 12 + it.month }
                } else {
                    // 标题随整页滑动，只留极短淡入淡出平滑宽度变化，避免双重位移
                    fadeIn(tween(120, easing = FastOutSlowInEasing)) with
                        fadeOut(tween(120, easing = FastOutSlowInEasing))
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
            Button(
                onClick = onAction,
                // 与全局主按钮体系一致（20dp），不做 M3 默认全圆角
                shape = MaterialTheme.shapes.large
            ) {
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

/** emoji 候选选择行（分类 / 习惯 / 攒钱目标共用）：流式 FilterChip，
 *  48dp 触达下限 + 轻震反馈，与其他选择器同一套交互语言。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EmojiPickerRow(
    candidates: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        candidates.forEach { candidate ->
            FilterChip(
                selected = selected == candidate,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onSelect(candidate)
                },
                label = { Text(candidate) },
                modifier = Modifier.heightIn(min = 48.dp)
            )
        }
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

/**
 * Dialog 弹层沉浸：把弹层窗口的状态栏/导航栏切透明、图标深浅随主题。
 * 全屏（记账）与底部弹层（攒钱）的 Dialog 都调用；不调用则弹层窗口
 * 继承主题的蓝色状态栏/不透明导航栏，压在弹层内容上方。
 */
@Composable
fun DialogEdgeToEdge() {
    val view = LocalView.current
    val darkTheme = LocalDarkTheme.current
    val dialogWindow = (view.context as? android.app.Dialog)?.window
    LaunchedEffect(Unit) {
        dialogWindow?.let { w ->
            w.statusBarColor = android.graphics.Color.TRANSPARENT
            w.navigationBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(w, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
}

/** 一级页头（Tab 页）：去色块卡片，品牌渐变大字标题 + 副标题，留白更大气。
 *  与首页的蓝渐变同源（brandHeaderBrush），只是不再以卡片形式铺底。 */
@Composable
fun YuanmanHeader(
    title: String,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 24.dp, bottom = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    brush = brandHeaderBrush()
                ),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            actions()
        }
        subtitle?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 二级页头（子页面）：返回箭头 + 渐变标题 + 可选操作，与一级页头同语言。 */
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
            .statusBarsPadding()
            .padding(horizontal = 6.dp)
            .padding(top = 8.dp, bottom = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        brush = brandHeaderBrush()
                    ),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            actions()
        }
    }
}

/**
 * 滚动页头的状态栏图标联动（打卡/心情/我的页共用）：
 * 页头在顶（渐变标题）用白图标；滚出页头露出页面背景后，浅色主题切深图标，
 * 否则白图标在浅底上看不见。与首页同一套逻辑；离开页面时恢复白图标。
 */
@Composable
fun ScrollingTabStatusBar(scrolledPastHeader: Boolean) {
    val darkTheme = LocalDarkTheme.current
    val view = LocalView.current
    val window = (view.context as Activity).window
    val darkIcons = !darkTheme && scrolledPastHeader
    LaunchedEffect(darkIcons) {
        WindowCompat.getInsetsController(window, view)
            .isAppearanceLightStatusBars = darkIcons
    }
    DisposableEffect(Unit) {
        onDispose {
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = false
        }
    }
}
