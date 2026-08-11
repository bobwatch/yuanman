package com.moneyhistory.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyhistory.app.BuildConfig
import com.moneyhistory.app.MainViewModel
import com.moneyhistory.app.MoneyUtils
import com.moneyhistory.app.R
import com.moneyhistory.app.ThemeMode
import com.moneyhistory.app.allBadges

/** 我的 Tab：勋章墙 + 外观/记账/数据/关于全部设置项。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MineScreen(
    viewModel: MainViewModel,
    onNavigateToBadges: () -> Unit,
    onNavigateToFamily: () -> Unit,
    onNavigateToRecurring: () -> Unit,
    onNavigateToCategories: () -> Unit
) {
    val context = LocalContext.current

    val themeMode by viewModel.settings.themeMode.collectAsStateWithLifecycle()
    val budgetCents by viewModel.settings.budgetCents.collectAsStateWithLifecycle()
    val badgeUnlocks by viewModel.settings.badgeUnlocks.collectAsStateWithLifecycle()

    var showBudgetDialog by remember { mutableStateOf(false) }
    var showImportChooser by remember { mutableStateOf(false) }
    var importMergeMode by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<String?>(null) }

    val readFailedText = stringResource(R.string.import_read_failed)
    val invalidText = stringResource(R.string.import_invalid)
    val successText = stringResource(R.string.import_success)

    // Snackbar 统一由 MainActivity 顶层宿主展示，这里只发消息
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val content = context.contentResolver.openInputStream(uri)?.use { input ->
                String(input.readBytes(), Charsets.UTF_8)
            }
            when {
                content == null -> viewModel.postMessage(readFailedText)
                importMergeMode -> {
                    val merged = viewModel.mergeImportJson(content)
                    viewModel.postMessage(
                        if (merged != null) {
                            context.getString(R.string.import_merged, merged)
                        } else {
                            invalidText
                        }
                    )
                }
                else -> pendingImport = content
            }
        } catch (e: Exception) {
            viewModel.postMessage(
                context.getString(R.string.import_read_failed_reason, e.message)
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mine_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 勋章墙
            SettingsCard(title = stringResource(R.string.mine_section_achievement)) {
                SettingsRow(
                    title = stringResource(R.string.mine_badges),
                    subtitle = stringResource(
                        R.string.mine_badges_sub,
                        badgeUnlocks.size,
                        allBadges.size
                    ),
                    onClick = onNavigateToBadges
                )
            }

            // 外观
            SettingsCard(title = stringResource(R.string.mine_section_appearance)) {
                Text(
                    text = stringResource(R.string.mine_dark_mode),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = themeMode == ThemeMode.SYSTEM,
                        onClick = { viewModel.settings.setThemeMode(ThemeMode.SYSTEM) },
                        label = { Text(stringResource(R.string.theme_system)) }
                    )
                    FilterChip(
                        selected = themeMode == ThemeMode.LIGHT,
                        onClick = { viewModel.settings.setThemeMode(ThemeMode.LIGHT) },
                        label = { Text(stringResource(R.string.theme_light)) }
                    )
                    FilterChip(
                        selected = themeMode == ThemeMode.DARK,
                        onClick = { viewModel.settings.setThemeMode(ThemeMode.DARK) },
                        label = { Text(stringResource(R.string.theme_dark)) }
                    )
                }
            }

            // 记账
            SettingsCard(title = stringResource(R.string.mine_section_record)) {
                SettingsRow(
                    title = stringResource(R.string.mine_budget),
                    subtitle = if (budgetCents > 0) {
                        MoneyUtils.formatCents(budgetCents)
                    } else {
                        stringResource(R.string.mine_budget_unset)
                    },
                    onClick = { showBudgetDialog = true }
                )
                SettingsRow(
                    title = stringResource(R.string.mine_recurring),
                    subtitle = stringResource(R.string.mine_recurring_sub),
                    onClick = onNavigateToRecurring
                )
                SettingsRow(
                    title = stringResource(R.string.mine_categories),
                    subtitle = stringResource(R.string.mine_categories_sub),
                    onClick = onNavigateToCategories
                )
            }

            // 数据
            SettingsCard(title = stringResource(R.string.mine_section_data)) {
                SettingsRow(
                    title = stringResource(R.string.mine_family),
                    subtitle = stringResource(R.string.mine_family_sub),
                    onClick = onNavigateToFamily
                )
                SettingsRow(
                    title = stringResource(R.string.mine_export),
                    subtitle = stringResource(R.string.mine_export_sub),
                    onClick = {
                        exportBackup(context, viewModel) { msg ->
                            viewModel.postMessage(msg)
                        }
                    }
                )
                SettingsRow(
                    title = stringResource(R.string.mine_import),
                    subtitle = stringResource(R.string.mine_import_sub),
                    onClick = { showImportChooser = true }
                )
            }

            // 关于
            SettingsCard(title = stringResource(R.string.mine_section_about)) {
                SettingsRow(
                    title = stringResource(R.string.app_name),
                    subtitle = stringResource(
                        R.string.mine_about_version,
                        BuildConfig.VERSION_NAME
                    )
                )
                SettingsRow(
                    title = stringResource(R.string.mine_license),
                    subtitle = "MIT License"
                )
                SettingsRow(
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
                SettingsRow(
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

        if (showBudgetDialog) {
            BudgetDialog(
                currentCents = budgetCents,
                onDismiss = { showBudgetDialog = false },
                onSave = { viewModel.settings.setBudgetCents(it) }
            )
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
                        Text(stringResource(R.string.import_overwrite))
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
                        val ok = viewModel.importJson(content)
                        viewModel.postMessage(if (ok) successText else invalidText)
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
    }
}

@Composable
internal fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
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

@Composable
internal fun SettingsRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
