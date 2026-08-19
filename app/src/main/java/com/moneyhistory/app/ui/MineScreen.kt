package com.moneyhistory.app.ui

import android.content.Intent
import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyhistory.app.BuildConfig
import com.moneyhistory.app.MainViewModel
import com.moneyhistory.app.MessageVariant
import com.moneyhistory.app.MoneyUtils
import com.moneyhistory.app.R
import com.moneyhistory.app.ThemeMode
import com.moneyhistory.app.allBadges
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 我的 Tab：勋章墙 + 外观/记账/数据/关于全部设置项。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MineScreen(
    viewModel: MainViewModel,
    onNavigateToBudget: () -> Unit,
    onNavigateToBadges: () -> Unit,
    onNavigateToFamily: () -> Unit,
    onNavigateToRecurring: () -> Unit,
    onNavigateToCategories: () -> Unit
) {
    val context = LocalContext.current

    val themeMode by viewModel.settings.themeMode.collectAsStateWithLifecycle()
    val budgetCents by viewModel.settings.budgetCents.collectAsStateWithLifecycle()
    val badgeUnlocks by viewModel.settings.badgeUnlocks.collectAsStateWithLifecycle()

    var showImportChooser by remember { mutableStateOf(false) }
    var importMergeMode by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<String?>(null) }
    // 覆盖导入进行中：确认后弹加载态，大备份文件解析期间不卡 UI
    var importing by remember { mutableStateOf(false) }

    // 重复点击底部「我的」Tab：页面滚回顶部
    val scrollState = rememberScrollState()
    LaunchedEffect(Unit) {
        viewModel.tabReclick.collect { route ->
            if (route == "mine") scrollState.animateScrollTo(0)
        }
    }

    val readFailedText = stringResource(R.string.import_read_failed)
    val invalidText = stringResource(R.string.import_invalid)
    val successText = stringResource(R.string.import_success)
    val exportDoneText = stringResource(R.string.export_done)

    // Snackbar 统一由 MainActivity 顶层宿主展示，这里只发消息
    val importScope = rememberCoroutineScope()
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // 读取 + JSON 解析走 IO 线程，大备份文件不卡 UI
        importScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val content = context.contentResolver.openInputStream(uri)?.use { input ->
                        String(input.readBytes(), Charsets.UTF_8)
                    }
                    when {
                        content == null ->
                            ImportOutcome.Failure(readFailedText)
                        importMergeMode -> {
                            val merged = viewModel.mergeImportJson(content)
                            if (merged != null) {
                                ImportOutcome.Success(
                                    context.getString(R.string.import_merged, merged)
                                )
                            } else {
                                ImportOutcome.Failure(invalidText)
                            }
                        }
                        else -> ImportOutcome.Pending(content)
                    }
                } catch (e: Exception) {
                    ImportOutcome.Failure(
                        context.getString(R.string.import_read_failed_reason, e.message)
                    )
                }
            }
            when (result) {
                is ImportOutcome.Success ->
                    viewModel.postMessage(result.message, MessageVariant.SUCCESS)
                is ImportOutcome.Failure ->
                    viewModel.postMessage(result.message, MessageVariant.ERROR)
                is ImportOutcome.Pending -> pendingImport = result.content
            }
        }
    }

    // 页头随内容滚动；滚出页头后浅色主题切深状态栏图标（白图标在浅底上看不见）
    val scrolledPastHeader by remember {
        derivedStateOf { scrollState.value > 0 }
    }
    ScrollingTabStatusBar(scrolledPastHeader)

    // 底部导航栏悬浮在页面之上（见 MainActivity），内容底部预留其高度
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState)
                .navigationBarsPadding()
        ) {
            YuanmanHeader(
                title = stringResource(R.string.mine_title),
                subtitle = stringResource(
                    R.string.mine_badges_sub,
                    badgeUnlocks.size,
                    allBadges.size
                )
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            // 勋章墙（页头已显示解锁进度，行内再给一条进度条：离下一枚勋章还有多远一眼可见）
            SettingsCard(title = stringResource(R.string.mine_section_achievement)) {
                SettingRow(
                    icon = Icons.Filled.Star,
                    title = stringResource(R.string.mine_badges),
                    onClick = onNavigateToBadges
                )
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = {
                        if (allBadges.isEmpty()) 0f
                        else badgeUnlocks.size / allBadges.size.toFloat()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
            }

            // 外观
            SettingsCard(title = stringResource(R.string.mine_section_appearance)) {
                SettingRow(
                    icon = Icons.Filled.Settings,
                    title = stringResource(R.string.mine_dark_mode)
                )
                Spacer(Modifier.height(4.dp))
                // 主题三选：窄屏/大字号放不下时自动换行
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val view = LocalView.current
                    FilterChip(
                        selected = themeMode == ThemeMode.SYSTEM,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            viewModel.settings.setThemeMode(ThemeMode.SYSTEM)
                        },
                        label = { Text(stringResource(R.string.theme_system)) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                    FilterChip(
                        selected = themeMode == ThemeMode.LIGHT,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            viewModel.settings.setThemeMode(ThemeMode.LIGHT)
                        },
                        label = { Text(stringResource(R.string.theme_light)) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                    FilterChip(
                        selected = themeMode == ThemeMode.DARK,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            viewModel.settings.setThemeMode(ThemeMode.DARK)
                        },
                        label = { Text(stringResource(R.string.theme_dark)) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }

            // 记账
            SettingsCard(title = stringResource(R.string.mine_section_record)) {
                SettingRow(
                    icon = Icons.Filled.ShoppingCart,
                    title = stringResource(R.string.mine_budget),
                    subtitle = if (budgetCents > 0) {
                        MoneyUtils.formatCents(budgetCents)
                    } else {
                        stringResource(R.string.mine_budget_unset)
                    },
                    onClick = { onNavigateToBudget() }
                )
                SettingRow(
                    icon = Icons.Filled.Refresh,
                    title = stringResource(R.string.mine_recurring),
                    subtitle = stringResource(R.string.mine_recurring_sub),
                    onClick = onNavigateToRecurring
                )
                SettingRow(
                    icon = Icons.AutoMirrored.Filled.List,
                    title = stringResource(R.string.mine_categories),
                    subtitle = stringResource(R.string.mine_categories_sub),
                    onClick = onNavigateToCategories
                )
            }

            // 数据
            SettingsCard(title = stringResource(R.string.mine_section_data)) {
                SettingRow(
                    icon = Icons.Filled.Person,
                    title = stringResource(R.string.mine_family),
                    subtitle = stringResource(R.string.mine_family_sub),
                    onClick = onNavigateToFamily
                )
                SettingRow(
                    icon = Icons.Filled.Share,
                    title = stringResource(R.string.mine_export),
                    subtitle = stringResource(R.string.mine_export_sub),
                    onClick = {
                        exportBackup(
                            context,
                            viewModel,
                            onSuccess = {
                                viewModel.postMessage(exportDoneText, MessageVariant.SUCCESS)
                            },
                            onError = { msg ->
                                viewModel.postMessage(msg, MessageVariant.ERROR)
                            }
                        )
                    }
                )
                SettingRow(
                    icon = Icons.Filled.Add,
                    title = stringResource(R.string.mine_import),
                    subtitle = stringResource(R.string.mine_import_sub),
                    onClick = { showImportChooser = true }
                )
            }

            // 关于
            SettingsCard(title = stringResource(R.string.mine_section_about)) {
                SettingRow(
                    icon = Icons.Filled.Info,
                    title = stringResource(R.string.app_name),
                    subtitle = stringResource(
                        R.string.mine_about_version,
                        BuildConfig.VERSION_NAME
                    )
                )
                SettingRow(
                    icon = Icons.Filled.Favorite,
                    title = stringResource(R.string.mine_license),
                    subtitle = stringResource(R.string.mine_license_sub)
                )
                SettingRow(
                    icon = Icons.Filled.Email,
                    title = stringResource(R.string.mine_feedback),
                    subtitle = stringResource(R.string.mine_feedback_sub),
                    onClick = {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(
                                    "https://github.com/bobwatch/yuanman/issues"
                                )
                            )
                        )
                    }
                )
                SettingRow(
                    icon = Icons.Filled.Home,
                    title = stringResource(R.string.mine_repo),
                    subtitle = stringResource(R.string.mine_repo_sub),
                    onClick = {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/bobwatch/yuanman")
                            )
                        )
                    }
                )
            }
            }
        }
    }

    if (showImportChooser) {
        AlertDialog(
            onDismissRequest = { showImportChooser = false },
            title = { Text(stringResource(R.string.import_title)) },
            text = { Text(stringResource(R.string.import_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showImportChooser = false
                    importMergeMode = true
                    importLauncher.launch(
                        arrayOf("application/json", "text/*", "application/octet-stream")
                    )
                }) {
                    Text(stringResource(R.string.import_merge))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportChooser = false
                    importMergeMode = false
                    importLauncher.launch(
                        arrayOf("application/json", "text/*", "application/octet-stream")
                    )
                }) {
                    Text(
                        stringResource(R.string.import_overwrite),
                        // 覆盖导入会替换现有数据：用警示色标出代价，选之前先看清
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        )
    }

    pendingImport?.let { content ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.import_overwrite)) },
            text = {
                Text(
                    stringResource(
                        R.string.import_overwrite_msg,
                        viewModel.count
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    // 覆盖导入（解析 + 写入）走 IO 线程：大备份不冻结 UI，
                    // 完成前弹「正在导入」加载态
                    importing = true
                    importScope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            viewModel.importJson(content)
                        }
                        importing = false
                        viewModel.postMessage(
                            if (ok) successText else invalidText,
                            if (ok) MessageVariant.SUCCESS else MessageVariant.ERROR
                        )
                    }
                }) {
                    Text(stringResource(R.string.import_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (importing) {
        // 加载态弹窗：不可关闭，导入完成自动收起；
        // 转圈带读屏描述：TalkBack 用户能听到「正在导入，请稍候」而不是无声等待
        AlertDialog(
            onDismissRequest = {},
            icon = {
                // stringResource 是 @Composable：先在组合上下文取好，semantics 里只用值
                val importingWait = stringResource(R.string.importing_wait)
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(28.dp)
                        .semantics {
                            contentDescription = importingWait
                        }
                )
            },
            title = { Text(stringResource(R.string.importing)) },
            text = {},
            confirmButton = {}
        )
    }
}

@Composable
internal fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    AppCard {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

/** 设置行：左侧品牌色圆底图标 + 标题/副标题 + 右侧箭头。 */
@Composable
internal fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null
) {
    val view = LocalView.current
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier
                        .clickable {
                            // 与全 App 触觉语言一致：导航型点击轻震一下
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onClick()
                        }
                        .pressScale(pressedScale = 0.97f)
                } else {
                    Modifier
                }
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (onClick != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 导入流程的中间结果：成功提示 / 失败提示 / 待确认的覆盖导入内容。 */
private sealed interface ImportOutcome {
    class Success(val message: String) : ImportOutcome
    class Failure(val message: String) : ImportOutcome
    class Pending(val content: String) : ImportOutcome
}
