package com.yuanman.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yuanman.app.data.local.DatabaseBackupManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 首次安装启动时的历史账本自动检测与恢复引导弹窗
 */
@Composable
fun FirstLaunchRestoreDialog(
    visible: Boolean,
    needsPermission: Boolean,
    historicalBackupInfo: DatabaseBackupManager.HistoricalBackupInfo?,
    isRestoring: Boolean,
    onRequestPermission: () -> Unit,
    onConfirmRestore: () -> Unit,
    onDismissFreshStart: () -> Unit
) {
    if (!visible) return

    val title = if (needsPermission) {
        "恢复历史账本"
    } else {
        "发现本地历史账本"
    }

    val icon = if (needsPermission) Icons.Outlined.History else Icons.Outlined.SettingsBackupRestore

    AlertDialog(
        onDismissRequest = {
            if (!isRestoring) {
                onDismissFreshStart()
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (needsPermission) {
                    Text(
                        text = "如果您之前在手机上使用过「沅满」并卸载重装，系统可能已将历史账单与账户安全备份在手机「文档/Yuanman」目录中。\n\n" +
                            "是否授权检索并恢复历史数据？",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (historicalBackupInfo != null) {
                    val dateText = if (historicalBackupInfo.lastModified > 0L) {
                        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(historicalBackupInfo.lastModified))
                    } else {
                        "最近备份"
                    }

                    Text(
                        text = "检测到手机本地保存有历史账本快照（$dateText）：",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (historicalBackupInfo.recordCount > 0) {
                            Text(
                                text = "• 账单流水：${historicalBackupInfo.recordCount} 笔",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (historicalBackupInfo.accountCount > 0) {
                            Text(
                                text = "• 账户与攒钱计划：${historicalBackupInfo.accountCount} 个",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "• 分类体系、月度预算与个性化偏好",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "是否立即还原历史账本？",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (needsPermission) {
                        onRequestPermission()
                    } else {
                        onConfirmRestore()
                    }
                },
                enabled = !isRestoring
            ) {
                if (isRestoring) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("正在恢复…")
                } else {
                    Text(if (needsPermission) "检索并恢复" else "立即恢复")
                }
            }
        },
        dismissButton = {
            if (!isRestoring) {
                TextButton(onClick = onDismissFreshStart) {
                    Text("全新开始")
                }
            }
        }
    )
}
