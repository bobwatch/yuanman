package com.yuanman.app.ui.screens.category

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuanman.app.data.local.entity.CategoryEntity
import com.yuanman.app.data.model.RecordType
import com.yuanman.app.ui.components.AlertInfoDialog
import com.yuanman.app.ui.components.CategoryIconView
import com.yuanman.app.ui.components.ConfirmDeleteDialog
import com.yuanman.app.ui.components.LocalToastHostState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManageScreen(
    viewModel: CategoryManageViewModel,
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val toast = LocalToastHostState.current

    var showAddEditCategoryDialog by remember { mutableStateOf(false) }
    var categoryToEdit by remember { mutableStateOf<CategoryEntity?>(null) }
    var categoryToDelete by remember { mutableStateOf<CategoryEntity?>(null) }

    val primaryColor = MaterialTheme.colorScheme.primary

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("分类与标签管理", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    categoryToEdit = null
                    showAddEditCategoryDialog = true
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("新增分类", fontWeight = FontWeight.Bold) },
                containerColor = primaryColor,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(bottom = 76.dp)
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

            Spacer(modifier = Modifier.height(8.dp))

            // 🌟 分类及其专属子标签列表
            if (uiState.categoriesWithUsage.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无分类，点击右下角新增",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = uiState.categoriesWithUsage,
                        key = { it.category.id }
                    ) { item ->
                        val category = item.category
                        val usageCount = item.usageCount
                        val childTags = category.getTagList()

                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    if (usageCount > 0) {
                                        viewModel.deleteCategory(category)
                                    } else {
                                        categoryToDelete = category
                                    }
                                    false
                                } else {
                                    false
                                }
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            enableDismissFromEndToStart = true,
                            backgroundContent = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.errorContainer),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .clickable {
                                                if (usageCount > 0) {
                                                    viewModel.deleteCategory(category)
                                                } else {
                                                    categoryToDelete = category
                                                }
                                            }
                                            .padding(horizontal = 20.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "删除",
                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "删除",
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp
                                        )
                                    }
                                }
                            },
                            content = {
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            categoryToEdit = category
                                            showAddEditCategoryDialog = true
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

                                            Text(
                                                text = "点击编辑 · 左滑删除",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
                                            )
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
                        )
                    }
                }
            }
        }
    }

    // 🌟 新增 / 编辑分类及其子标签弹窗
    AddEditCategoryDialog(
        visible = showAddEditCategoryDialog,
        categoryToEdit = categoryToEdit,
        onDismiss = {
            showAddEditCategoryDialog = false
            categoryToEdit = null
        },
        onConfirm = { name, icon, colorHex, tags ->
            if (categoryToEdit == null) {
                viewModel.addCategory(name, icon, colorHex, tags)
                toast.success("已新增分类「$name」及其专属标签")
            } else {
                viewModel.updateCategory(categoryToEdit!!, name, icon, colorHex, tags)
                toast.success("已更新分类「$name」与子标签")
            }
        }
    )

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
