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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yuanman.app.data.local.entity.CategoryEntity
import com.yuanman.app.data.model.CategoryIconHelper
import com.yuanman.app.ui.components.CategoryIconView

@Composable
fun AddEditCategoryDialog(
    visible: Boolean,
    categoryToEdit: CategoryEntity? = null,
    onDismiss: () -> Unit,
    onConfirm: (name: String, iconName: String, colorHex: Long) -> Unit
) {
    if (!visible) return

    var name by remember(categoryToEdit) { mutableStateOf(categoryToEdit?.name ?: "") }
    var selectedIcon by remember(categoryToEdit) {
        mutableStateOf(categoryToEdit?.iconName ?: CategoryIconHelper.AVAILABLE_ICONS.first().key)
    }
    var selectedColor by remember(categoryToEdit) {
        mutableStateOf(categoryToEdit?.colorHex ?: CategoryIconHelper.PRESET_COLORS.first())
    }
    var errorText by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (categoryToEdit == null) "新增分类" else "编辑分类",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 分类预览小卡片
                CategoryIconView(
                    iconName = selectedIcon,
                    colorHex = selectedColor,
                    size = 56.dp,
                    iconSize = 30.dp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 分类名称输入框
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        if (it.length <= 8) {
                            name = it
                            errorText = null
                        }
                    },
                    label = { Text("分类名称") },
                    placeholder = { Text("例如：零食、健身") },
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

                Spacer(modifier = Modifier.height(12.dp))

                // 选择图标
                Text(
                    text = "选择图标",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Box(modifier = Modifier.height(130.dp)) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(CategoryIconHelper.AVAILABLE_ICONS) { iconInfo ->
                            val isSelected = selectedIcon == iconInfo.key
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
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
                                    modifier = Modifier.size(22.dp)
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
                                .size(32.dp)
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

                Spacer(modifier = Modifier.height(24.dp))

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
                            onConfirm(trimmed, selectedIcon, selectedColor)
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
