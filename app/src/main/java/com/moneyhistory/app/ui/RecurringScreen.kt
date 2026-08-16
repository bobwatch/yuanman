package com.moneyhistory.app.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyhistory.app.Categories
import com.moneyhistory.app.MainViewModel
import com.moneyhistory.app.MessageVariant
import com.moneyhistory.app.MoneyUtils
import com.moneyhistory.app.R
import com.moneyhistory.app.RecurringExpense

/** 周期账单管理：列表 + 删除（不做编辑，删掉重录）。 */
@Composable
fun RecurringScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val recurring by viewModel.recurring.collectAsStateWithLifecycle()
    val datePattern = stringResource(R.string.date_pattern)
    val deletedText = stringResource(R.string.common_deleted)
    var deleteTarget by remember { mutableStateOf<RecurringExpense?>(null) }
    val view = LocalView.current

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        SubPageHeader(
            title = stringResource(R.string.recurring_title),
            onBack = onBack
        )

        if (recurring.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    emoji = "🔁",
                    title = stringResource(R.string.recurring_empty_title),
                    subtitle = stringResource(R.string.recurring_empty_sub),
                    // 创建入口藏在首页记账面板的「周期」开关里，空态给直达按钮：
                    // 返回首页并打开面板、预勾选周期开关，用户不用自己摸索
                    actionLabel = stringResource(R.string.recurring_empty_action),
                    onAction = {
                        viewModel.requestRecurringSheet()
                        onBack()
                    }
                )
            }
        } else {
            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 4.dp)
            ) {
                items(recurring, key = { it.id }) { r ->
                    AppCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconTile(
                                icon = categoryIcon(r.category),
                                tint = MaterialTheme.colorScheme.primary,
                                container = MaterialTheme.colorScheme.primaryContainer,
                                size = 42.dp,
                                iconSize = 20.dp
                            )
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                // 备注为空时不显示「分类 · 」的裸分隔符
                                val rowTitle = if (r.note.isEmpty()) {
                                    Categories.displayName(r.category)
                                } else {
                                    stringResource(
                                        R.string.recurring_row_title,
                                        Categories.displayName(r.category),
                                        r.note
                                    )
                                }
                                Text(
                                    text = rowTitle,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = stringResource(
                                        R.string.recurring_row_subtitle,
                                        cycleLabel(r.cycle),
                                        formatSheetDate(r.nextDue, datePattern)
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            // 金额优先占位（标题列弹性让位，常规宽度完整展示）；
                            // 极端窄屏/超大字号下兜底省略号，不与删除键重叠
                            Text(
                                text = MoneyUtils.formatCents(r.amountCents),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            IconButton(onClick = { deleteTarget = r }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription =
                                        stringResource(R.string.recurring_delete),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { r ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.recurring_delete_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.recurring_delete_confirm_msg,
                        Categories.displayName(r.category),
                        MoneyUtils.formatCents(r.amountCents)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    // 删除成功反馈与全 App 一致：轻震动确认
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    viewModel.removeRecurring(r.id)
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
