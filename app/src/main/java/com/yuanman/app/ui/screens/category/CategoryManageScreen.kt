package com.yuanman.app.ui.screens.category

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.yuanman.app.data.local.entity.CategoryEntity
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

    var showAddEditTagDialog by remember { mutableStateOf(false) }
    var tagToEdit by remember { mutableStateOf<String?>(null) }
    var tagToDelete by remember { mutableStateOf<String?>(null) }

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
                    if (uiState.currentTab == ManageTab.TAGS) {
                        tagToEdit = null
                        showAddEditTagDialog = true
                    } else {
                        categoryToEdit = null
                        showAddEditCategoryDialog = true
                    }
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = {
                    Text(
                        if (uiState.currentTab == ManageTab.TAGS) "新增标签" else "新增分类",
                        fontWeight = FontWeight.Bold
                    )
                },
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
            // 🌟 支出 / 收入 / 标签管理 3 Tab 切换
            TabRow(
                selectedTabIndex = uiState.currentTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                ManageTab.values().forEach { tab ->
                    val isSelected = uiState.currentTab == tab
                    Tab(
                        selected = isSelected,
                        onClick = { viewModel.switchTab(tab) },
                        text = {
                            Text(
                                text = tab.title,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.currentTab == ManageTab.TAGS) {
                // 🌟 标签管理列表
                if (uiState.customTags.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无自定义快捷标签，点击右下角新增",
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
                            items = uiState.customTags,
                            key = { it }
                        ) { tag ->
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value == SwipeToDismissBoxValue.EndToStart) {
                                        tagToDelete = tag
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
                                                .clickable { tagToDelete = tag }
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
                                                tagToEdit = tag
                                                showAddEditTagDialog = true
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = primaryColor.copy(alpha = 0.12f),
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Default.LocalOffer,
                                                        contentDescription = null,
                                                        tint = primaryColor,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(14.dp))

                                            Text(
                                                text = tag,
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontWeight = FontWeight.Medium,
                                                    fontSize = 15.sp
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f)
                                            )

                                            Text(
                                                text = "点击编辑 · 左滑删除",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            } else {
                // 🌟 分类管理列表
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
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CategoryIconView(
                                            iconName = category.iconName,
                                            colorHex = category.colorHex,
                                            size = 42.dp,
                                            iconSize = 22.dp
                                        )

                                        Spacer(modifier = Modifier.width(14.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = category.name,
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontWeight = FontWeight.Medium,
                                                    fontSize = 15.sp
                                                )
                                            )

                                            Spacer(modifier = Modifier.height(2.dp))

                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = if (usageCount > 0) primaryColor.copy(alpha = 0.12f)
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                            ) {
                                                Text(
                                                    text = if (usageCount > 0) "已记录 $usageCount 笔账单" else "未使用",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                                    color = if (usageCount > 0) primaryColor else MaterialTheme.colorScheme.outline,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Text(
                                            text = "点击编辑 · 左滑删除",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // 🌟 新增 / 编辑分类弹窗
    AddEditCategoryDialog(
        visible = showAddEditCategoryDialog,
        categoryToEdit = categoryToEdit,
        onDismiss = {
            showAddEditCategoryDialog = false
            categoryToEdit = null
        },
        onConfirm = { name, icon, colorHex ->
            if (categoryToEdit == null) {
                viewModel.addCategory(name, icon, colorHex)
                toast.success("已新增分类「$name」")
            } else {
                viewModel.updateCategory(categoryToEdit!!, name, icon, colorHex)
                toast.success("已修改分类「$name」")
            }
        }
    )

    // 🌟 新增 / 编辑标签弹窗
    if (showAddEditTagDialog) {
        var tagInput by remember { mutableStateOf(tagToEdit ?: "") }

        Dialog(onDismissRequest = {
            showAddEditTagDialog = false
            tagToEdit = null
        }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = if (tagToEdit == null) "新增快捷标签" else "编辑快捷标签",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )

                    OutlinedTextField(
                        value = tagInput,
                        onValueChange = { tagInput = it },
                        label = { Text("标签名称") },
                        placeholder = { Text("如: 咖啡、夜宵、打车") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                showAddEditTagDialog = false
                                tagToEdit = null
                            }
                        ) {
                            Text("取消")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                val trimmed = tagInput.trim()
                                if (trimmed.isNotEmpty()) {
                                    if (tagToEdit == null) {
                                        viewModel.addTag(trimmed)
                                        toast.success("已添加标签「$trimmed」")
                                    } else {
                                        viewModel.updateTag(tagToEdit!!, trimmed)
                                        toast.success("已修改标签为「$trimmed」")
                                    }
                                    showAddEditTagDialog = false
                                    tagToEdit = null
                                }
                            },
                            enabled = tagInput.isNotBlank()
                        ) {
                            Text("保存")
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
        message = "确定要删除分类「${categoryToDelete?.name}」吗？",
        onConfirm = {
            categoryToDelete?.let {
                viewModel.deleteCategory(it)
                toast.success("已删除分类「${it.name}」")
            }
            categoryToDelete = null
        },
        onDismiss = { categoryToDelete = null }
    )

    // 🌟 删除标签确认弹窗
    ConfirmDeleteDialog(
        visible = tagToDelete != null,
        title = "删除标签",
        message = "确定要删除快捷标签「${tagToDelete}」吗？",
        onConfirm = {
            tagToDelete?.let {
                viewModel.deleteTag(it)
                toast.success("已删除标签「$it」")
            }
            tagToDelete = null
        },
        onDismiss = { tagToDelete = null }
    )

    // 分类已被使用时的阻断警告弹窗
    AlertInfoDialog(
        visible = uiState.errorDialogMessage != null,
        title = "无法删除分类",
        message = uiState.errorDialogMessage ?: "",
        onDismiss = { viewModel.clearErrorDialog() }
    )
}
