package com.moneyhistory.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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

    Column(Modifier.fillMaxSize()) {
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
                    subtitle = stringResource(R.string.recurring_empty_sub)
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
                    Card(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 14.dp),
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
                                Text(
                                    text = Categories.nameOf(r.category) +
                                        if (r.note.isNotEmpty()) " · ${r.note}" else "",
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
                            Text(
                                text = MoneyUtils.formatCents(r.amountCents),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
                        Categories.nameOf(r.category),
                        MoneyUtils.formatCents(r.amountCents)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
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
