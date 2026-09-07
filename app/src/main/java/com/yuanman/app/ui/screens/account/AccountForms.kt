package com.yuanman.app.ui.screens.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuanman.app.data.model.CategoryIconHelper
import com.yuanman.app.ui.components.CategoryIconView
import com.yuanman.app.ui.components.YuanmanModalBottomSheet
import com.yuanman.app.utils.MoneyUtils
import java.math.BigDecimal

/**
 * 新增 / 编辑账户底包 (AddEditAccountSheet)
 *
 * 类型选择遵循「类型完全自定义」规则（§4.2.0）：
 * 自由文本输入 + 下方"已用类型"快捷 chips（数据源为全账户既有类型，
 * 代码不内置任何类型名，无预设语义）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditAccountSheet(
    accountToEdit: AccountUiModel?,
    existingTypes: List<String>,
    onDismiss: () -> Unit,
    onSave: (name: String, label: String, iconName: String, colorHex: Long, openingBalanceCents: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf(accountToEdit?.name ?: "") }
    var label by remember { mutableStateOf(accountToEdit?.label ?: "") }
    var iconName by remember { mutableStateOf(accountToEdit?.iconName ?: "wallet") }
    var colorHex by remember { mutableStateOf(accountToEdit?.colorHex ?: 0xFF059669L) }
    var openingYuan by remember {
        mutableStateOf(
            if (accountToEdit != null) {
                val bd = BigDecimal(accountToEdit.openingBalanceCents).divide(BigDecimal(100))
                bd.stripTrailingZeros().toPlainString()
            } else "0"
        )
    }

    // 主题色盘：仅作视觉挑选，与类型无关
    val presetColors = listOf(
        0xFF059669L, // 翡翠翠绿
        0xFF0284C7L, // 晴空蔚蓝
        0xFFE53935L, // 鲜明赤红
        0xFFFF9800L, // 活力暖橙
        0xFF9C27B0L, // 优雅紫
        0xFFE91E63L, // 珊瑚粉
        0xFF009688L, // 墨玉青
        0xFF3F51B5L, // 靛青蓝
        0xFF795548L, // 暖棕
        0xFF607D8BL, // 极简灰
        0xFFFFB300L, // 晨曦金
        0xFF26A69AL  // 薄荷绿
    )

    // 账户类别图标词表（沿用旧词表，图标与类型语义解耦）
    val availableIcons = listOf(
        "wallet", "part_time", "bank", "bonus", "savings", "salary",
        "finance", "refund", "card_gift", "shopping", "digital", "housing",
        "traffic", "other"
    )

    YuanmanModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = if (accountToEdit == null) "新建账户" else "编辑账户",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            // 账户名称
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("账户名称 (如: 微信零钱、招行储蓄卡)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // 账户类型：自由文本 + 已用类型快捷 chips（可留空，无内置类型清单）
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "账户类型（可自定义，可留空）",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    placeholder = { Text("例如：日常、储蓄、信用") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // 已用类型快捷区：点击即填入，选中态与输入框内容联动（忽略大小写与首尾空白）
                if (existingTypes.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        existingTypes.forEach { type ->
                            val isSelected = label.trim().equals(type.trim(), ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = { label = type.trim() },
                                label = { Text(type) },
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }
            }

            // 期初余额
            OutlinedTextField(
                value = openingYuan,
                onValueChange = { openingYuan = it },
                label = { Text("期初余额 (元，信用卡欠款可输负数如 -1500)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // 图标选择
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "账户图标",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    availableIcons.forEach { iconKey ->
                        val isSelected = iconName == iconKey
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { iconName = iconKey },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = CategoryIconHelper.getIcon(iconKey),
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            // 颜色选择
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "主题色彩",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    presetColors.forEach { colorVal ->
                        val isSelected = colorHex == colorVal
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(colorVal))
                                .clickable { colorHex = colorVal },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val cents = try {
                            val bd = BigDecimal(openingYuan.trim())
                            bd.multiply(BigDecimal(100)).toLong()
                        } catch (e: Exception) {
                            0L
                        }
                        onSave(name.trim(), label.trim(), iconName, colorHex, cents)
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("保存账户", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/**
 * 快速转账 / 信用卡还款底包 (QuickTransferSheet)
 *
 * 原样复用：账户 FilterChip 双向选择（自避免同账户互斥）、金额 / 备注输入、
 * secondary 确认按钮。负余额账户进入时语义即"还款"，由余额符号驱动。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickTransferSheet(
    accounts: List<AccountUiModel>,
    initialFromAccountId: Long? = null,
    initialToAccountId: Long? = null,
    onDismiss: () -> Unit,
    onConfirmTransfer: (fromId: Long, toId: Long, amountCents: Long, remark: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var fromAccountId by remember {
        mutableStateOf(
            initialFromAccountId ?: accounts.firstOrNull { it.id != initialToAccountId && it.balanceCents > 0 }?.id ?: accounts.firstOrNull()?.id ?: 0L
        )
    }
    var toAccountId by remember {
        mutableStateOf(
            initialToAccountId ?: accounts.firstOrNull { it.id != fromAccountId }?.id ?: 0L
        )
    }
    var amountYuan by remember { mutableStateOf("") }
    var remark by remember { mutableStateOf("") }

    YuanmanModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "资金转账 / 信用卡还款",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            // 转出账户选择
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "转出账户 (付款方)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    accounts.forEach { acc ->
                        FilterChip(
                            selected = acc.id == fromAccountId,
                            onClick = {
                                fromAccountId = acc.id
                                if (toAccountId == acc.id) {
                                    toAccountId = accounts.firstOrNull { it.id != acc.id }?.id ?: 0L
                                }
                            },
                            label = { Text("${acc.name} (${MoneyUtils.centsToYuanString(acc.balanceCents)})") },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }

            // 方向指示箭头
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowDownward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // 转入账户选择
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "转入账户 (收款方/还款卡)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    accounts.filter { it.id != fromAccountId }.forEach { acc ->
                        FilterChip(
                            selected = acc.id == toAccountId,
                            onClick = { toAccountId = acc.id },
                            label = { Text("${acc.name} (${MoneyUtils.centsToYuanString(acc.balanceCents)})") },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }

            // 转账金额
            OutlinedTextField(
                value = amountYuan,
                onValueChange = { amountYuan = it },
                label = { Text("转账金额 (元)") },
                placeholder = { Text("0.00") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // 备注
            OutlinedTextField(
                value = remark,
                onValueChange = { remark = it },
                label = { Text("备注说明 (可选，如: 还清本期花呗账单)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = {
                    val cents = MoneyUtils.parseYuanToCents(amountYuan)
                    if (cents > 0L && fromAccountId > 0L && toAccountId > 0L && fromAccountId != toAccountId) {
                        onConfirmTransfer(fromAccountId, toAccountId, cents, remark.trim())
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("确认转账", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
