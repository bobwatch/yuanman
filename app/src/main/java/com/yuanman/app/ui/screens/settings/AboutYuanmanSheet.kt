package com.yuanman.app.ui.screens.settings

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.yuanman.app.R
import com.yuanman.app.ui.components.YuanmanModalBottomSheet
import com.yuanman.app.utils.UpdateManager
import com.yuanman.app.utils.UpdateState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutYuanmanSheet(
    updateManager: UpdateManager,
    onDismiss: () -> Unit,
    onInstallApk: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val updateState by updateManager.updateState.collectAsState()
    val currentVersion = updateManager.currentVersionName
    val configuration = LocalConfiguration.current
    val sheetMaxHeight = (configuration.screenHeightDp * 0.72f).dp

    val context = LocalContext.current
    val density = LocalDensity.current
    val appIconBitmap = remember(context, density) {
        try {
            val pm = context.packageManager
            val drawable = context.applicationInfo.loadIcon(pm)
                ?: ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
            val sizePx = with(density) { 68.dp.roundToPx() }
            drawable?.toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)?.asImageBitmap()
        } catch (e: Throwable) {
            null
        }
    }

    // 打开 Sheet 时静默获取一次是否需要更新
    LaunchedEffect(Unit) {
        updateManager.checkForUpdates(isManual = false)
    }

    YuanmanModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = sheetMaxHeight)
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 1. App 标志与版本名称：获取系统真实桌面图标（自适应蒙版与比例缩放），与手机桌面完全一致
            if (appIconBitmap != null) {
                Image(
                    bitmap = appIconBitmap,
                    contentDescription = "沅满记账图标",
                    modifier = Modifier.size(68.dp)
                )
            } else {
                // 降级容错：自适应图标安全区规范为 72x72（总画布 108x108），需放大 1.5 倍裁切出血边
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(18.dp))
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_background),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(scaleX = 1.5f, scaleY = 1.5f)
                    )
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = "沅满记账图标",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(scaleX = 1.5f, scaleY = 1.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "沅满记账",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(2.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Text(
                    text = "v$currentVersion · 井井有条",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 2. 数据与隐私安全承诺卡片
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "数据隐私与安全承诺",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    PrivacyPointItem(
                        icon = Icons.Default.Storage,
                        title = "100% 纯本地离线存储",
                        description = "账单、账户与预算仅存于本机 SQLite，不上传云端。"
                    )

                    PrivacyPointItem(
                        icon = Icons.Outlined.Lock,
                        title = "零跟踪、零商业广告",
                        description = "不采集设备指纹，不接入广告或商业统计 SDK。"
                    )

                    PrivacyPointItem(
                        icon = Icons.Default.SyncLock,
                        title = "自主可控的离线备份与局域网同步",
                        description = "支持带校验的离线备份，双机同步仅在局域网内进行。"
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. 版本更新：固定为单行紧凑状态，避免检查结果变化时 Sheet 高度跳动
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)),
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdateAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "版本更新",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1
                        )
                        Text(
                            text = updateStatusText(updateState, currentVersion),
                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }

                    when (val state = updateState) {
                        is UpdateState.Checking -> CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        is UpdateState.Downloading -> LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier
                                .width(48.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                        )
                        is UpdateState.Available -> TextButton(
                            onClick = { updateManager.startDownload(state.info) },
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) { Text("下载") }
                        is UpdateState.ReadyToInstall -> TextButton(
                            onClick = { onInstallApk(state.apkFile) },
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) { Text("安装") }
                        else -> TextButton(
                            onClick = { updateManager.checkForUpdates(isManual = true) },
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text(if (updateState is UpdateState.Error) "重试" else "检查")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun PrivacyPointItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            )
        }
    }
}

private fun updateStatusText(state: UpdateState, currentVersion: String): String = when (state) {
    UpdateState.Idle -> "当前 v$currentVersion · 点击检查"
    UpdateState.Checking -> "正在检查更新"
    is UpdateState.Available -> "发现新版本 v${state.info.versionName}"
    is UpdateState.Downloading -> "下载中 ${(state.progress * 100).toInt()}%"
    is UpdateState.ReadyToInstall -> "v${state.info.versionName} 已下载"
    UpdateState.UpToDate -> "当前 v$currentVersion · 已是最新"
    is UpdateState.Error -> "检查失败 · 点击重试"
}
