package com.yuanman.app.ui.screens.category

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.yuanman.app.data.local.entity.CategoryEntity
import com.yuanman.app.data.model.CategoryIconHelper
import com.yuanman.app.ui.components.CategoryIconView

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditCategoryDialog(
    visible: Boolean,
    categoryToEdit: CategoryEntity? = null,
    onDismiss: () -> Unit,
    onConfirm: (name: String, iconName: String, colorHex: Long, tags: List<String>) -> Unit
) {
    if (!visible) return

    var name by remember(categoryToEdit) { mutableStateOf(categoryToEdit?.name ?: "") }
    var selectedIcon by remember(categoryToEdit) {
        mutableStateOf(categoryToEdit?.iconName ?: CategoryIconHelper.AVAILABLE_ICONS.first().key)
    }
    var selectedColor by remember(categoryToEdit) {
        mutableStateOf(categoryToEdit?.colorHex ?: CategoryIconHelper.PRESET_COLORS.first())
    }
    var tagList by remember(categoryToEdit) {
        mutableStateOf(categoryToEdit?.getTagList() ?: CategoryIconHelper.getPresetRemarks(name).ifEmpty { listOf("默认标签") })
    }

    var newTagInput by remember { mutableStateOf("") }
    var isAddingTag by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (categoryToEdit == null) "新增分类与子标签" else "编辑分类与子标签",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 分类预览图标
                CategoryIconView(
                    iconName = selectedIcon,
                    colorHex = selectedColor,
                    size = 52.dp,
                    iconSize = 28.dp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 分类名称输入框
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        if (it.length <= 8) {
                            name = it
                            errorText = null
                            if (categoryToEdit == null && tagList.isEmpty()) {
                                tagList = CategoryIconHelper.getPresetRemarks(it)
                            }
                        }
                    },
                    label = { Text("分类名称") },
                    placeholder = { Text("如：餐饮、数码") },
                    singleLine = true,
                    isError = errorText != null,
                    supportingText = {
                        if (errorText != null) {
                            Text(errorText!!, color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("${name.length}/8 字")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 🌟 子标签管理模块
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "专属子标签 (${tagList.size})",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "记账时自动联想",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 子标签流式芯片展示
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tagList.forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            border = androidx.compose.foundation.BorderStroke(
                                0.5.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = tag,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "删除标签",
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            tagList = tagList.filterNot { it == tag }
                                        }
                                )
                            }
                        }
                    }

                    // 添加新标签按钮或输入框
                    if (isAddingTag) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            OutlinedTextField(
                                value = newTagInput,
                                onValueChange = { if (it.length <= 8) newTagInput = it },
                                placeholder = { Text("标签名", fontSize = 11.sp) },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                                modifier = Modifier.width(90.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            TextButton(
                                onClick = {
                                    val trimmed = newTagInput.trim()
                                    if (trimmed.isNotEmpty() && !tagList.contains(trimmed)) {
                                        tagList = tagList + trimmed
                                    }
                                    newTagInput = ""
                                    isAddingTag = false
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Text("确定", fontSize = 12.sp)
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { isAddingTag = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "添加标签",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "添加标签",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 选择图标
                Text(
                    text = "选择图标",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Box(modifier = Modifier.height(120.dp)) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(CategoryIconHelper.AVAILABLE_ICONS) { iconInfo ->
                            val isSelected = selectedIcon == iconInfo.key
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) Color(selectedColor).copy(alpha = 0.2f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    )
                                    .border(
                                        width = if (isSelected) 2.dp else 0.dp,
                                        color = if (isSelected) Color(selectedColor) else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedIcon = iconInfo.key },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = iconInfo.icon,
                                    contentDescription = iconInfo.name,
                                    tint = if (isSelected) Color(selectedColor) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 选择颜色
                Text(
                    text = "选择主题色",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(CategoryIconHelper.PRESET_COLORS) { colorHex ->
                        val isSelected = selectedColor == colorHex
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(colorHex))
                                .border(
                                    width = if (isSelected) 3.dp else 0.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = colorHex }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 按钮栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("取消")
                    }

                    Button(
                        onClick = {
                            val trimmed = name.trim()
                            if (trimmed.isEmpty()) {
                                errorText = "分类名称不能为空"
                                return@Button
                            }
                            onConfirm(trimmed, selectedIcon, selectedColor, tagList)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }
}
