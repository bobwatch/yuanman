package com.yuanman.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.yuanman.app.utils.MoneyUtils
import java.text.DecimalFormat
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * 🌟 月度预算拖动设置弹窗（首页看板 / 设置页共用）
 *
 * 拖动滑杆选金额代替键盘输入：档位按数量级呈对数分布（100~100,000 元，
 * 低金额区档位密、高金额区档位疏），拖动过程大字号实时回显；
 * 打开时若已有预算（含历史手输的零头金额），未触碰滑杆前原值原样保留。
 */
private val BUDGET_TIERS: List<Long> = buildList {
    for (exp in 2..4) {
        for (mult in listOf(1.0, 1.5, 2.0, 3.0, 4.0, 5.0, 6.0, 8.0)) {
            add((mult * 100.0 * 10.0.pow(exp - 2)).roundToLong())
        }
    }
    add(100_000L)
}

private val BUDGET_YUAN_FORMAT = DecimalFormat("#,##0")

private fun tierIndexForBudget(cents: Long): Int {
    if (cents <= 0L) return BUDGET_TIERS.indexOf(5_000L).coerceAtLeast(0) // 未设置时锚定常用档
    val yuan = cents / 100
    return BUDGET_TIERS.indices.minBy { abs(BUDGET_TIERS[it] - yuan) }
}

@Composable
fun BudgetSliderDialog(
    title: String,
    subtitle: String,
    initialBudgetCents: Long,
    onSave: (Long) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    var draftCents by remember { mutableStateOf(initialBudgetCents.coerceAtLeast(0L)) }
    var sliderIndex by remember { mutableStateOf(tierIndexForBudget(initialBudgetCents).toFloat()) }

    val amountText = if (draftCents == 0L) {
        "未设置"
    } else if (draftCents % 100 != 0L) {
        // 历史手输的零头金额：未触碰滑杆前原样展示（保留分的精度）
        "¥${MoneyUtils.centsToYuanString(draftCents, withGrouping = true)}"
    } else {
        "¥${BUDGET_YUAN_FORMAT.format(draftCents / 100)}"
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )

                // 拖动回显大金额（拖动中实时变化）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = amountText,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 34.sp
                        ),
                        textAlign = TextAlign.Center,
                        color = if (draftCents == 0L) {
                            MaterialTheme.colorScheme.outline
                        } else {
                            primary
                        }
                    )
                }

                // 🌟 拖动滑杆选档：档位对数分布，触感与「选一个整额目标」的心智一致
                Slider(
                    value = sliderIndex,
                    onValueChange = { position ->
                        val index = position.roundToInt()
                            .coerceIn(0, BUDGET_TIERS.lastIndex)
                        sliderIndex = index.toFloat() // 吸附到档位，thumb 与回显金额始终一致
                        draftCents = BUDGET_TIERS[index] * 100
                    },
                    valueRange = 0f..(BUDGET_TIERS.size - 1).toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = primary,
                        activeTrackColor = primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "¥${BUDGET_YUAN_FORMAT.format(BUDGET_TIERS.first())}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "¥${BUDGET_YUAN_FORMAT.format(BUDGET_TIERS.last())}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onClear) {
                        Text("清除预算", color = MaterialTheme.colorScheme.error)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(onClick = { onSave(draftCents) }) {
                        Text("保存")
                    }
                }
            }
        }
    }
}
