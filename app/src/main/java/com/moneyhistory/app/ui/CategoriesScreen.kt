package com.moneyhistory.app.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyhistory.app.Categories
import com.moneyhistory.app.MainViewModel
import com.moneyhistory.app.MessageVariant
import com.moneyhistory.app.R

/** 分类管理：预设分类不可删，自定义分类（emoji + 名称）可增可删。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoriesScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val customCategories by viewModel.customCategories.collectAsStateWithLifecycle()

    var selectedEmoji by remember { mutableStateOf(Categories.emojiCandidates.first()) }
    var name by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    val view = LocalView.current

    val errorDupText = stringResource(R.string.cats_error_dup)
    val addedText = stringResource(R.string.cats_added)
    val deletedText = stringResource(R.string.common_deleted)

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        SubPageHeader(
            title = stringResource(R.string.cats_title),
            onBack = onBack
        )

        LazyColumn(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 4.dp)
        ) {
            // 添加自定义分类
            item(key = "add") {
                AppCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.cats_add_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        EmojiPickerRow(
                            candidates = Categories.emojiCandidates,
                            selected = selectedEmoji,
                            onSelect = { selectedEmoji = it }
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it.take(8) },
                                label = { Text(stringResource(R.string.cats_name_hint)) },
                                // 上限 8 字：边输边显示计数，不会「打字突然卡住」的错觉
                                supportingText = {
                                    if (name.isNotEmpty()) {
                                        Text(
                                            stringResource(
                                                R.string.cats_name_count,
                                                name.length,
                                                8
                                            )
                                        )
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.size(8.dp))
                            // 最小与输入框同高；大字号下随内容自然长高，不会「吊高」；
                            // 名称为空（含纯空格）时禁按，比点了再报错更先一步给出反馈
                            Button(
                                onClick = {
                                    val trimmed = name.trim()
                                    if (!viewModel.addCustomCategory("$selectedEmoji $trimmed")) {
                                        viewModel.postMessage(errorDupText, MessageVariant.WARNING)
                                    } else {
                                        name = ""
                                        viewModel.postMessage(addedText, MessageVariant.SUCCESS)
                                    }
                                },
                                enabled = name.isNotBlank(),
                                modifier = Modifier.heightIn(min = 56.dp)
                            ) {
                                Text(stringResource(R.string.common_add))
                            }
                        }
                    }
                }
            }

            // 自定义分类（可删）
            if (customCategories.isNotEmpty()) {
                item(key = "custom_header") {
                    SectionTitle(stringResource(R.string.cats_custom))
                }
                // 自定义分类可删，以分类名作 key（名称唯一），删除后列表项身份不漂移
                items(customCategories, key = { it }) { category ->
                    CategoryRow(
                        category = category,
                        deletable = true,
                        onDelete = { deleteTarget = category }
                    )
                }
            }

            // 预设分类（不可删）
            item(key = "preset_expense_header") {
                SectionTitle(stringResource(R.string.cats_preset_expense))
            }
            items(Categories.expense.size, key = { "preset_e_$it" }) { index ->
                CategoryRow(
                    category = Categories.expense[index],
                    deletable = false,
                    onDelete = null
                )
            }
            item(key = "preset_income_header") {
                SectionTitle(stringResource(R.string.cats_preset_income))
            }
            items(Categories.income.size, key = { "preset_i_$it" }) { index ->
                CategoryRow(
                    category = Categories.income[index],
                    deletable = false,
                    onDelete = null
                )
            }
        }
    }

    deleteTarget?.let { category ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.cats_delete_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.cats_delete_confirm_msg,
                        Categories.displayName(category)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    // 删除成功反馈与全 App 一致：轻震动确认
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    viewModel.removeCustomCategory(category)
                    viewModel.postMessage(deletedText, MessageVariant.INFO)
                    deleteTarget = null
                }) {
                    Text(
                        stringResource(R.string.common_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun CategoryRow(
    category: String,
    deletable: Boolean,
    onDelete: (() -> Unit)?
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(
            icon = categoryIcon(category),
            tint = MaterialTheme.colorScheme.primary,
            container = MaterialTheme.colorScheme.primaryContainer,
            size = 36.dp,
            iconSize = 18.dp
        )
        Spacer(Modifier.size(12.dp))
        Text(
            text = Categories.displayName(category),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        if (deletable && onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.common_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
