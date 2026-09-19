package com.yuanman.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.yuanman.app.utils.MoneyUtils
import java.text.DecimalFormat
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 🌟 月度预算弹窗（首页看板 / 设置页共用）
 *
 * 两种选额方式并存：
 *  - 拖滑杆：档位按数量级呈对数分布（100~100,000 元，低金额区档位密、高金额区档位疏），
 *    拖动过程大字号实时回显，适合「选一个整额目标」的心智；
 *  - 点金额直接输入：需要精确零头（如 6,666.66）时点金额区，原地换成输入框手打。
 * 打开时若已有预算（含历史手输的零头金额），未触碰滑杆与输入前原值原样保留。
 */
private val BUDGET_TIERS: List<Long> = listOf(
    0L,
    500L, 1_000L, 1_500L, 2_000L, 2_500L, 3_000L, 4_000L, 5_000L, 6_000L, 7_000L, 8_000L, 10_000L,
    12_000L, 15_000L, 20_000L, 25_000L, 30_000L, 40_000L, 50_000L, 60_000L, 80_000L, 100_000L
)

private val BUDGET_YUAN_FORMAT = DecimalFormat("#,##0")

private fun tierIndexForBudget(cents: Long): Int {
    if (cents <= 0L) return 0 // 未设置时停靠在最左端（0 档位）
    val yuan = cents / 100
    val validIndices = 1..BUDGET_TIERS.lastIndex
    return validIndices.minByOrNull { abs(BUDGET_TIERS[it] - yuan) } ?: 0
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

    var isEditing by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    fun finishEditing() {
        isEditing = false
        keyboard?.hide()
        focusManager.clearFocus()
    }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    val amountText = if (draftCents == 0L) {
        "未设置"
    } else if (draftCents % 100 != 0L) {
        // 手输的零头金额：保留分的精度
        "¥${MoneyUtils.centsToYuanString(draftCents, withGrouping = true)}"
    } else {
        "¥${BUDGET_YUAN_FORMAT.format(draftCents / 100)}"
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
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

                // 金额区：默认是「点一下就能改」的大字回显，点开后原地换成输入框
                if (isEditing) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { raw ->
                            val cleaned = MoneyUtils.sanitizeYuanInput(raw)
                            inputText = cleaned
                            draftCents = MoneyUtils.yuanInputToCents(cleaned)
                            // 手输期间让滑杆 thumb 跟到最近的档位，避免两处数字打架
                            sliderIndex = tierIndexForBudget(draftCents).toFloat()
                        },
                        label = { Text("预算金额 (元)") },
                        placeholder = { Text("例如 6666.66") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { finishEditing() }),
                        trailingIcon = {
                            IconButton(onClick = { finishEditing() }) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "完成输入",
                                    tint = primary
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                } else {
                    Surface(
                        onClick = {
                            inputText = if (draftCents == 0L) "" else MoneyUtils.centsToPlainYuan(draftCents)
                            isEditing = true
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
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
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "自定义金额",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // 🌟 拖动滑杆选档：档位对数分布，触感与「选一个整额目标」的心智一致
                Slider(
                    value = sliderIndex,
                    onValueChange = { position ->
                        val index = position.roundToInt()
                            .coerceIn(0, BUDGET_TIERS.lastIndex)
                        sliderIndex = index.toFloat() // 吸附到档位，thumb 与回显金额始终一致
                        draftCents = BUDGET_TIERS[index] * 100
                        if (isEditing) {
                            // 拖滑杆即放弃手输：同步输入框文本，避免收起后数字对不上
                            inputText = if (draftCents == 0L) "" else MoneyUtils.centsToPlainYuan(draftCents)
                        }
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
                        text = "未设置",
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
