package com.moneyhistory.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyhistory.app.Categories
import com.moneyhistory.app.MainViewModel
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

    val errorEmptyText = stringResource(R.string.cats_error_empty)
    val errorDupText = stringResource(R.string.cats_error_dup)
    val addedText = stringResource(R.string.cats_added)

    Column(Modifier.fillMaxSize()) {
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
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Categories.emojiCandidates.forEach { emoji ->
                                FilterChip(
                                    selected = selectedEmoji == emoji,
                                    onClick = { selectedEmoji = emoji },
                                    label = { Text(emoji) }
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it.take(8) },
                                label = { Text(stringResource(R.string.cats_name_hint)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.size(8.dp))
                            Button(onClick = {
                                val trimmed = name.trim()
                                when {
                                    trimmed.isEmpty() ->
                                        viewModel.postMessage(errorEmptyText)
                                    !viewModel.addCustomCategory("$selectedEmoji $trimmed") ->
                                        viewModel.postMessage(errorDupText)
                                    else -> {
                                        name = ""
                                        viewModel.postMessage(addedText)
                                    }
                                }
                            }) {
                                Text(stringResource(R.string.common_add))
                            }
                        }
                    }
                }
            }

            // 自定义分类（可删）
            if (customCategories.isNotEmpty()) {
                item(key = "custom_header") {
                    SectionHeader(stringResource(R.string.cats_custom))
                }
                items(customCategories.size, key = { "custom_$it" }) { index ->
                    val category = customCategories[index]
                    CategoryRow(
                        category = category,
                        deletable = true,
                        onDelete = { viewModel.removeCustomCategory(category) }
                    )
                }
            }

            // 预设分类（不可删）
            item(key = "preset_expense_header") {
                SectionHeader(stringResource(R.string.cats_preset_expense))
            }
            items(Categories.expense.size, key = { "preset_e_$it" }) { index ->
                CategoryRow(
                    category = Categories.expense[index],
                    deletable = false,
                    onDelete = {}
                )
            }
            item(key = "preset_income_header") {
                SectionHeader(stringResource(R.string.cats_preset_income))
            }
            items(Categories.income.size, key = { "preset_i_$it" }) { index ->
                CategoryRow(
                    category = Categories.income[index],
                    deletable = false,
                    onDelete = {}
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    SectionTitle(title)
}

@Composable
private fun CategoryRow(
    category: String,
    deletable: Boolean,
    onDelete: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = category,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        if (deletable) {
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
