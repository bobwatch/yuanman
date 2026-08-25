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
import androidx.compose.foundation.lazy.items
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
    var draggingCategoryId by remember { mutableStateOf<Long?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val primaryColor = MaterialTheme.colorScheme.primary

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
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 6.dp
            ) {
                Button(
                    onClick = { onNavigateToAddCategory(uiState.currentType) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .navigationBarsPadding(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("新增分类", fontWeight = FontWeight.Bold)
                }
            }
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
                    userScrollEnabled = (draggingCategoryId == null),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = orderedItems,
                        key = { it.category.id }
                    ) { item ->
                        val category = item.category
                        val usageCount = item.usageCount
                        val childTags = category.getTagList()
                        val isDragging = draggingCategoryId == category.id

                        val dragScale by animateFloatAsState(
                            targetValue = if (isDragging) 1.035f else 1.0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "dragScale"
                        )
                        val dragElevation by animateDpAsState(
                            targetValue = if (isDragging) 10.dp else 0.5.dp,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "dragElevation"
                        )

                        // 🌟 拖动期间彻底禁用并隐藏侧滑删除按钮
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
                                    containerColor = if (isDragging) MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
                                    else MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = dragElevation),
                                border = BorderStroke(
                                    if (isDragging) 1.5.dp else 0.5.dp,
                                    if (isDragging) primaryColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .zIndex(if (isDragging) 10f else 0f)
                                    .graphicsLayer {
                                        scaleX = dragScale
                                        scaleY = dragScale
                                        translationY = if (isDragging) dragOffsetY else 0f
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

                                        // 🌟 国际通用 6 点悬浮拖动抓手 (全局 PointerLock 拖动手势)
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .pointerInput(category.id) {
                                                    detectDragGesturesAfterLongPress(
                                                        onDragStart = {
                                                            openSwipeItemId = null
                                                            draggingCategoryId = category.id
                                                            dragOffsetY = 0f
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        },
                                                        onDrag = { change, dragAmount ->
                                                            // Consume the drag after the long press so LazyColumn and
                                                            // the swipe detector cannot swallow the movement first.
                                                            change.consume()
                                                            dragOffsetY += dragAmount.y

                                                            val itemsSnapshot = orderedItems
                                                            val currentIndex = itemsSnapshot.indexOfFirst { it.category.id == category.id }
                                                            if (currentIndex >= 0) {
                                                                val currentItemInfo = listState.layoutInfo.visibleItemsInfo
                                                                    .firstOrNull { it.key == category.id }
                                                                val itemHeight = (currentItemInfo?.size ?: 200).toFloat()
                                                                val threshold = itemHeight * 0.42f

                                                                if (dragOffsetY > threshold && currentIndex < itemsSnapshot.size - 1) {
                                                                    val reordered = itemsSnapshot.toMutableList()
                                                                    val movedItem = reordered.removeAt(currentIndex)
                                                                    reordered.add(currentIndex + 1, movedItem)
                                                                    orderedItems = reordered
                                                                    dragOffsetY -= itemHeight
                                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                } else if (dragOffsetY < -threshold && currentIndex > 0) {
                                                                    val reordered = itemsSnapshot.toMutableList()
                                                                    val movedItem = reordered.removeAt(currentIndex)
                                                                    reordered.add(currentIndex - 1, movedItem)
                                                                    orderedItems = reordered
                                                                    dragOffsetY += itemHeight
                                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                }

                                                                // 边缘自动滚屏
                                                                if (currentItemInfo != null) {
                                                                    val viewportHeight = listState.layoutInfo.viewportSize.height.toFloat()
                                                                    val currentTop = currentItemInfo.offset + dragOffsetY
                                                                    if (currentTop < 70f) {
                                                                        coroutineScope.launch { listState.scrollBy(-14f) }
                                                                    } else if (currentTop + itemHeight > viewportHeight - 90f) {
                                                                        coroutineScope.launch { listState.scrollBy(14f) }
                                                                    }
                                                                }
                                                            }
                                                        },
                                                        onDragEnd = {
                                                            // 手指抬起结束拖动
                                                            viewModel.updateCategoryOrder(orderedItems.map { it.category.id })
                                                            draggingCategoryId = null
                                                            dragOffsetY = 0f
                                                        },
                                                        onDragCancel = {
                                                            draggingCategoryId = null
                                                            dragOffsetY = 0f
                                                        }
                                                    )
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DragIndicator,
                                                contentDescription = "按住拖动排序",
                                                tint = if (isDragging) primaryColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
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
