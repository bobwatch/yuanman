package com.yuanman.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yuanman.app.utils.UpdateInfo
import com.yuanman.app.utils.UpdateState
import java.io.File

/**
 * 🌟 全局应用版本更新弹窗组件
 *
 * 支持：
 * - 发现新版本：展示版本号、发布标题、更新日志、安装包大小，提供「下载更新」与「稍后」
 * - 更新包就绪：提供「立即安装」与「稍后」
 * - 取消/稍后：触发推迟 1 天提醒
 */
@Composable
fun AppUpdateDialog(
    visible: Boolean,
    updateState: UpdateState,
    onDownload: (UpdateInfo) -> Unit,
    onInstall: (File) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val info = when (updateState) {
        is UpdateState.Available -> updateState.info
        is UpdateState.ReadyToInstall -> updateState.info
        else -> null
    } ?: return

    val readyApk = (updateState as? UpdateState.ReadyToInstall)?.apkFile

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (readyApk != null) {
                        "更新已就绪 v${info.versionName}"
                    } else {
                        "发现新版本 v${info.versionName}"
                    },
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (info.releaseTitle.isNotBlank()) {
                    Text(
                        text = info.releaseTitle,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (info.releaseNotes.isNotBlank()) {
                    Text(
                        text = info.releaseNotes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "本次更新包含体验优化与问题修复。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                if (info.sizeBytes > 0L) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "安装包大小: ${"%.1f".format(info.sizeBytes / 1024.0 / 1024.0)} MB",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (readyApk != null) {
                        onInstall(readyApk)
                    } else {
                        onDownload(info)
                    }
                }
            ) {
                Text(if (readyApk != null) "立即安装" else "下载更新")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("稍后")
            }
        }
    )
}
