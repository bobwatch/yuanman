package com.yuanman.app.ui.screens.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.yuanman.app.ui.components.CategoryIconView
import com.yuanman.app.ui.theme.ExpenseColorLight
import com.yuanman.app.utils.MoneyUtils
import java.math.BigDecimal

/**
 * 资金对账对话框（AccountReconcileDialog）—— 从旧 AccountComponents.kt 原样迁移，行为与文案不变：
 * 账面余额卡 → 实际余额输入 → 差额实时比对提示（无差 / 多出 / 缺少），
 * 差额非零时可"按实际校正期初基线"，否则仅"确认对账"记录核对时间。
 * 校验语义：账面 = 期初 + 流水；校正 = 差额并入期初，不产生假流水。
 */
@Composable
fun AccountReconcileDialog(
    account: AccountUiModel,
    onDismiss: () -> Unit,
    onConfirmReconcile: (actualBalanceCents: Long, applyCorrection: Boolean) -> Unit
) {
    val bookBalanceCents = account.balanceCents
    var actualYuanInput by remember {
        mutableStateOf(
            BigDecimal(bookBalanceCents).divide(BigDecimal(100)).stripTrailingZeros().toPlainString()
        )
    }

    val actualCents = try {
        BigDecimal(actualYuanInput.trim()).multiply(BigDecimal(100)).toLong()
    } catch (e: Exception) {
        bookBalanceCents
    }

    val diffCents = actualCents - bookBalanceCents
    val primaryColor = MaterialTheme.colorScheme.primary

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    CategoryIconView(
                        iconName = account.iconName,
                        colorHex = account.colorHex,
                        size = 36.dp,
                        iconSize = 18.dp
                    )
                    Text(
                        text = "月度资金对账 · ${account.name}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Text(
                    text = "建议按月核对一次网银或对账单即可，保持轻松记账。若有微小利息、手续费或漏记，可一键自动校正期初基线。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 账面余额卡
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "当前账面余额",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = MoneyUtils.formatCurrency(bookBalanceCents),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                // 真实实际余额输入框
                OutlinedTextField(
                    value = actualYuanInput,
                    onValueChange = { actualYuanInput = it },
                    label = { Text("实际真实余额 (元)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // 差额比对提示框
                if (diffCents == 0L) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = primaryColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, null, tint = primaryColor, modifier = Modifier.size(16.dp))
                            Text(
                                text = "账面与实际完全一致，无需校正",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = primaryColor
                            )
                        }
                    }
                } else {
                    val diffStr = MoneyUtils.centsToYuanString(if (diffCents > 0L) diffCents else -diffCents)
                    val hint = if (diffCents > 0L) "实际比账面多出 ¥$diffStr" else "实际比账面缺少 ¥$diffStr"
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ExpenseColorLight.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, ExpenseColorLight.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(
                                text = "存在差额: $hint",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = ExpenseColorLight
                            )
                            Text(
                                text = "点击下方校正，差额将并入期初基线，保持后续流水自然累计。",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 操作按钮栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("取消")
                    }

                    if (diffCents != 0L) {
                        Button(
                            onClick = { onConfirmReconcile(actualCents, true) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                            modifier = Modifier.weight(1.4f)
                        ) {
                            Text("按实际校正期初", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = { onConfirmReconcile(actualCents, false) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("确认对账", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
