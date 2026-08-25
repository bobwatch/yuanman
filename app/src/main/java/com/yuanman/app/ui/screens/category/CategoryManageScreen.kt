package com.yuanman.app.ui.screens.category

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManageScreen(
    viewModel: CategoryManageViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    var showAddEditDialog by remember { mutableStateOf(false) }
    var categoryToEdit by remember { mutableStateOf<CategoryEntity?>(null) }
    var categoryToDelete by remember { mutableStateOf<CategoryEntity?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("分类管理", fontWeight = FontWeight.Bold) }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    categoryToEdit = null
                    showAddEditDialog = true
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("新增分类") },
                containerColor = MaterialTheme.colorScheme.primary,
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
            // 支出 / 收入 切换 Tab
            TabRow(
                selectedTabIndex = if (uiState.currentType == RecordType.EXPENSE) 0 else 1,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = uiState.currentType == RecordType.EXPENSE,
                    onClick = { viewModel.switchType(RecordType.EXPENSE) },
                    text = {
                        Text(
                            "支出分类",
                            fontWeight = if (uiState.currentType == RecordType.EXPENSE) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = uiState.currentType == RecordType.INCOME,
                    onClick = { viewModel.switchType(RecordType.INCOME) },
                    text = {
                        Text(
                            "收入分类",
                            fontWeight = if (uiState.currentType == RecordType.INCOME) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = uiState.categoriesWithUsage,
                    key = { it.category.id }
                ) { item ->
                    val category = item.category
                    val usageCount = item.usageCount

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
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
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (usageCount > 0) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ) {
                                    Text(
                                        text = if (usageCount > 0) "已记录 $usageCount 笔账单" else "未使用",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        color = if (usageCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // 编辑按钮
                            IconButton(onClick = {
                                categoryToEdit = category
                                showAddEditDialog = true
                            }) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "编辑",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // 删除按钮
                            IconButton(onClick = {
                                categoryToDelete = category
                            }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "删除",
                                    tint = if (usageCount > 0) MaterialTheme.colorScheme.outline.copy(alpha = 0.4f) else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 新增 / 编辑分类弹窗
    AddEditCategoryDialog(
        visible = showAddEditDialog,
        categoryToEdit = categoryToEdit,
        onDismiss = {
            showAddEditDialog = false
            categoryToEdit = null
        },
        onConfirm = { name, icon, colorHex ->
            if (categoryToEdit == null) {
                viewModel.addCategory(name, icon, colorHex)
            } else {
                viewModel.updateCategory(categoryToEdit!!, name, icon, colorHex)
            }
        }
    )

    // 删除分类确认弹窗
    ConfirmDeleteDialog(
        visible = categoryToDelete != null,
        title = "删除分类",
        message = "确定要删除分类「${categoryToDelete?.name}」吗？",
        onConfirm = {
            categoryToDelete?.let { viewModel.deleteCategory(it) }
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
