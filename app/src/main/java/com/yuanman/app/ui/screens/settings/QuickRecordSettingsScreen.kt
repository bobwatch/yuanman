package com.yuanman.app.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuanman.app.data.local.entity.QuickEntryLearningEntity
import com.yuanman.app.data.model.RecordType
import com.yuanman.app.ui.components.ConfirmDeleteDialog
import com.yuanman.app.ui.components.LocalToastHostState
import kotlinx.coroutines.delay

/**
 * 🌟 快捷记账二级页：总开关与分类学习（个人分类习惯）管理。
 * 由设置页「快捷记账」入口行进入，结构与分类管理页一致：
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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
    val learningListState = rememberLazyListState()
    val pageSize = 60

    fun openEditor(rule: QuickEntryLearningEntity?) {
        editingRule = rule
        showAddDialog = rule == null
        editorPhrase = rule?.phrase.orEmpty()
        editorType = rule?.let { runCatching { RecordType.valueOf(it.type) }.getOrDefault(RecordType.EXPENSE) } ?: RecordType.EXPENSE
        editorCategoryId = categories.firstOrNull { it.syncId == rule?.categorySyncId }?.id
            ?: categories.firstOrNull { it.type == editorType.name }?.id
    }

    val visibleRules = rules.filter { rule ->
        val categoryName = categories.firstOrNull { it.syncId == rule.categorySyncId }?.name.orEmpty()
        (selectedType == null || rule.type == selectedType?.name) &&
            (searchQuery.isBlank() || rule.phrase.contains(searchQuery.trim(), ignoreCase = true) || categoryName.contains(searchQuery.trim(), ignoreCase = true))
    }

    LaunchedEffect(searchExpanded) {
        if (searchExpanded) {
            delay(100)
            searchFocusRequester.requestFocus()
        }
    }

    // 词云按页渲染，首屏只创建少量 chip；滚动接近底部时再无感追加下一页。
    var loadedRuleCount by remember(searchQuery, selectedType, rules.size) { mutableIntStateOf(pageSize) }
    var isLoadingMoreRules by remember(searchQuery, selectedType, rules.size) { mutableStateOf(false) }
    val sortedRules = remember(visibleRules) {
        visibleRules.sortedWith(
            compareByDescending<QuickEntryLearningEntity> { it.sampleCount }
                .thenBy { it.phrase.length }
        )
    }
    val loadedRules = sortedRules.take(loadedRuleCount)
    LaunchedEffect(learningListState, loadedRules.size, sortedRules.size) {
        snapshotFlow {
            val layoutInfo = learningListState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisible != null &&
                lastVisible.index >= layoutInfo.totalItemsCount - 1 &&
                lastVisible.offset + lastVisible.size <= layoutInfo.viewportEndOffset
        }.collect { reachedEnd ->
            if (reachedEnd && loadedRules.size < sortedRules.size && !isLoadingMoreRules) {
                // 给用户一个明确的反馈，即使本地数据加载很快也短暂展示加载状态。
                isLoadingMoreRules = true
                delay(180)
                loadedRuleCount = (loadedRuleCount + pageSize).coerceAtMost(sortedRules.size)
                isLoadingMoreRules = false
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            // 规范头部：左上返回图标 + 标题「快捷记账」靠左（与分类管理页一致）
            Surface(color = MaterialTheme.colorScheme.surface) {
                TopAppBar(
                    title = { Text("快捷记账", fontWeight = FontWeight.Bold) },
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

            // 1. 快捷记账总开关卡片
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
                            color = if (uiState.quickEntryEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Bolt,
                                    contentDescription = null,
                                    tint = if (uiState.quickEntryEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "开启快捷记账",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (uiState.quickEntryEnabled) "首页顶部常驻闪电记账条" else "已隐藏首页快捷记账条",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (uiState.quickEntryEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    Switch(
                        checked = uiState.quickEntryEnabled,
                        onCheckedChange = viewModel::setQuickEntryEnabled
                    )
                }
            }

            // 2. 分类学习（个人分类习惯）管理区，总开关关闭时整体隐藏
            AnimatedVisibility(
                visible = uiState.quickEntryEnabled,
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
                    Spacer(modifier = Modifier.height(6.dp))

                    if (rules.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "还没有学习记录。保存几笔快捷记账后，系统会逐步记住你的分类习惯。",
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
                        LazyColumn(
                            state = learningListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(
                                items = loadedRules.chunked(pageSize),
                                key = { page ->
                                    page.firstOrNull()?.let { "${it.type}_${it.categorySyncId}_${it.phrase}" } ?: "learning_page"
                                }
                            ) { page ->
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    page.forEach { rule ->
                                        val category = categories.firstOrNull { it.syncId == rule.categorySyncId }
                                        val typeColor = if (rule.type == RecordType.EXPENSE.name) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        }
                                        val categoryColor = category?.colorHex?.let { Color(it) } ?: typeColor
                                        Surface(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50))
                                                .combinedClickable(
                                                    onClick = { openEditor(rule) },
                                                    onLongClick = { deleteRule = rule }
                                                ),
                                            shape = RoundedCornerShape(50),
                                            color = categoryColor.copy(alpha = 0.10f),
                                            border = BorderStroke(0.7.dp, categoryColor.copy(alpha = 0.35f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(categoryColor)
                                                )
                                                Text(
                                                    text = rule.phrase,
                                                    fontSize = (11 + rule.sampleCount.coerceIn(0, 3)).sp,
                                                    fontWeight = if (rule.sampleCount > 1) FontWeight.SemiBold else FontWeight.Normal,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (loadedRules.size < sortedRules.size) {
                                item(key = "learning_loading_footer") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isLoadingMoreRules) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(18.dp),
                                                    strokeWidth = 2.dp
                                                )
                                                Text(
                                                    text = "正在加载更多…",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                        } else {
                                            Text(
                                                text = "继续下滑加载更多",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }
                                }
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
        message = "将清除快捷记账的个人分类习惯，内置分类词典不会受影响。确定继续吗？",
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
