package com.moneyhistory.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyhistory.app.MainViewModel
import com.moneyhistory.app.MessageVariant
import com.moneyhistory.app.R
import com.moneyhistory.app.ui.theme.incomeAmountColor

/** 家庭同步：配对码管理 + 设备列表 + 手动同步。 */
@Composable
fun FamilySyncScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val sync = viewModel.syncManager
    val devices by sync.devices.collectAsStateWithLifecycle()
    val status by sync.status.collectAsStateWithLifecycle()
    val myCode by sync.pairingCode.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current

    var inputCode by remember { mutableStateOf("") }
    // 配对码操作结果：text + 是否错误（失败用错误红而非品牌蓝）
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    // 重新生成配对码不可逆（全部已配对设备失效），先确认
    var confirmRegenerate by remember { mutableStateOf(false) }

    val regeneratedText = stringResource(R.string.family_regenerated)
    val joinedText = stringResource(R.string.family_joined)
    val joinInvalidText = stringResource(R.string.family_join_invalid)
    val copiedText = stringResource(R.string.family_copied)

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        SubPageHeader(
            title = stringResource(R.string.family_title),
            onBack = onBack
        )

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 页头已显示「家庭同步」，此处不再重复标题
            AppCard {
                Text(
                    text = stringResource(R.string.family_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }

            // 本机配对码：发给家人前先复制，免手抄
            SettingsCard(title = stringResource(R.string.family_code_title)) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = myCode,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 4.sp,
                        maxLines = 1
                    )
                    Row {
                        TextButton(onClick = {
                            val clipboard = context.getSystemService(
                                Context.CLIPBOARD_SERVICE
                            ) as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("pairingCode", myCode)
                            )
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            viewModel.postMessage(copiedText, MessageVariant.SUCCESS)
                        }) {
                            Text(stringResource(R.string.family_copy))
                        }
                        TextButton(onClick = { confirmRegenerate = true }) {
                            Text(stringResource(R.string.family_regenerate))
                        }
                    }
                }
            }

            // 加入家庭
            SettingsCard(title = stringResource(R.string.family_join_title)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = inputCode,
                        onValueChange = { input ->
                            inputCode = input.filter { it.isDigit() }.take(6)
                            // 输入变化后清掉上一条结果，避免旧提示误导
                            message = null
                        },
                        label = { Text(stringResource(R.string.family_join_hint)) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            message = if (sync.setPairingCode(inputCode)) {
                                joinedText to false
                            } else {
                                joinInvalidText to true
                            }
                        },
                        // 不满 6 位禁按：比点了再报错更先一步给出反馈
                        enabled = inputCode.length == 6
                    ) {
                        Text(stringResource(R.string.common_confirm))
                    }
                }
                message?.let { (text, isError) ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }

            // 同步状态与设备列表
            SettingsCard(title = stringResource(R.string.family_status_title)) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                // 同步进行中：按钮转圈禁用，避免重复触发；结束后恢复
                val syncing = status == stringResource(R.string.sync_status_syncing)
                Button(
                    onClick = { sync.syncNow() },
                    enabled = !syncing
                ) {
                    if (syncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.family_sync_now))
                }
                Spacer(Modifier.height(12.dp))
                if (devices.isEmpty()) {
                    Text(
                        text = stringResource(R.string.family_no_devices),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    devices.forEach { device ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.family_device,
                                    device.name.removePrefix("MH-")
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = stringResource(
                                    if (device.connected) {
                                        R.string.family_connected
                                    } else {
                                        R.string.family_discovered
                                    }
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (device.connected) {
                                    incomeAmountColor()
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmRegenerate) {
        AlertDialog(
            onDismissRequest = { confirmRegenerate = false },
            title = { Text(stringResource(R.string.family_regenerate_confirm_title)) },
            text = { Text(stringResource(R.string.family_regenerate_confirm_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRegenerate = false
                    sync.regeneratePairingCode()
                    // 操作发生在页面顶部配对码卡片，用全局 Toast 就近反馈，
                    // 不落到下方「加入家庭」卡片的输入结果位
                    viewModel.postMessage(regeneratedText, MessageVariant.SUCCESS)
                }) {
                    Text(
                        stringResource(R.string.common_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRegenerate = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}