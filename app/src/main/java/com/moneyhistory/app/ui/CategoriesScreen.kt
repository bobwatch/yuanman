package com.moneyhistory.app.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyhistory.app.Categories
import com.moneyhistory.app.MainViewModel
import com.moneyhistory.app.MessageVariant
import com.moneyhistory.app.R
import com.moneyhistory.app.codePointLength
import com.moneyhistory.app.truncateByCodePoints

/** 自定义分类名上限（8 个码点；emoji 按 1 个字符计）。 */
private const val CATEGORY_NAME_MAX = 8

/** 自定义颜文字输入上限（码点；家族 emoji 等复合字符也放得下）。 */
private const val CUSTOM_EMOJI_MAX = 4

/** 分类管理：支出/收入两组统一管理（均含默认分类），可增可删可改名。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoriesScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val expenseCategories by viewModel.expenseCategories.collectAsStateWithLifecycle()
    val incomeCategories by viewModel.incomeCategories.collectAsStateWithLifecycle()

    var selectedEmoji by remember { mutableStateOf(Categories.emojiCandidates.first()) }
    var customEmoji by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    // 新增分类的归属：支出（默认）/ 收入
    var addIsExpense by remember { mutableStateOf(true) }
    // 删除/重命名目标：分类 + 归属类型（支出 true / 收入 false）
    var deleteTarget by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var renameTarget by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var addPickerOpen by remember { mutableStateOf(false) }
    var renamePickerOpen by remember { mutableStateOf(false) }
    // 重命名对话框状态提升到顶层：弹层选择器（sheet）与对话框内快捷行共用一份状态
    var renameEmoji by remember(renameTarget) {
        mutableStateOf(renameTarget?.let { Categories.emojiOf(it.first) } ?: "")
    }
    var renameName by remember(renameTarget) {
        mutableStateOf(renameTarget?.let { Categories.nameOf(it.first) } ?: "")
    }
    var renameCustomEmoji by remember(renameTarget) { mutableStateOf("") }
    val view = LocalView.current

    val errorDupText = stringResource(R.string.cats_error_dup)
    val addedText = stringResource(R.string.cats_added)
    val deletedText = stringResource(R.string.common_deleted)
    val renamedText = stringResource(R.string.cats_renamed)

    // 自定义输入框非空时优先于候选选中（自己输入的颜文字）
    val effectiveEmoji = customEmoji.trim().ifEmpty { selectedEmoji }

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
            // 添加分类（先选支出/收入归属，再选图标与名称）
            item(key = "add") {
                AppCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.cats_add_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        // 新增分类先选归属：支出 / 收入
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(
                                selected = addIsExpense,
                                onClick = { addIsExpense = true },
                                label = { Text(stringResource(R.string.sheet_type_expense)) }
                            )
                            Spacer(Modifier.width(8.dp))
                            FilterChip(
                                selected = !addIsExpense,
                                onClick = { addIsExpense = false },
                                label = { Text(stringResource(R.string.sheet_type_income)) }
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        // 页面上只铺 18 个常用颜文字，行尾「更多」点开完整选择器（弹层）
                        EmojiQuickRow(
                            selected = selectedEmoji,
                            onSelect = {
                                selectedEmoji = it
                                customEmoji = ""
                            },
                            onMore = { addPickerOpen = true }
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = {
                                    // 按码点截断：emoji 占 2 个 UTF-16 单元，take() 会切半成乱码
                                    name = truncateByCodePoints(it, CATEGORY_NAME_MAX)
                                },
                                label = { Text(stringResource(R.string.cats_name_hint)) },
                                // 计数行常驻：开始打字时下方不突然多出一行
                                supportingText = {
                                    Text(
                                        stringResource(
                                            R.string.cats_name_count,
                                            codePointLength(name),
                                            CATEGORY_NAME_MAX
                                        )
                                    )
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
                                    if (!viewModel.addCategory("$effectiveEmoji $trimmed", addIsExpense)) {
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

            // 支出分类（可删可改名；key 加前缀防与收入组冲突）
            item(key = "expense_header") {
                SectionTitle(stringResource(R.string.cats_expense))
            }
            items(expenseCategories, key = { "e_$it" }) { category ->
                CategoryRow(
                    category = category,
                    onRename = { renameTarget = category to true },
                    onDelete = { deleteTarget = category to true }
                )
            }

            // 收入分类（可删可改名）
            item(key = "income_header") {
                SectionTitle(stringResource(R.string.cats_income))
            }
            items(incomeCategories, key = { "i_$it" }) { category ->
                CategoryRow(
                    category = category,
                    onRename = { renameTarget = category to false },
                    onDelete = { deleteTarget = category to false }
                )
            }
        }
    }

    deleteTarget?.let { (category, isExpense) ->
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
                    viewModel.removeCategory(category, isExpense)
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

    renameTarget?.let { (old, isExpense) ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.cats_rename_title)) },
            text = {
                Column {
                    EmojiQuickRow(
                        selected = renameEmoji,
                        onSelect = {
                            renameEmoji = it
                            renameCustomEmoji = ""
                        },
                        onMore = { renamePickerOpen = true }
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = renameName,
                        onValueChange = {
                            renameName = truncateByCodePoints(it, CATEGORY_NAME_MAX)
                        },
                        label = { Text(stringResource(R.string.cats_name_hint)) },
                        supportingText = {
                            Text(
                                stringResource(
                                    R.string.cats_name_count,
                                    codePointLength(renameName),
                                    CATEGORY_NAME_MAX
                                )
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = renameName.isNotBlank(),
                    onClick = {
                        val new = "$renameEmoji ${renameName.trim()}"
                        if (!viewModel.renameCategory(old, new, isExpense)) {
                            // 重名（含预设）/ 原名不存在：提示后留在对话框可改
                            viewModel.postMessage(errorDupText, MessageVariant.WARNING)
                        } else {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            viewModel.postMessage(renamedText, MessageVariant.SUCCESS)
                            renameTarget = null
                        }
                    }
                ) {
                    Text(stringResource(R.string.common_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // 完整图标选择器：添加卡片 / 重命名对话框共用弹层，选中即关
    if (addPickerOpen) {
        EmojiPickerSheet(
            selected = selectedEmoji,
            customEmoji = customEmoji,
            onCustomEmojiChange = { customEmoji = it },
            onSelect = {
                selectedEmoji = it
                customEmoji = ""
            },
            onDismiss = { addPickerOpen = false }
        )
    }
    if (renamePickerOpen) {
        EmojiPickerSheet(
            selected = renameEmoji,
            customEmoji = renameCustomEmoji,
            onCustomEmojiChange = { renameCustomEmoji = it },
            onSelect = {
                renameEmoji = it
                renameCustomEmoji = ""
                renamePickerOpen = false
            },
            onDismiss = { renamePickerOpen = false }
        )
    }
}

/** 添加 / 重命名卡片上的快捷颜文字行：18 个常用直接点选，行尾「更多」展开完整选择器。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmojiQuickRow(
    selected: String,
    onSelect: (String) -> Unit,
    onMore: () -> Unit
) {
    val view = LocalView.current
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Categories.emojiQuick.forEach { emoji ->
            Surface(
                shape = CircleShape,
                color = if (emoji == selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier
                    .size(44.dp)
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        onSelect(emoji)
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(emoji, fontSize = 20.sp)
                }
            }
        }
        // 「更多」入口：弹出完整选择器（常用/全部全集 + 自定义输入）
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .size(44.dp)
                .clickable(onClick = onMore)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.cats_emoji_more),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/** 完整颜文字选择器（底部弹层）：常用 / 全部切换 + 可滚动全集 + 自定义输入，选中即关。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EmojiPickerSheet(
    selected: String,
    customEmoji: String,
    onCustomEmojiChange: (String) -> Unit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var allTab by remember { mutableStateOf(false) }
    // 默认部分展开只有半屏高，网格会把输入/完成挤出可视区；完全展开保证底部常驻
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        // 弹层高度固定为屏幕 3/4：网格占剩余空间滚动，输入/完成常驻底部可见
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.cats_emoji_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = !allTab,
                    onClick = { allTab = false },
                    label = { Text(stringResource(R.string.cats_emoji_common)) }
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = allTab,
                    onClick = { allTab = true },
                    label = { Text(stringResource(R.string.cats_emoji_all)) }
                )
            }
            Spacer(Modifier.height(8.dp))
            // 网格单独滚动：全集也放得下，标题/输入区不被顶出弹层
            // 注意顺序：weight 必须在外层，Column 才能识别并分配剩余空间
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                EmojiPickerRow(
                    candidates = if (allTab) {
                        Categories.emojiCandidatesAll
                    } else {
                        Categories.emojiCandidates
                    },
                    selected = selected,
                    onSelect = {
                        onSelect(it)
                        onDismiss()
                    }
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = customEmoji,
                onValueChange = {
                    // 自定义颜文字按码点截断（emoji 占 2 个 UTF-16 单元）
                    onCustomEmojiChange(truncateByCodePoints(it, CUSTOM_EMOJI_MAX))
                },
                label = { Text(stringResource(R.string.cats_emoji_custom)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    // 自己输入的颜文字优先；没输入时维持当前选中
                    onSelect(customEmoji.trim().ifEmpty { selected })
                    onDismiss()
                }) {
                    Text(stringResource(R.string.cats_emoji_done))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CategoryRow(
    category: String,
    onRename: () -> Unit,
    onDelete: () -> Unit
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
            size = 56.dp,
            iconSize = 32.dp
        )
        Spacer(Modifier.size(12.dp))
        Text(
            text = Categories.displayName(category),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onRename) {
            Icon(
                Icons.Outlined.Edit,
                contentDescription = stringResource(R.string.cats_rename_title),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.common_delete),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
