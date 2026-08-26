package com.yuanman.app.ui.screens.category

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.yuanman.app.data.local.entity.CategoryEntity
import com.yuanman.app.data.model.RecordType
import com.yuanman.app.ui.components.AlertInfoDialog
import com.yuanman.app.ui.components.CategoryIconView
import com.yuanman.app.ui.components.ConfirmDeleteDialog
import com.yuanman.app.ui.components.LocalToastHostState
import com.yuanman.app.ui.components.SwipeRevealDeleteItem
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManageScreen(
    viewModel: CategoryManageViewModel,
    onNavigateBack: (() -> Unit)? = null,
    onNavigateToAddCategory: (RecordType) -> Unit = {},
    onNavigateToEditCategory: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val toast = LocalToastHostState.current
    val haptic = LocalHapticFeedback.current

    var categoryToDelete by remember { mutableStateOf<CategoryEntity?>(null) }
    var openSwipeItemId by remember { mutableStateOf<Long?>(null) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var orderedItems by remember { mutableStateOf(emptyList<CategoryWithUsage>()) }

    // 🌟 120 FPS 物理无闪烁拖拽状态系统
    var draggingCategoryId by remember { mutableStateOf<Long?>(null) }
    var dragInitialIndex by remember { mutableIntStateOf(-1) }
    var totalDragOffsetY by remember { mutableFloatStateOf(0f) }
    var currentTargetIndex by remember { mutableIntStateOf(-1) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current

    LaunchedEffect(uiState.currentType, uiState.categoriesWithUsage) {
        if (draggingCategoryId == null) {
            orderedItems = uiState.categoriesWithUsage
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("分类管理", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigateToAddCategory(uiState.currentType) }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "新增分类",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 🌟 支出 / 收入 2 Tab 切换
            TabRow(
                selectedTabIndex = if (uiState.currentType == RecordType.EXPENSE) 0 else 1,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = uiState.currentType == RecordType.EXPENSE,
                    onClick = { viewModel.switchType(RecordType.EXPENSE) },
                    text = {
                        Text(
                            text = "支出分类",
                            fontWeight = if (uiState.currentType == RecordType.EXPENSE) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.5.sp
                        )
                    }
                )
                Tab(
                    selected = uiState.currentType == RecordType.INCOME,
                    onClick = { viewModel.switchType(RecordType.INCOME) },
                    text = {
                        Text(
                            text = "收入分类",
                            fontWeight = if (uiState.currentType == RecordType.INCOME) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.5.sp
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 🌟 分类及其专属子标签列表
            if (orderedItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无分类，点击下方新增",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .pointerInput(uiState.currentType) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { startOffset ->
                                    val matchedItem = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                        startOffset.y >= it.offset && startOffset.y <= it.offset + it.size
                                    }
                                    if (matchedItem != null) {
                                        openSwipeItemId = null
                                        val categoryId = matchedItem.key as? Long
                                        val index = orderedItems.indexOfFirst { it.category.id == categoryId }
                                        if (index >= 0) {
                                            draggingCategoryId = categoryId
                                            dragInitialIndex = index
                                            currentTargetIndex = index
                                            totalDragOffsetY = 0f
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    if (draggingCategoryId == null || dragInitialIndex < 0) return@detectDragGesturesAfterLongPress
                                    totalDragOffsetY += dragAmount.y

                                    val currentItemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggingCategoryId }
                                    val itemHeight = (currentItemInfo?.size ?: 180).toFloat()
                                    val itemSpacing = with(density) { 8.dp.toPx() }
                                    val slotHeight = itemHeight + itemSpacing

                                    // 计算当前悬浮的目标插槽位置
                                    val newTarget = (dragInitialIndex + (totalDragOffsetY / slotHeight).roundToInt())
                                        .coerceIn(0, orderedItems.size - 1)

                                    if (newTarget != currentTargetIndex) {
                                        currentTargetIndex = newTarget
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }

                                    // 边缘自动平滑滚屏
                                    if (currentItemInfo != null) {
                                        val viewportHeight = listState.layoutInfo.viewportSize.height.toFloat()
                                        val currentTop = currentItemInfo.offset + totalDragOffsetY
                                        if (currentTop < 60f) {
                                            coroutineScope.launch { listState.scrollBy(-12f) }
                                        } else if (currentTop + itemHeight > viewportHeight - 80f) {
                                            coroutineScope.launch { listState.scrollBy(12f) }
                                        }
                                    }
                                },
                                onDragEnd = {
                                    if (dragInitialIndex >= 0 && currentTargetIndex >= 0 && dragInitialIndex != currentTargetIndex) {
                                        val newList = orderedItems.toMutableList()
                                        val movedItem = newList.removeAt(dragInitialIndex)
                                        newList.add(currentTargetIndex, movedItem)
                                        orderedItems = newList
                                        viewModel.updateCategoryOrder(newList.map { it.category.id })
                                    }
                                    draggingCategoryId = null
                                    dragInitialIndex = -1
                                    currentTargetIndex = -1
                                    totalDragOffsetY = 0f
                                },
                                onDragCancel = {
                                    if (dragInitialIndex >= 0 && currentTargetIndex >= 0 && dragInitialIndex != currentTargetIndex) {
                                        val newList = orderedItems.toMutableList()
                                        val movedItem = newList.removeAt(dragInitialIndex)
                                        newList.add(currentTargetIndex, movedItem)
                                        orderedItems = newList
                                        viewModel.updateCategoryOrder(newList.map { it.category.id })
                                    }
                                    draggingCategoryId = null
                                    dragInitialIndex = -1
                                    currentTargetIndex = -1
                                    totalDragOffsetY = 0f
                                }
                            )
                        },
                    contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(
                        items = orderedItems,
                        key = { _, item -> item.category.id }
                    ) { index, item ->
                        val category = item.category
                        val usageCount = item.usageCount
                        val childTags = category.getTagList()
                        val isCurrentDragging = draggingCategoryId == category.id

                        // 🌟 物理弹簧计算位移与插槽让位（完全避免布局跳变与屏闪）
                        val itemHeight = (listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == category.id }?.size ?: 180).toFloat()
                        val itemSpacing = with(density) { 8.dp.toPx() }
                        val slotHeight = itemHeight + itemSpacing

                        val targetShiftY = if (draggingCategoryId != null && !isCurrentDragging) {
                            if (dragInitialIndex < currentTargetIndex) {
                                if (index > dragInitialIndex && index <= currentTargetIndex) -slotHeight else 0f
                            } else if (dragInitialIndex > currentTargetIndex) {
                                if (index >= currentTargetIndex && index < dragInitialIndex) slotHeight else 0f
                            } else {
                                0f
                            }
                        } else {
                            0f
                        }

                        val animatedShiftY by animateFloatAsState(
                            targetValue = targetShiftY,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "itemShiftY"
                        )

                        val dragScale by animateFloatAsState(
                            targetValue = if (isCurrentDragging) 1.035f else 1.0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "dragScale"
                        )
                        val dragElevation by animateDpAsState(
                            targetValue = if (isCurrentDragging) 12.dp else 0.5.dp,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "dragElevation"
                        )

                        val effectiveTranslationY = if (isCurrentDragging) totalDragOffsetY else animatedShiftY

                        // 🌟 拖动期间禁用侧滑删除
                        SwipeRevealDeleteItem(
                            itemKey = category.id,
                            openKey = openSwipeItemId,
                            onOpen = { openSwipeItemId = it },
                            onDelete = { categoryToDelete = category },
                            enabled = (draggingCategoryId == null)
                        ) {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isCurrentDragging) MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
                                    else MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = dragElevation),
                                border = BorderStroke(
                                    if (isCurrentDragging) 1.5.dp else 0.5.dp,
                                    if (isCurrentDragging) primaryColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .zIndex(if (isCurrentDragging) 20f else 0f)
                                    .graphicsLayer {
                                        scaleX = dragScale
                                        scaleY = dragScale
                                        translationY = effectiveTranslationY
                                    }
                                    .clickable(enabled = draggingCategoryId == null) {
                                        onNavigateToEditCategory(category.id)
                                    }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CategoryIconView(
                                            iconName = category.iconName,
                                            colorHex = category.colorHex,
                                            size = 40.dp,
                                            iconSize = 22.dp
                                        )

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = category.name,
                                                    style = MaterialTheme.typography.bodyLarge.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 15.5.sp
                                                    )
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = if (usageCount > 0) primaryColor.copy(alpha = 0.12f)
                                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                                ) {
                                                    Text(
                                                        text = if (usageCount > 0) "$usageCount 笔账单" else "未使用",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                                        color = if (usageCount > 0) primaryColor else MaterialTheme.colorScheme.outline,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                                    )
                                                }
                                            }
                                        }

                                        // 🌟 国际通用 6 点悬浮拖动抓手
                                        Box(
                                            modifier = Modifier.size(36.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DragIndicator,
                                                contentDescription = "按住拖动排序",
                                                tint = if (isCurrentDragging) primaryColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }

                                    // 子标签展示
                                    if (childTags.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "子标签: ",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = primaryColor
                                                )
                                                Text(
                                                    text = childTags.joinToString(" · "),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    modifier = Modifier.weight(1f)
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
    }

    // 🌟 删除分类确认弹窗
    ConfirmDeleteDialog(
        visible = categoryToDelete != null,
        title = "删除分类",
        message = "确定要删除分类「${categoryToDelete?.name}」及其专属子标签吗？",
        onConfirm = {
            categoryToDelete?.let {
                viewModel.deleteCategory(it)
                toast.success("已删除分类「${it.name}」")
            }
            categoryToDelete = null
        },
        onDismiss = { categoryToDelete = null }
    )

    // 分类已被使用时的阻断警告弹窗
    AlertInfoDialog(
        visible = uiState.errorDialogMessage != null,
        title = "无法删除分类",
        message = uiState.errorDialogMessage ?: "",
        onDismiss = { viewModel.clearErrorDialog() }
    )
}
