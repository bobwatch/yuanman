package com.moneyhistory.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyhistory.app.MainViewModel
import com.moneyhistory.app.R
import com.moneyhistory.app.allBadges

/** 勋章墙：已获得彩色 + 获得日期；未获得灰色 + 解锁条件。 */
@Composable
fun BadgeScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val unlocks by viewModel.settings.badgeUnlocks.collectAsStateWithLifecycle()
    val grouped = allBadges.groupBy { it.categoryRes }

    Column(Modifier.fillMaxSize()) {
        SubPageHeader(
            title = stringResource(
                R.string.badge_screen_title,
                unlocks.size,
                allBadges.size
            ),
            onBack = onBack
        )

        LazyColumn(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 4.dp)
        ) {
            grouped.forEach { (categoryRes, badges) ->
                item(key = "header_$categoryRes") {
                    SectionTitle(stringResource(categoryRes))
                }
                items(badges.size, key = { badges[it].id }) { index ->
                    val badge = badges[index]
                    val unlockedDate = unlocks[badge.id]
                    val unlocked = unlockedDate != null
                    Card(
                        shape = RoundedCornerShape(20.dp),
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
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconTile(
                                icon = badgeIcon(badge.id),
                                tint = if (unlocked) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                },
                                container = if (unlocked) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                },
                                size = 48.dp,
                                iconSize = 24.dp,
                                modifier = Modifier.alpha(if (unlocked) 1f else 0.6f)
                            )
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(badge.titleRes),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (unlocked) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                Text(
                                    text = if (unlocked) {
                                        stringResource(
                                            R.string.badge_obtained,
                                            unlockedDate ?: ""
                                        )
                                    } else {
                                        stringResource(badge.descRes)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (unlocked) {
                                Text(text = "✓", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}
