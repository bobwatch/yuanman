@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.yuanman.app.ui.screens.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuanman.app.ui.components.EmptyStateView
import com.yuanman.app.ui.components.LocalToastHostState
import com.yuanman.app.utils.DateTimeUtils
import com.yuanman.app.utils.MoneyUtils
import java.math.BigDecimal

/**
 * 发薪分配 二级页（设计文档 saving-plans-and-paycheck-v0.3.md §2.3 的页面形态）
 *
 * 页面内完成发薪分配完整闭环，纯引擎与模型全部复用 SavingPlanModels.kt（同包）：
 *  - 顶部规范头部（返回 + 「发薪分配」），Scaffold contentWindowInsets = statusBars
 *  - 上次分配摘要卡（uiState.paycheckLastRun：时间 / 金额 / 笔数 / 留存）
 *  - 本月工资入账候选：优先展示已自动匹配来源账户的候选（E6）；手填金额或候选未命中时
 *    自选正余额账户 chips 作为来源
 *  - 金额输入：复刻 PaycheckFlowSheet 的轻量「候选 + 手动填写」实现（不引入 CustomKeypad）
 *  - 规则预览：与执行共用同一纯引擎 [planPaycheckActions]（E7，输入相同输出必然相同），
 *    受限 0 金额步骤整行降透明度，note 一律纯文案不含金额
 *  - 执行按钮 → viewModel.executePaycheck(...) → toast，LastRun 摘要随 uiState 自动刷新
 *
 * 金额展示一律走 MoneyUtils（千分位 2 位）；隐私模式下所有金额文本掩码「¥ ••••」
 * （账户名 / 计划名等名称不受影响）。无账户 → 空态引导；本页不新建 viewModel。
 */
@Composable
fun PaycheckRunScreen(
    viewModel: AccountViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val toast = LocalToastHostState.current
    val colors = MaterialTheme.colorScheme

    val accounts = uiState.accounts
    val plans = uiState.plans
    val scheme = uiState.paycheckScheme
    val isPrivacyMode = uiState.isPrivacyMode
    val hasScheme = scheme.rules.isNotEmpty()

    // ---- 表单状态（与 PaycheckFlowSheet 同款互斥口径）----
    var selectedRecordId by remember { mutableStateOf<Long?>(null) }
    var manualInput by remember { mutableStateOf("") }
    var pickedSourceId by remember { mutableStateOf<Long?>(null) }

    // 候选仅列已自动匹配来源账户的工资入账（优先级最高）；未匹配靠手动 + 自选来源兜底
    val matchedCandidates = remember(uiState.incomeCandidates) {
        uiState.incomeCandidates.filter { it.matchedAccountId != null }
    }
    val selectedCandidate = matchedCandidates.firstOrNull { it.recordId == selectedRecordId }
    val accountNameById = remember(accounts) { accounts.associate { it.id to it.name } }

    val manualAmountCents = remember(manualInput) { parseManualYuanToCents(manualInput) }
    val amountCents = selectedCandidate?.amountCents ?: manualAmountCents

    // 来源账户：候选命中账户优先（E6）；未命中 / 手填 / 命中账户已不在列表 → 用户自选
    val matchedSourceId = selectedCandidate?.matchedAccountId
    val sourceAccount = accounts.firstOrNull { it.id == (matchedSourceId ?: pickedSourceId) }
    val showSourcePick = amountCents > 0L && (matchedSourceId == null || sourceAccount == null)
    val positiveAccounts = remember(accounts) { accounts.filter { it.balanceCents > 0L } }

    // E7：预览与执行共用同一纯引擎；金额 / 来源未就绪时不预览
    val preview = remember(amountCents, sourceAccount, accounts, plans, scheme) {
        if (sourceAccount != null && amountCents > 0L) {
            planPaycheckActions(amountCents, sourceAccount, accounts, plans, scheme)
        } else {
            null
        }
    }

    val runPaycheck: () -> Unit = {
        val source = sourceAccount
        if (source != null && amountCents > 0L && hasScheme) {
            viewModel.executePaycheck(source.id, amountCents)
            toast.success("已按方案分配 " + MoneyUtils.formatCurrency(amountCents))
            // 复位表单便于连续分配；顶部 LastRun 摘要卡随 uiState 自动刷新
            selectedRecordId = null
            manualInput = ""
            pickedSourceId = null
        }
    }

    val onSelectCandidate: (Long) -> Unit = { recordId ->
        selectedRecordId = recordId
        manualInput = ""
    }
    val onManualInputChange: (String) -> Unit = { input ->
        manualInput = input
        if (input.isNotBlank()) selectedRecordId = null
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            // 规范头部（与 CategoryManageScreen / AddEditRecordScreen 同式）：左上返回钮 + 靠左标题
            TopAppBar(
                modifier = Modifier.offset(y = (-4).dp),
                title = {
                    Text(
                        text = "发薪分配",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (accounts.isEmpty()) {
                // ---- 无账户空态：给引导不发散（对应 PaycheckFlowSheet 空态）----
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    EmptyStateView(
                        title = "请先创建资金账户",
                        description = "创建微信、支付宝、储蓄卡等账户后，工资到账即可按方案自动分账",
                        icon = Icons.Outlined.AccountBalanceWallet,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // ---- 上次分配摘要 ----
                    PaycheckLastRunCard(
                        lastRun = uiState.paycheckLastRun,
                        isPrivacyMode = isPrivacyMode,
                        modifier = Modifier.padding(top = 6.dp)
                    )

                    // ---- 金额 + 来源卡 ----
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "分配金额与来源",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp
                                ),
                                color = colors.onSurfaceVariant
                            )

                            // ---- 本月工资入账候选（自动匹配）----
                            Text(
                                text = "本月工资入账",
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                                color = colors.onSurfaceVariant
                            )
                            if (matchedCandidates.isEmpty()) {
                                Text(
                                    text = "本月暂无工资入账记录，可手动填写下方金额",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = colors.outline,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            } else {
                                matchedCandidates.forEachIndexed { index, candidate ->
                                    if (index > 0) {
                                        HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.3f))
                                    }
                                    PaycheckCandidateRow(
                                        candidate = candidate,
                                        sourceName = candidate.matchedAccountName
                                            ?: accountNameById[candidate.matchedAccountId],
                                        isSelected = candidate.recordId == selectedRecordId,
                                        isPrivacyMode = isPrivacyMode,
                                        onClick = { onSelectCandidate(candidate.recordId) }
                                    )
                                }
                            }

                            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.3f))

                            // ---- 或手动填写本次到手金额 ----
                            Text(
                                text = "或手动填写本次到手金额",
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                                color = colors.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = manualInput,
                                onValueChange = onManualInputChange,
                                label = { Text("金额 (元)") },
                                placeholder = { Text("0.00") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            // ---- 来源账户自选（候选未命中 / 手填时出现，E6）----
                            if (showSourcePick) {
                                Text(
                                    text = "来源账户",
                                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                                    color = colors.onSurfaceVariant
                                )
                                if (positiveAccounts.isEmpty()) {
                                    Text(
                                        text = "没有余额为正的账户，暂时无法分配",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = colors.error,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                } else {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        positiveAccounts.forEach { acc ->
                                            FilterChip(
                                                selected = acc.id == pickedSourceId,
                                                onClick = { pickedSourceId = acc.id },
                                                shape = RoundedCornerShape(10.dp),
                                                label = {
                                                    Text(
                                                        text = acc.name,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // ---- 实时小字：来源 + 可分配金额（隐私掩码）----
                            if (amountCents > 0L) {
                                Text(
                                    text = when {
                                        sourceAccount != null ->
                                            "来源：${sourceAccount.name} · 可分配 ${displayMoney(amountCents, isPrivacyMode)}"
                                        else -> "请先选择来源账户"
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = if (sourceAccount != null) colors.onSurfaceVariant else colors.outline,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // ---- 分配预览卡 ----
                    if (hasScheme) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = colors.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "分配预览",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp
                                        ),
                                        color = colors.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${scheme.rules.size} 条规则 · " +
                                            if (scheme.autoClearDebts) "剩余自动清欠" else "未自动清欠",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                        color = colors.outline,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                when {
                                    preview == null -> Text(
                                        text = "选择金额并确认来源账户后，这里会按方案展示将执行的动作",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = colors.outline,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                    preview.steps.isEmpty() -> Text(
                                        text = "按当前方案没有可执行的动作",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = colors.outline,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                    else -> {
                                        preview.steps.forEachIndexed { index, step ->
                                            if (index > 0) {
                                                HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.3f))
                                            }
                                            PaycheckRunStepRow(step = step, isPrivacyMode = isPrivacyMode)
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                    }
                                }
                            }
                        }
                    }

                    // ---- 方案为空引导 + 执行钮 ----
                    if (!hasScheme) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = colors.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "还没有分配方案",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = colors.onSurface
                                )
                                Text(
                                    text = "先到「攒钱计划」页添加几条分配规则（转账户 / 攒进计划），再回来一键分账",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = colors.outline
                                )
                            }
                        }
                    }

                    Button(
                        onClick = runPaycheck,
                        enabled = amountCents > 0L && sourceAccount != null && hasScheme,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = "确认分配 ${displayMoney(amountCents, isPrivacyMode)}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp)) // 底部预留
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 纯展示辅助（无状态；与 PaycheckFlowSheet 同款小段实现，允许少量重复）
// ---------------------------------------------------------------------------

/** 隐私掩码文案（与账户操作面板同一形式） */
private const val PRIVACY_MASK = "¥ ••••"

/** 展示金额：隐私 → 掩码；否则千分位 2 位（MoneyUtils 口径） */
private fun displayMoney(cents: Long, isPrivacyMode: Boolean): String =
    if (isPrivacyMode) PRIVACY_MASK else MoneyUtils.formatCurrency(cents)

/** 手填金额解析：BigDecimal → 分（与 AccountForms.kt / PaycheckFlowSheet 同款），非法输入当 0 */
private fun parseManualYuanToCents(yuanInput: String): Long {
    if (yuanInput.isBlank()) return 0L
    return try {
        val bd = BigDecimal(yuanInput.trim())
        bd.multiply(BigDecimal(100)).toLong()
    } catch (e: Exception) {
        0L
    }
}

// ---------------------------------------------------------------------------
// 上次分配摘要卡
// ---------------------------------------------------------------------------

/**
 * 上次分配摘要卡：执行过 → 主行金额 + 「M月d日 HH:mm · N 笔动作 · 留存」；
 * 未执行过 → 引导文案。金额在隐私态掩码。
 */
@Composable
private fun PaycheckLastRunCard(
    lastRun: PaycheckLastRunUiModel,
    isPrivacyMode: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (lastRun.at == null) {
                Text(
                    text = "上次发薪分配",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp
                    ),
                    color = colors.onSurfaceVariant
                )
                Text(
                    text = "还没有执行过分配。完成下方选择后一键按方案分账，结果会记录在这里",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = colors.outline
                )
            } else {
                val amountText = if (isPrivacyMode) {
                    PRIVACY_MASK
                } else {
                    "¥" + MoneyUtils.centsToYuanString(lastRun.amountCents ?: 0L, withGrouping = true)
                }
                val actionCount = lastRun.actionCount ?: 0
                val remainingCents = lastRun.remainingCents
                val remainText = if (remainingCents != null && remainingCents > 0L) {
                    val remainAmount = if (isPrivacyMode) {
                        PRIVACY_MASK
                    } else {
                        "¥" + MoneyUtils.centsToYuanString(remainingCents, withGrouping = true)
                    }
                    " · 留存 $remainAmount"
                } else {
                    ""
                }

                Text(
                    text = "上次发薪分配",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp
                    ),
                    color = colors.onSurfaceVariant
                )
                Text(
                    text = "$amountText · $actionCount 笔动作",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = DateTimeUtils.formatDateTime(lastRun.at) + remainText,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                    color = colors.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 候选行 / 预览行
// ---------------------------------------------------------------------------

/** 工资入账候选行：金额 + note + 副行日期/来源；选中态右侧 primary CheckCircle */
@Composable
private fun PaycheckCandidateRow(
    candidate: IncomeCandidateUiModel,
    sourceName: String?,
    isSelected: Boolean,
    isPrivacyMode: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayMoney(candidate.amountCents, isPrivacyMode),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    color = colors.onSurface,
                    maxLines = 1
                )
                // remark 为空则省
                if (candidate.note.isNotBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = candidate.note,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${DateTimeUtils.formatMonthDay(candidate.at)} · " +
                    (sourceName?.let { "$it · 工资入账" } ?: "已自动匹配来源账户"),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                color = colors.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * 预览动作行：转 / 攒 / 留存前缀 + 图标语义色 + 可选的引擎 note 副行；
 * 受限行（note 非空）整行降透明度；引擎 note 纯文案不含金额，0 金额步骤不画 ¥0.00。
 */
@Composable
private fun PaycheckRunStepRow(step: PaycheckStepUi, isPrivacyMode: Boolean) {
    val colors = MaterialTheme.colorScheme
    val constrained = !step.note.isNullOrBlank()

    val prefix: String
    val icon: ImageVector?
    val iconTint: Color
    when (step.kind) {
        PaycheckStepKind.TO_PLAN -> {
            prefix = "攒"
            icon = Icons.Default.Savings
            iconTint = colors.primary
        }
        PaycheckStepKind.TO_ACCOUNT, PaycheckStepKind.CLEAR_DEBT -> {
            prefix = "转"
            icon = Icons.Default.SwapHoriz
            iconTint = colors.secondary
        }
        PaycheckStepKind.REMAIN -> {
            prefix = "留存"
            icon = null
            iconTint = colors.outline
        }
    }

    val amountText = displayMoney(step.amountCents, isPrivacyMode)
    val mainText = when {
        step.kind == PaycheckStepKind.REMAIN -> "留存 $amountText"
        step.amountCents <= 0L -> "$prefix → ${step.caption}" // 受限 0 金额：不展示 ¥0.00
        else -> "$prefix $amountText → ${step.caption}"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp)
            .alpha(if (constrained) 0.62f else 1f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(26.dp), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(14.dp)
                )
            } else {
                // 留存：无图标，用 4dp 圆点占位
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(iconTint)
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = mainText,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // 受限原因副行（留存行的引擎 note 即行语义，不重复展示）
            val noteText = step.note
            if (noteText != null && noteText.isNotBlank() && step.kind != PaycheckStepKind.REMAIN) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "（$noteText）",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                    color = colors.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
