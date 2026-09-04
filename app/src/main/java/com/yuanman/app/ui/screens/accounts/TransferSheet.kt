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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuanman.app.data.local.entity.AccountEntity
import com.yuanman.app.data.model.AccountIconHelper
import com.yuanman.app.data.model.AccountTransferCalculator
import com.yuanman.app.data.model.AccountTransferError
import com.yuanman.app.data.model.AccountType
import com.yuanman.app.ui.components.YuanmanModalBottomSheet
import com.yuanman.app.utils.MoneyUtils
import java.math.BigDecimal
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferSheet(
    accounts: List<AccountEntity>,
    initialFromAccountId: Long? = null,
    initialToAccountId: Long? = null,
    isLoading: Boolean = false,
    privacyMode: Boolean = false,
    onDismiss: () -> Unit,
    onConfirmTransfer: (fromId: Long, toId: Long, amountCents: Long, remark: String) -> Unit
) {
    if (accounts.size < 2) {
        YuanmanModalBottomSheet(
            onDismissRequest = onDismiss
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(text = "账户不足", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text(text = "资金划转至少需要 2 个账户，请先创建更多账户。")
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("知道了")
                }
            }
        }
        return
    }

    var fromAccountId by remember {
        mutableStateOf(
            initialFromAccountId ?: accounts.firstOrNull { it.id != initialToAccountId }?.id ?: accounts.firstOrNull()?.id ?: 0L
        )
    }
    var toAccountId by remember {
        mutableStateOf(
            initialToAccountId ?: accounts.firstOrNull { it.id != fromAccountId }?.id ?: accounts.getOrNull(1)?.id ?: 0L
        )
    }
    var amountStr by remember { mutableStateOf("") }
    var remark by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var isSelectingFromAccount by remember { mutableStateOf(false) }
    var isSelectingToAccount by remember { mutableStateOf(false) }

    val fromAccount = accounts.firstOrNull { it.id == fromAccountId } ?: accounts[0]
    val toAccount = accounts.firstOrNull { it.id == toAccountId } ?: accounts[1]

    val inputAmountCents = try {
        if (amountStr.isBlank()) 0L else MoneyUtils.parseYuanToCents(amountStr)
    } catch (e: Exception) {
        0L
    }

    val transferPreview = AccountTransferCalculator.preview(fromAccount, toAccount, inputAmountCents)
    val previewErrorMessage = when (transferPreview.error) {
        AccountTransferError.INSUFFICIENT_FUNDS -> "转出账户余额不足，不能透支转账"
        AccountTransferError.EXCEEDS_CREDIT_BALANCE -> "还款金额不能超过当前待还欠款"
        else -> null
    }

    YuanmanModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 顶栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "资金划转",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "关闭", modifier = Modifier.size(20.dp))
                }
            }

            // 1. 左右双卡槽互转选择器：转出（左）→ 转入（右）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TransferAccountSlot(
                    roleLabel = "转出账户",
                    account = fromAccount,
                    afterBalanceCents = transferPreview.fromAfterBalanceCents,
                    privacyMode = privacyMode,
                    onClick = { isSelectingFromAccount = true },
                    modifier = Modifier.weight(1f)
                )

                // 中间调换按钮
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .size(40.dp)
                        .clickable {
                            val temp = fromAccountId
                            fromAccountId = toAccountId
                            toAccountId = temp
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = "调换转出转入",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                TransferAccountSlot(
                    roleLabel = "转入账户",
                    account = toAccount,
                    afterBalanceCents = transferPreview.toAfterBalanceCents,
                    privacyMode = privacyMode,
                    onClick = { isSelectingToAccount = true },
                    modifier = Modifier.weight(1f)
                )
            }

            // 2. 转账金额输入框
            OutlinedTextField(
                value = amountStr,
                onValueChange = {
                    amountStr = it
                    errorMessage = null
                },
                label = { Text("转账金额") },
                prefix = { Text("¥ ", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                shape = RoundedCornerShape(14.dp),
                textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.fillMaxWidth()
            )

            // 3. 快速金额小胶囊
            val isToLiability = AccountType.fromString(toAccount.type).isLiability
            val isFromLiability = AccountType.fromString(fromAccount.type).isLiability

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isToLiability && toAccount.balanceCents > 0L) {
                    item {
                        val maxRepay = if (!isFromLiability && fromAccount.balanceCents > 0L) {
                            min(fromAccount.balanceCents, toAccount.balanceCents)
                        } else {
                            toAccount.balanceCents
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                            modifier = Modifier.clickable {
                                amountStr = MoneyUtils.centsToYuanString(maxRepay)
                            }
                        ) {
                            Text(
                                text = if (privacyMode) "结清欠款（¥****）" else "结清欠款（¥${MoneyUtils.centsToYuanString(toAccount.balanceCents)}）",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                val presets = listOf("100", "500", "1000", "5000")
                items(presets) { preset ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.clickable { amountStr = preset }
                    ) {
                        Text(
                            text = "¥$preset",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                if (!isFromLiability && fromAccount.balanceCents > 0L) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            modifier = Modifier.clickable {
                                amountStr = MoneyUtils.centsToYuanString(fromAccount.balanceCents)
                            }
                        ) {
                            Text(
                                text = "全部可用",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // 4. 转账备注
            OutlinedTextField(
                value = remark,
                onValueChange = { remark = it },
                label = { Text("转账备注（选填，如：还信用卡、零钱充值）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            } else if (inputAmountCents > 0L && previewErrorMessage != null) {
                Text(
                    text = previewErrorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 5. 确认按钮
            Button(
                onClick = {
                    if (fromAccountId == toAccountId) {
                        errorMessage = "转出账户和转入账户不能相同"
                        return@Button
                    }
                    if (inputAmountCents <= 0L) {
                        errorMessage = "请输入大于 0 的转账金额"
                        return@Button
                    }
                    if (!transferPreview.isValid) {
                        errorMessage = previewErrorMessage ?: "当前账户状态不支持这笔转账"
                        return@Button
                    }
                    onConfirmTransfer(fromAccountId, toAccountId, inputAmountCents, remark.trim())
                },
                enabled = inputAmountCents > 0L && transferPreview.isValid && !isLoading,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("转账中…", fontWeight = FontWeight.Bold)
                } else {
                    Text(
                        text = if (privacyMode) {
                            "确认转账 ¥****"
                        } else {
                            "确认转账 ¥${if (amountStr.isNotBlank()) amountStr else "0.00"}"
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // 转出账户选择 Dialog
    if (isSelectingFromAccount) {
        AccountSelectDialog(
            title = "选择转出账户",
            accounts = accounts,
            selectedId = fromAccountId,
            disabledId = toAccountId,
            privacyMode = privacyMode,
            onDismiss = { isSelectingFromAccount = false },
            onSelect = {
                fromAccountId = it
                isSelectingFromAccount = false
            }
        )
    }

    // 转入账户选择 Dialog
    if (isSelectingToAccount) {
        AccountSelectDialog(
            title = "选择转入账户",
            accounts = accounts,
            selectedId = toAccountId,
            disabledId = fromAccountId,
            privacyMode = privacyMode,
            onDismiss = { isSelectingToAccount = false },
            onSelect = {
                toAccountId = it
                isSelectingToAccount = false
            }
        )
    }
}

@Composable
private fun TransferAccountSlot(
    roleLabel: String,
    account: AccountEntity,
    afterBalanceCents: Long,
    privacyMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accColor = try {
        Color(android.graphics.Color.parseColor(account.colorHex.ifBlank { "#1B5E20" }))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }
    val stateLabel = if (AccountType.fromString(account.type).isLiability) "欠款" else "余额"

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = roleLabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.outline
                )
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AccountIconHelper.getIcon(account.icon),
                    contentDescription = null,
                    tint = accColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = account.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (privacyMode) "当前$stateLabel ¥****" else "当前$stateLabel ¥${MoneyUtils.centsToYuanString(account.balanceCents)}",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (privacyMode) "转后$stateLabel ¥****" else "转后$stateLabel ¥${MoneyUtils.centsToYuanString(afterBalanceCents)}",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.outline
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AccountSelectDialog(
    title: String,
    accounts: List<AccountEntity>,
    selectedId: Long,
    disabledId: Long,
    privacyMode: Boolean,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                accounts.forEach { acc ->
                    val isSelected = acc.id == selectedId
                    val isDisabled = acc.id == disabledId
                    val accColor = try {
                        Color(android.graphics.Color.parseColor(acc.colorHex.ifBlank { "#1B5E20" }))
                    } catch (e: Exception) {
                        MaterialTheme.colorScheme.primary
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        },
                        border = BorderStroke(
                            0.6.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = !isDisabled) { onSelect(acc.id) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(accColor.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = AccountIconHelper.getIcon(acc.icon),
                                        contentDescription = null,
                                        tint = accColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = acc.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isDisabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                    if (isDisabled) {
                                        Text(
                                            text = "已选为对方账户",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        )
                                    }
                                }
                            }

                            Text(
                                text = if (privacyMode) "****" else "¥${MoneyUtils.centsToYuanString(acc.balanceCents)}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDisabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
