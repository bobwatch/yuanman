package com.yuanman.app.ui.screens.accounts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuanman.app.data.local.entity.AccountEntity
import com.yuanman.app.data.model.AccountIconHelper
import com.yuanman.app.data.model.AccountType
import com.yuanman.app.ui.components.YuanmanModalBottomSheet
import com.yuanman.app.utils.MoneyUtils
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditAccountSheet(
    account: AccountEntity? = null,
    isLoading: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        type: AccountType,
        balanceCents: Long,
        includeInNetWorth: Boolean,
        icon: String,
        colorHex: String,
        remark: String,
        balanceAdjustmentRemark: String
    ) -> Unit
) {
    val isEdit = account != null

    var name by remember { mutableStateOf(account?.name ?: "") }
    var selectedType by remember {
        mutableStateOf(account?.let { AccountType.fromString(it.type) } ?: AccountType.CHECKING)
    }
    var balanceYuanStr by remember {
        mutableStateOf(account?.let { MoneyUtils.centsToYuanString(it.balanceCents) } ?: "")
    }
    var includeInNetWorth by remember { mutableStateOf(account?.includeInNetWorth ?: true) }
    var selectedIcon by remember { mutableStateOf(account?.icon ?: "account_balance_wallet") }
    var selectedColor by remember { mutableStateOf(account?.colorHex ?: AccountIconHelper.defaultColorHexes[0]) }
    var remark by remember { mutableStateOf(account?.remark ?: "") }
    var balanceAdjustmentRemark by remember { mutableStateOf("") }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    YuanmanModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 顶栏标题
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEdit) "编辑账户「${account?.name}」" else "新增账户",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "关闭", modifier = Modifier.size(20.dp))
                }
            }

            // 1. 账户类别分段选择
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "账户类别",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AccountType.values().forEach { type ->
                        val isSelected = type == selectedType
                        Surface(
                            shape = RoundedCornerShape(9.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedType = type }
                        ) {
                            Text(
                                text = type.groupTitle,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                ),
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }

            // 2. 账户名称
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    errorMessage = null
                },
                label = { Text("账户名称（如：招商银行卡、微信零钱）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // 3. 当前余额（负债账户金额为负数表示溢缴/多还）
            OutlinedTextField(
                value = balanceYuanStr,
                onValueChange = {
                    balanceYuanStr = it
                    errorMessage = null
                },
                label = {
                    Text(
                        if (selectedType.isLiability) "当前待还/欠款金额（元）" else "当前账户余额（元）"
                    )
                },
                prefix = { Text("¥ ", fontWeight = FontWeight.Bold) },
                trailingIcon = {
                    if (selectedType.isLiability) {
                        TextButton(
                            onClick = {
                                balanceYuanStr = toggleBalanceSign(balanceYuanStr)
                                errorMessage = null
                            },
                            enabled = balanceYuanStr.isNotBlank(),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text(
                                text = if (balanceYuanStr.trimStart().startsWith("-")) "转待还" else "转溢缴",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            if (isEdit) {
                OutlinedTextField(
                    value = balanceAdjustmentRemark,
                    onValueChange = { balanceAdjustmentRemark = it },
                    label = { Text("余额调整说明（选填）") },
                    supportingText = { Text("修改余额会生成可撤销的调整账单") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 4. 颜色与图标选择
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "标识色与图标",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                // 颜色选择条
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AccountIconHelper.defaultColorHexes.forEach { hex ->
                        val color = Color(android.graphics.Color.parseColor(hex))
                        val isSelected = hex.equals(selectedColor, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { selectedColor = hex },
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

                Spacer(modifier = Modifier.height(4.dp))

                // 图标选择条
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(AccountIconHelper.availableIcons) { (iconKey, vector) ->
                        val isSelected = iconKey == selectedIcon
                        val activeColor = Color(android.graphics.Color.parseColor(selectedColor))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) activeColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(if (isSelected) 1.5.dp else 0.5.dp, if (isSelected) activeColor else Color.Transparent),
                            modifier = Modifier
                                .size(44.dp)
                                .clickable { selectedIcon = iconKey }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = vector,
                                    contentDescription = null,
                                    tint = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 5. 备注 / 尾号说明
            OutlinedTextField(
                value = remark,
                onValueChange = { remark = it },
                label = { Text("卡号尾号或备注（选填，如：尾号8899）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // 6. 是否计入净资产
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .clickable { includeInNetWorth = !includeInNetWorth }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "计入总净资产核算",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "关闭后该账户余额不参与总净资产与增长对比",
                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline)
                    )
                }
                Switch(
                    checked = includeInNetWorth,
                    onCheckedChange = { includeInNetWorth = it }
                )
            }

            // 错误提示
            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 底部操作按钮
            Button(
                onClick = {
                    if (name.isBlank()) {
                        errorMessage = "请输入账户名称"
                        return@Button
                    }
                    val balanceInputValid = if (selectedType.isLiability) {
                        MoneyUtils.isValidSignedAmountInput(balanceYuanStr)
                    } else {
                        MoneyUtils.isValidNonNegativeAmountInput(balanceYuanStr)
                    }
                    if (balanceYuanStr.isNotBlank() && !balanceInputValid) {
                        errorMessage = if (selectedType.isLiability) {
                            "请输入有效的金额（最多两位小数，负数表示溢缴）"
                        } else {
                            "请输入有效的非负金额（最多两位小数）"
                        }
                        return@Button
                    }
                    val balanceCents = try {
                        if (balanceYuanStr.isBlank()) {
                            0L
                        } else if (selectedType.isLiability) {
                            MoneyUtils.parseSignedYuanToCents(balanceYuanStr)
                        } else {
                            MoneyUtils.parseYuanToCents(balanceYuanStr)
                        }
                    } catch (e: Exception) {
                        errorMessage = "请输入有效的金额数字"
                        return@Button
                    }
                    onSave(
                        name.trim(),
                        selectedType,
                        balanceCents,
                        includeInNetWorth,
                        selectedIcon,
                        selectedColor,
                        remark.trim(),
                        balanceAdjustmentRemark.trim()
                    )
                },
                enabled = !isLoading,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("保存中…", fontWeight = FontWeight.Bold)
                } else {
                    Text(
                        text = if (isEdit) "保存修改" else "创建",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 切换金额文本的正负号（负债账户余额为负表示溢缴/多还）。
 * 空串或非数字开头的非法内容保持原样，避免产生无法解析的输入。
 */
private fun toggleBalanceSign(raw: String): String {
    val trimmed = raw.trim()
    val canFlip = trimmed.isNotEmpty() && (trimmed.startsWith("-") || trimmed[0].isDigit())
    if (!canFlip) return raw
    return if (trimmed.startsWith("-")) trimmed.removePrefix("-") else "-$trimmed"
}
