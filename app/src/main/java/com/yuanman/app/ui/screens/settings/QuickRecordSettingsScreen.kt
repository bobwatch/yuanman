package com.yuanman.app.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuanman.app.data.local.entity.QuickEntryLearningEntity
import com.yuanman.app.data.model.RecordType
import com.yuanman.app.ui.components.ConfirmDeleteDialog
import com.yuanman.app.ui.components.LocalToastHostState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

private val HabitTagPillShape = RoundedCornerShape(50)
private val HabitTagDotShape = CircleShape

/**
 * 🌟 记账习惯二级页：首页闪电记账条总开关与分类学习（个人分类习惯）管理。
 * 由设置页「记账习惯」入口行进入，结构与分类管理页一致：
 * 左上返回图标 + 标题靠左；所有状态持久化沿用 SettingsViewModel。
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
@Composable
fun QuickRecordSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val quickEntryEnabled by viewModel.quickEntryEnabled.collectAsStateWithLifecycle(initialValue = true)
    val rules by viewModel.quickEntryLearningRules.collectAsStateWithLifecycle()
    val categories = viewModel.allCategories.collectAsStateWithLifecycle().value
    val toast = LocalToastHostState.current

    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    var selectedType by remember { mutableStateOf<RecordType?>(null) }
    var editingRule by remember { mutableStateOf<QuickEntryLearningEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editorPhrase by remember { mutableStateOf("") }
    var editorType by remember { mutableStateOf(RecordType.EXPENSE) }
    var editorCategoryId by remember { mutableStateOf<Long?>(null) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var deleteRule by remember { mutableStateOf<QuickEntryLearningEntity?>(null) }
    var showResetConfirm by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    val categoriesBySyncId = remember(categories) { categories.associateBy { it.syncId } }

    fun openEditor(rule: QuickEntryLearningEntity?) {
        editingRule = rule
        showAddDialog = rule == null
        editorPhrase = rule?.phrase.orEmpty()
        editorType = rule?.let { runCatching { RecordType.valueOf(it.type) }.getOrDefault(RecordType.EXPENSE) } ?: RecordType.EXPENSE
        editorCategoryId = categoriesBySyncId[rule?.categorySyncId]?.id
            ?: categories.firstOrNull { it.type == editorType.name }?.id
    }

    // 分类查询表 + 筛选/排序记忆化：避免大规模列表重排时 O(N*M) 扫描
    val visibleRules = remember(rules, searchQuery, selectedType, categoriesBySyncId) {
        val query = searchQuery.trim()
        rules.filter { rule ->
            (selectedType == null || rule.type == selectedType?.name) &&
                (query.isEmpty() ||
                    rule.phrase.contains(query, ignoreCase = true) ||
                    (categoriesBySyncId[rule.categorySyncId]?.name.orEmpty()).contains(query, ignoreCase = true))
        }
    }

    LaunchedEffect(searchExpanded) {
        if (searchExpanded) {
            delay(100)
            searchFocusRequester.requestFocus()
        }
    }

    // 排序逻辑：频次降序 + 最近使用/添加时间降序，新学到的词条位于同频次前列
    val sortedRules = remember(visibleRules) {
        visibleRules.sortedWith(
            compareByDescending<QuickEntryLearningEntity> { it.sampleCount }
                .thenByDescending { it.lastUsedAt }
        )
    }

    // 标签首批展示 80 条（覆盖多数用户全量习惯），超量数据滚动到底部或点击平滑增量展现
    var loadedRuleCount by remember(searchQuery, selectedType) { mutableIntStateOf(80) }
    val loadedRules = remember(sortedRules, loadedRuleCount) {
        sortedRules.take(loadedRuleCount)
    }

    // 切换筛选条件或关键词时，平滑回到顶部
    LaunchedEffect(searchQuery, selectedType) {
        scrollState.scrollTo(0)
    }

    // 监听滑动到底部触发预加载下一批，通过 distinctUntilChanged 避免滚动每像素重发
    val thresholdPx = with(LocalDensity.current) { 360.dp.roundToPx() }
    LaunchedEffect(scrollState, sortedRules.size) {
        snapshotFlow {
            val max = scrollState.maxValue
            if (max <= 0) false
            else scrollState.value >= max - thresholdPx
        }
        .distinctUntilChanged()
        .collect { nearEnd ->
            if (nearEnd && loadedRuleCount < sortedRules.size) {
                loadedRuleCount = (loadedRuleCount + 60).coerceAtMost(sortedRules.size)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            // 规范头部：左上返回图标 + 标题「记账习惯」靠左（与分类管理页一致）
            Surface(color = MaterialTheme.colorScheme.surface) {
                TopAppBar(
                    title = { Text("记账习惯", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    ),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // 1. 记账习惯总开关卡片（控制首页闪电记账条）
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (quickEntryEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Bolt,
                                    contentDescription = null,
                                    tint = if (quickEntryEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "开启记账习惯",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (quickEntryEnabled) "首页顶部常驻闪电记账条" else "已隐藏首页闪电记账条",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (quickEntryEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    Switch(
                        checked = quickEntryEnabled,
                        onCheckedChange = viewModel::setQuickEntryEnabled
                    )
                }
            }

            // 2. 分类学习（个人分类习惯）管理区，总开关关闭时整体隐藏
            AnimatedVisibility(
                visible = quickEntryEnabled,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    )

                    // 3. 分类学习管理区域
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("分类学习", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Text(
                                "个人习惯随记账自动积累",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            TextButton(onClick = { openEditor(null) }) { Text("新增") }
                            TextButton(onClick = { showResetConfirm = true }, enabled = rules.isNotEmpty()) { Text("重置") }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (searchExpanded) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.weight(1f).focusRequester(searchFocusRequester),
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                                placeholder = { Text("搜索关键词或分类") },
                                trailingIcon = {
                                    IconButton(onClick = {
                                        searchQuery = ""
                                        searchExpanded = false
                                    }) { Icon(Icons.Default.Close, contentDescription = "关闭搜索") }
                                }
                            )
                        } else {
                            FilterChip(selected = selectedType == null, onClick = { selectedType = null }, label = { Text("全部") })
                            FilterChip(selected = selectedType == RecordType.EXPENSE, onClick = { selectedType = RecordType.EXPENSE }, label = { Text("支出") })
                            FilterChip(selected = selectedType == RecordType.INCOME, onClick = { selectedType = RecordType.INCOME }, label = { Text("收入") })
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { searchExpanded = true }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Outlined.Search, contentDescription = "展开搜索")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    if (rules.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "还没有学习记录。用首页闪电记账条记几笔后，系统会自动记住你的分类习惯。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    } else if (visibleRules.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("没有匹配的学习记录", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                        }
                    } else {
                        // 使用单一连续 FlowRow 结合 verticalScroll：
                        // 彻底解决按 batch 切割导致的行末断行与留白不连贯问题，滑动由 GPU 偏移直接渲染，告别掉帧
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(scrollState),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                loadedRules.forEach { rule ->
                                    key(rule.type, rule.categorySyncId, rule.phrase) {
                                        val category = categoriesBySyncId[rule.categorySyncId]
                                        val typeColor = if (rule.type == RecordType.EXPENSE.name) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        }
                                        val categoryColor = category?.colorHex?.let { Color(it) } ?: typeColor
                                        HabitTagChip(
                                            phrase = rule.phrase,
                                            sampleCount = rule.sampleCount,
                                            categoryColor = categoryColor,
                                            onClick = { openEditor(rule) },
                                            onLongClick = { deleteRule = rule }
                                        )
                                    }
                                }
                            }

                            if (loadedRules.size < sortedRules.size) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        onClick = {
                                            loadedRuleCount = sortedRules.size
                                        },
                                        shape = HabitTagPillShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                    ) {
                                        Text(
                                            text = "已展示 ${loadedRules.size} / ${sortedRules.size} 条 · 点击展开全部或继续下滑",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(20.dp))
                            } else {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "已展示全部 ${sortedRules.size} 条习惯",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp)
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // 重置分类学习（清除个人分类习惯）确认弹窗
    ConfirmDeleteDialog(
        visible = showResetConfirm,
        title = "重置分类学习",
        message = "将清除已记住的个人分类习惯（首页闪电记账条的智能匹配），内置分类词典不受影响。确定继续吗？",
        confirmButtonText = "确认重置",
        onConfirm = {
            viewModel.clearQuickEntryLearning()
            showResetConfirm = false
            toast.success("分类学习已重置")
        },
        onDismiss = { showResetConfirm = false }
    )

    if (showAddDialog || editingRule != null) {
        val currentRule = editingRule
        val editorCategories = categories.filter { it.type == editorType.name }
        val selectedCategory = editorCategories.firstOrNull { it.id == editorCategoryId }
        AlertDialog(
            onDismissRequest = { editingRule = null; showAddDialog = false },
            title = { Text(if (currentRule == null) "新增学习内容" else "编辑学习内容") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editorPhrase,
                        onValueChange = { editorPhrase = it },
                        label = { Text("描述关键词") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = editorType == RecordType.EXPENSE, onClick = { editorType = RecordType.EXPENSE; editorCategoryId = null }, label = { Text("支出") })
                        FilterChip(selected = editorType == RecordType.INCOME, onClick = { editorType = RecordType.INCOME; editorCategoryId = null }, label = { Text("收入") })
                    }
                    Box {
                        OutlinedButton(onClick = { categoryMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(selectedCategory?.name ?: "选择分类")
                        }
                        DropdownMenu(
                            expanded = categoryMenuExpanded,
                            onDismissRequest = { categoryMenuExpanded = false },
                            modifier = Modifier.heightIn(max = 280.dp)
                        ) {
                            editorCategories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category.name) },
                                    onClick = { editorCategoryId = category.id; categoryMenuExpanded = false }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val category = editorCategories.firstOrNull { it.id == editorCategoryId }
                        if (category != null) {
                            if (currentRule == null) viewModel.addQuickEntryLearning(editorType, editorPhrase, category.syncId)
                            else viewModel.updateQuickEntryLearning(currentRule, editorPhrase, editorType, category.syncId)
                        }
                        editingRule = null
                        showAddDialog = false
                    },
                    enabled = editorPhrase.isNotBlank() && selectedCategory != null
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editingRule = null; showAddDialog = false }) { Text("取消") } }
        )
    }

    deleteRule?.let { rule ->
        AlertDialog(
            onDismissRequest = { deleteRule = null },
            title = { Text("删除学习内容") },
            text = { Text("确定删除“${rule.phrase}”这条分类学习记录吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteQuickEntryLearning(rule)
                        deleteRule = null
                    }
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteRule = null }) { Text("取消") } }
        )
    }
}

/**
 * 极简轻量级习惯标签 Chip：
 * 移除重量级 Surface 嵌套，复用单例 Shape 减免对象分配，保证流畅绘制。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HabitTagChip(
    phrase: String,
    sampleCount: Int,
    categoryColor: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fontSize = (11 + sampleCount.coerceIn(0, 3)).sp
    val fontWeight = if (sampleCount > 1) FontWeight.SemiBold else FontWeight.Normal

    Row(
        modifier = modifier
            .clip(HabitTagPillShape)
            .background(categoryColor.copy(alpha = 0.10f))
            .border(width = 0.7.dp, color = categoryColor.copy(alpha = 0.35f), shape = HabitTagPillShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(categoryColor, HabitTagDotShape)
        )
        Text(
            text = phrase,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

