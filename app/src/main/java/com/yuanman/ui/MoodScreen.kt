package com.yuanman.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuanman.DateUtils
import com.yuanman.MainViewModel
import com.yuanman.MessageVariant
import com.yuanman.MoneyUtils
import com.yuanman.Mood
import com.yuanman.MoodEntry
import com.yuanman.R
import com.yuanman.Transaction
import com.yuanman.YearMonth
import com.yuanman.consecutiveNonAngryDays
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/** 心情名称文案。 */
@Composable
private fun moodLabel(mood: Mood): String = stringResource(
    when (mood) {
        Mood.GREAT -> R.string.mood_great
        Mood.GOOD -> R.string.mood_good
        Mood.CALM -> R.string.mood_calm
        Mood.BAD -> R.string.mood_bad
        Mood.ANGRY -> R.string.mood_angry
    }
)

/** 心情 Tab：今日记录 + 鼓励卡 + 本月统计 + 心情网格 + 心情×消费交叉卡。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoodScreen(viewModel: MainViewModel) {
    val moods by viewModel.moods.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()

    // 「今天 / 本月」随真实时间推进（每 30 秒校一次）：跨零点/跨月后自动切换，
    // 心情打卡不会落在昨天；值未变时不触发重组
    var today by remember { mutableStateOf(DateUtils.today()) }
    var monthPrefix by remember { mutableStateOf(DateUtils.monthPrefix()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            today = DateUtils.today()
            monthPrefix = DateUtils.monthPrefix()
        }
    }
    val noteSavedText = stringResource(R.string.mood_note_saved)
    val moodSavedText = stringResource(R.string.mood_saved)
    val todayEntry = moods[today]
    var note by remember(today, todayEntry?.note) {
        mutableStateOf(todayEntry?.note ?: "")
    }
    // 一句话备注默认折叠：保存后以文案展示，再次进入/重组不自动回到编辑态。
    // 之前按「已有备注则展开」初始化，而 setMood 每次都会发新数据使
    // todayEntry 换实例，noteOpen 被重置回 true，导致存完备注编辑器又弹开。
    var noteOpen by remember(today) { mutableStateOf(false) }

    // 重复点击底部「心情」Tab：页面滚回顶部
    val scrollState = rememberScrollState()
    LaunchedEffect(Unit) {
        viewModel.tabReclick.collect { route ->
            if (route == "mood") scrollState.animateScrollTo(0)
        }
    }

    // 本月记录
    val monthEntries = remember(moods, monthPrefix) {
        moods.filterKeys { it.startsWith(monthPrefix) }
    }
    val angryDays = monthEntries.count { it.value.mood == Mood.ANGRY }
    val nonAngryStreak = remember(moods) { consecutiveNonAngryDays(moods) }

    // 心情×消费交叉（全量数据，数据不足不显示）
    val crossAmount = remember(moods, transactions) {
        val dailyExpense = mutableMapOf<String, Long>()
        transactions.forEach { t ->
            if (t.type == Transaction.Type.EXPENSE) {
                val key = DateUtils.dateKey(t.timestamp)
                dailyExpense[key] = (dailyExpense[key] ?: 0L) + t.amountCents
            }
        }
        val angryKeys = moods.keys.filter { moods[it]?.mood == Mood.ANGRY }
        val calmKeys = moods.keys.filter { moods[it]?.mood != Mood.ANGRY }
        if (angryKeys.isNotEmpty() && calmKeys.size >= 3) {
            val angryAvg = angryKeys.map { dailyExpense[it] ?: 0L }.average()
            val calmAvg = calmKeys.map { dailyExpense[it] ?: 0L }.average()
            val diff = (angryAvg - calmAvg).toLong()
            if (diff > 0) diff else null
        } else {
            null
        }
    }

    // 月历回看：网格月份独立于「本月」，可翻回过去任一月；翻离本月时标题
    // 变可点，一键回到本月（未来月份不可达）
    var gridMonth by rememberSaveable { mutableStateOf(DateUtils.monthPrefix()) }
    val gridDaysInMonth = remember(gridMonth) {
        val parts = gridMonth.split("-")
        val cal = Calendar.getInstance().apply {
            set(parts[0].toInt(), parts[1].toInt() - 1, 1)
        }
        cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
    val gridYear = gridMonth.substringBefore("-").toInt()
    val gridMonthNum = gridMonth.substringAfter("-").toInt()
    // 心情日历点击回看 / 补记：选中的日期 key（yyyy-MM-dd）
    var dayPicker by remember { mutableStateOf<String?>(null) }
    val dayTitlePattern = stringResource(R.string.mood_day_pattern)

    // 页头随内容滚动；滚出页头后浅色主题切深状态栏图标（白图标在浅底上看不见）
    val scrolledPastHeader by remember {
        derivedStateOf { scrollState.value > 0 }
    }
    ScrollingTabStatusBar(scrolledPastHeader)

    // 底部导航栏悬浮在页面之上（见 MainActivity），内容底部预留其高度
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState)
                .navigationBarsPadding()
        ) {
            YuanmanHeader(
                title = stringResource(R.string.tab_mood),
                subtitle = stringResource(R.string.mood_header_sub)
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            // 今日记录：点 emoji 即保存
            AppCard {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.mood_today_question),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(14.dp))
                    // 5 枚心情按钮：均分行宽，放不下自动换行（窄屏/大字号不溢出）
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        maxItemsInEachRow = 5,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Mood.entries.forEach { mood ->
                            MoodButton(
                                mood = mood,
                                isSelected = todayEntry?.mood == mood,
                                onClick = {
                                    // 换了个心情才算「新记录」：给成功反馈，重复点同一枚不打扰
                                    if (todayEntry?.mood != mood) {
                                        viewModel.setMood(today, mood, note.trim())
                                        viewModel.postMessage(
                                            moodSavedText,
                                            MessageVariant.SUCCESS
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    if (todayEntry != null) {
                        if (!noteOpen) {
                            // 已保存的一句话：圆角浅底容器承载，右侧铅笔进编辑（保存后不再停在编辑态）
                            if (todayEntry.note.isNotEmpty()) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            MaterialTheme.colorScheme.surfaceContainerHighest
                                                .copy(alpha = 0.55f)
                                        )
                                        .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "📝",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = todayEntry.note,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 4,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { noteOpen = true }) {
                                        Icon(
                                            Icons.Outlined.Edit,
                                            contentDescription =
                                                stringResource(R.string.mood_note_edit),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            } else {
                                TextButton(onClick = { noteOpen = true }) {
                                    Text(stringResource(R.string.mood_note_toggle))
                                }
                            }
                        } else {
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = note,
                                // 一句话不限字数：原样接收，长文可换行，不做截断
                                onValueChange = { note = it },
                                label = {
                                    Text(stringResource(R.string.mood_note_hint))
                                },
                                maxLines = 4,
                                modifier = Modifier.fillMaxWidth()
                            )
                            TextButton(onClick = {
                                val trimmed = note.trim()
                                // 内容没变不重复写盘、不弹反馈：改动才算「保存」。
                                // 清空原文也算改动（保存即删除），同样给一次确认
                                if (trimmed != todayEntry.note) {
                                    viewModel.setMood(today, todayEntry.mood, trimmed)
                                    viewModel.postMessage(
                                        noteSavedText,
                                        MessageVariant.SUCCESS
                                    )
                                }
                                // 保存后退出编辑态，回到文案展示
                                noteOpen = false
                            }) {
                                Text(stringResource(R.string.mood_note_save))
                            }
                        }
                    }
                }
            }

            // 鼓励卡（视觉强化）
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(
                        alpha = 0.55f
                    )
                )
            ) {
                Text(
                    text = encouragementText(moods, nonAngryStreak),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }

            // 本月统计
            AppCard {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.mood_stats_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    if (monthEntries.isEmpty()) {
                        Text(
                            text = stringResource(R.string.mood_stats_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // 切片按各情绪本色着色（与选择器/月历一致），非通用图表色板
                            val moodSlices = Mood.entries.mapNotNull { mood ->
                                val count = monthEntries.count {
                                    it.value.mood == mood
                                }
                                if (count > 0) {
                                    ChartSlice(
                                        moodLabel(mood),
                                        count.toFloat()
                                    ) to Color(mood.colorValue)
                                } else {
                                    null
                                }
                            }
                            DonutChart(
                                slices = moodSlices.map { it.first },
                                sliceColors = moodSlices.map { it.second },
                                centerTitle = stringResource(R.string.mood_donut_center),
                                centerValue = "$angryDays",
                                modifier = Modifier.size(120.dp)
                            )
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(
                                        R.string.mood_angry_days, angryDays
                                    ),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (angryDays > 0) {
                                        Color(Mood.ANGRY.colorValue)
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stringResource(
                                        R.string.mood_calm_streak, nonAngryStreak
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = stringResource(
                                        R.string.mood_recorded_days, monthEntries.size
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 心情×消费交叉卡
            crossAmount?.let {
                AppCard {
                    Text(
                        text = stringResource(
                            R.string.mood_cross,
                            MoneyUtils.formatCents(it)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            // 心情月历：网格月份可翻月回看，标题行即月份切换条
            AppCard {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.mood_grid_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    MonthSelector(
                        month = YearMonth(gridYear, gridMonthNum),
                        onPrev = { gridMonth = shiftMonthKey(gridMonth, -1) },
                        onNext = { gridMonth = shiftMonthKey(gridMonth, 1) },
                        nextEnabled = gridMonth != monthPrefix,
                        onTitleClick = if (gridMonth == monthPrefix) {
                            null
                        } else {
                            { gridMonth = monthPrefix }
                        }
                    )
                    Spacer(Modifier.height(4.dp))
                    // 月历网格随月份滑动（与标题同一套过渡），回看不突兀
                    AnimatedContent(
                        targetState = YearMonth(gridYear, gridMonthNum),
                        transitionSpec = { monthPageTransition { it.year * 12 + it.month } },
                        label = "moodGrid"
                    ) {
                        MoodGrid(
                            daysInMonth = gridDaysInMonth,
                            monthPrefix = gridMonth,
                            moods = moods,
                            today = today,
                            onDayClick = { dayPicker = it }
                        )
                    }
                }
            }
            }
        }
    }

    // 回看 / 补记某天：显示当天记录，点心情即覆盖保存
    dayPicker?.let { dayKey ->
        val entry = moods[dayKey]
        val dayTitle = remember(dayKey) {
            // 存储 key 是固定 yyyy-MM-dd，用 DateUtils 解析（与 BadgeScreen 同源）
            DateUtils.parse(dayKey)?.let {
                SimpleDateFormat(dayTitlePattern, Locale.getDefault()).format(Date(it))
            } ?: dayKey
        }
        val backfillSavedText = stringResource(R.string.mood_backfill_saved, dayTitle)
        AlertDialog(
            onDismissRequest = { dayPicker = null },
            title = { Text(dayTitle) },
            text = {
                Column {
                    if (entry != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // 与主选择器同一套矢量表情图标（emoji 在不同设备渲染不一，矢量保设计意图）
                            Icon(
                                imageVector = moodIcon(entry.mood),
                                contentDescription = null,
                                tint = Color(entry.mood.colorValue),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = moodLabel(entry.mood),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (entry.note.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = entry.note,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.mood_day_none),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    CompactMoodPicker(
                        current = entry?.mood,
                        onSelect = { mood ->
                            // 与当天记录同心情时不重复写盘、不打扰；
                            // 换心情或补记空白日才算新记录，才给成功反馈
                            if (entry?.mood != mood) {
                                viewModel.setMood(dayKey, mood, entry?.note ?: "")
                                viewModel.postMessage(backfillSavedText, MessageVariant.SUCCESS)
                            }
                            dayPicker = null
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { dayPicker = null }) {
                    Text(stringResource(R.string.common_confirm))
                }
            }
        )
    }
}

@Composable
private fun MoodButton(
    mood: Mood,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = moodLabel(mood)
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 0.dp,
        label = "moodBorder"
    )
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1f,
        label = "moodScale"
    )
    // 点心情即记录：轻震动确认「已选上」，避免只见选中态动画、体感发虚
    val view = LocalView.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            Modifier
                .size(48.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(
                    if (isSelected) {
                        // 选中按该情绪本色填充，与月历/回填选择器一致
                        Color(mood.colorValue)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    }
                )
                // 描边用卡片底色而非纯白：浅色主题下不消失，深色主题下不刺眼
                .border(borderWidth, MaterialTheme.colorScheme.surface, CircleShape)
                // 圆钮承载完整语义（清掉内部 icon），下方文字标签单独存在但不重复朗读
                .clearAndSetSemantics {
                    contentDescription = label
                    selected = isSelected
                }
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = moodIcon(mood),
                contentDescription = null,
                tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            // 名称已由圆钮读出，标签不再单独成节点，避免 TalkBack 重复
            modifier = Modifier.clearAndSetSemantics {},
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1
        )
    }
}

/** 对话框内的小号心情选择：emoji 圆钮，选中按该情绪本色填充，
 *  与主选择器同一套缩放 + 描边选中反馈。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompactMoodPicker(
    current: Mood?,
    onSelect: (Mood) -> Unit
) {
    val view = LocalView.current
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        maxItemsInEachRow = 5,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Mood.entries.forEach { mood ->
            val label = moodLabel(mood)
            val isSelected = mood == current
            val borderWidth by animateDpAsState(
                targetValue = if (isSelected) 2.dp else 0.dp,
                label = "compactMoodBorder"
            )
            val scale by animateFloatAsState(
                targetValue = if (isSelected) 1.08f else 1f,
                label = "compactMoodScale"
            )
            Box(
                Modifier
                    .size(48.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) {
                            Color(mood.colorValue)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        }
                    )
                    // 描边用卡片底色而非纯白：浅色主题下不消失，深色主题下不刺眼
                    .border(borderWidth, MaterialTheme.colorScheme.surface, CircleShape)
                    // 与主选择器同一套无障碍语义：读出心情名 + 选中态
                    .clearAndSetSemantics {
                        contentDescription = label
                        selected = isSelected
                    }
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onSelect(mood)
                    },
                contentAlignment = Alignment.Center
            ) {
                // 与主选择器同款矢量图标 + 同款选中配色，两处选择器视觉完全一致
                Icon(
                    imageVector = moodIcon(mood),
                    contentDescription = null,
                    tint = if (isSelected) {
                        Color.White
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/** 本月心情日历：周表头 + 7 列按星期对齐，每日一个色点（未记录灰色），
 *  今天主色描边圈定位，点击可回看/补记。 */
@Composable
private fun MoodGrid(
    daysInMonth: Int,
    monthPrefix: String,
    moods: Map<String, MoodEntry>,
    today: String,
    onDayClick: (String) -> Unit
) {
    // 今天是当月几号（跨月后自动失效，回看上月不标今天）
    val todayDay = remember(monthPrefix, today) {
        if (today.startsWith(monthPrefix)) {
            today.substringAfterLast("-").toIntOrNull()
        } else {
            null
        }
    }
    val view = LocalView.current
    Column {
        // 周表头（周一起始）
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val weekdayRes = listOf(
                R.string.weekday_mon,
                R.string.weekday_tue,
                R.string.weekday_wed,
                R.string.weekday_thu,
                R.string.weekday_fri,
                R.string.weekday_sat,
                R.string.weekday_sun
            )
            weekdayRes.forEach { res ->
                Text(
                    text = stringResource(res),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        // 1 号对齐到正确星期（周一起始），前面补空位
        val leadingBlanks = remember(monthPrefix) {
            val parts = monthPrefix.split("-")
            val cal = Calendar.getInstance().apply {
                set(parts[0].toInt(), parts[1].toInt() - 1, 1)
            }
            (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        }
        val days = (1..daysInMonth).toList()
        val cells: List<Int?> = List(leadingBlanks) { null } + days
        cells.chunked(7).forEach { week ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                week.forEach { day ->
                    if (day == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        val key = DateUtils.dateKeyOf(monthPrefix, day)
                        val entry = moods[key]
                        val isToday = day == todayDay
                        // 整格可点（48dp 高触达区，与全 App 触达下限一致），圆点只做视觉；
                        // 无障碍读出当天心情（未记录也有明确说明），今天追加「今天」标记
                        val dayDesc = if (entry != null) {
                            stringResource(
                                R.string.mood_day_semantics,
                                day,
                                moodLabel(entry.mood)
                            )
                        } else {
                            stringResource(R.string.mood_day_none)
                        }
                        val cellDesc = if (isToday) {
                            dayDesc + " · " + stringResource(R.string.mood_today_marker)
                        } else {
                            dayDesc
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(CircleShape)
                                .pressScale()
                                .clearAndSetSemantics { contentDescription = cellDesc }
                                .clickable {
                                    // 轻触感反馈选择落点，与主心情选择器一致
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onDayClick(key)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (entry != null) {
                                            Color(entry.mood.colorValue)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHighest
                                        }
                                    )
                                    // 今天：主色描边圈，一眼定位「现在」的位置
                                    .then(
                                        if (isToday) {
                                            Modifier.border(
                                                2.dp,
                                                MaterialTheme.colorScheme.primary,
                                                CircleShape
                                            )
                                        } else {
                                            Modifier
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = day.toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (entry != null) {
                                        Color.White
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
                // 补齐最后一周的空位
                repeat(7 - week.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** 月历翻月：把 "yyyy-MM" 位移 [delta] 个月（返回同格式字符串）。 */
private fun shiftMonthKey(key: String, delta: Int): String {
    val parts = key.split("-")
    var y = parts[0].toInt()
    var m = parts[1].toInt() + delta
    while (m < 1) { m += 12; y-- }
    while (m > 12) { m -= 12; y++ }
    return String.format(Locale.CHINA, "%d-%02d", y, m)
}

/** 鼓励文案：按统计数据条件选择（7 条分支）。 */
@Composable
private fun encouragementText(
    moods: Map<String, MoodEntry>,
    nonAngryStreak: Int
): String {
    if (moods.isEmpty()) return stringResource(R.string.mood_encourage_start)

    val monthPrefix = DateUtils.monthPrefix()
    val monthEntries = moods.filterKeys { it.startsWith(monthPrefix) }
    val angryThisMonth = monthEntries.count { it.value.mood == Mood.ANGRY }

    if (angryThisMonth == 0 && monthEntries.size >= 10) {
        return stringResource(R.string.mood_encourage_calm_month)
    }
    if (angryThisMonth == 0 && nonAngryStreak >= 3) {
        return stringResource(R.string.mood_encourage_streak, nonAngryStreak)
    }
    val angryLastMonth = moods.keys
        .filter { it.startsWith(DateUtils.lastMonthPrefix()) }
        .count { moods[it]?.mood == Mood.ANGRY }
    if (angryThisMonth in 1..4 && angryThisMonth < angryLastMonth) {
        return stringResource(
            R.string.mood_encourage_better,
            angryThisMonth,
            angryLastMonth - angryThisMonth
        )
    }
    if (angryThisMonth >= 5) {
        return stringResource(R.string.mood_encourage_hard)
    }
    if (nonAngryStreak >= 1) {
        return stringResource(R.string.mood_encourage_stable, nonAngryStreak)
    }
    return stringResource(R.string.mood_encourage_default)
}
