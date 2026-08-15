package com.moneyhistory.app.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyhistory.app.DateUtils
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
                    val datePattern = stringResource(R.string.date_pattern)
                    // 存储的解锁日期是固定 yyyy-MM-dd，展示时按当前语言环境重排
                    val obtainedText = remember(unlockedDate, datePattern) {
                        unlockedDate?.let { raw ->
                            DateUtils.parse(raw)?.let {
                                formatSheetDate(it, datePattern)
                            }
                        } ?: ""
                    }
                    // 今天刚解锁的勋章：进页时弹一下——努力被看见的时刻值得一个仪式感
                    val fresh = unlocked && unlockedDate == DateUtils.today()
                    val popScale = remember { Animatable(1f) }
                    val view = LocalView.current
                    LaunchedEffect(fresh) {
                        if (fresh) {
                            // 弹跳动效 + 确认触感：解锁的「落定感」让努力被看见
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            popScale.snapTo(0.6f)
                            popScale.animateTo(
                                targetValue = 1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )
                        }
                    }
                    AppCard(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .graphicsLayer {
                                scaleX = popScale.value
                                scaleY = popScale.value
                            }
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
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
                                        if (fresh) {
                                            stringResource(R.string.badge_obtained_today)
                                        } else {
                                            stringResource(
                                                R.string.badge_obtained,
                                                obtainedText
                                            )
                                        }
                                    } else {
                                        stringResource(badge.descRes)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (fresh) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                            if (unlocked) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
