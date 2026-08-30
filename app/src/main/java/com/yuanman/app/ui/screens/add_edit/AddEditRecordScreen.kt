package com.yuanman.app.ui.screens.add_edit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.yuanman.app.data.model.PaymentMethod
import com.yuanman.app.data.model.QuickEntryParser
import com.yuanman.app.data.model.RecordType
import com.yuanman.app.ui.components.CategoryIconView
import com.yuanman.app.ui.components.ConfirmDeleteDialog
import com.yuanman.app.ui.components.CustomKeypad
import com.yuanman.app.ui.components.KeypadEngine
import com.yuanman.app.ui.components.YuanmanModalBottomSheet
import com.yuanman.app.ui.components.YuanmanDatePickerSheet
import com.yuanman.app.utils.DateTimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.RoundingMode
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun AddEditRecordScreen(
    viewModel: AddEditRecordViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToCategoryManage: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var isRemarkFocused by remember { mutableStateOf(false) }
    val remarkFocusRequester = remember { FocusRequester() }
    val isImeVisible = WindowInsets.isImeVisible

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showPaymentSheet by remember { mutableStateOf(false) }
    var showRecordDateSheet by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = if (uiState.type == RecordType.EXPENSE) 0 else 1) { 2 }
    val expenseGridState = rememberLazyGridState()
    val incomeGridState = rememberLazyGridState()

    // 左右滑动切换页面联动更新收支类型
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val targetType = if (page == 0) RecordType.EXPENSE else RecordType.INCOME
            if (uiState.type != targetType) {
                viewModel.setRecordType(targetType)
            }
        }
    }

    // 外部或快捷录入改变类型时，联动 Pager 平滑切页
    LaunchedEffect(uiState.type) {
        val targetPage = if (uiState.type == RecordType.EXPENSE) 0 else 1
        if (pagerState.currentPage != targetPage && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    // 当软键盘收起时，自动去除两端空白并持久化保存备注
    var wasImeEverVisible by remember { mutableStateOf(false) }
    LaunchedEffect(isImeVisible) {
        if (isImeVisible) {
            wasImeEverVisible = true
        } else if (wasImeEverVisible) {
            wasImeEverVisible = false
            if (isRemarkFocused) {
                isRemarkFocused = false
                focusManager.clearFocus()
            }
            if (uiState.remark.isNotEmpty()) {
                viewModel.setRemark(uiState.remark.trim())
            }
        }
    }

    LaunchedEffect(uiState.isSavedSuccess) {
        if (uiState.isSavedSuccess) {
            onNavigateBack()
        }
    }

    LaunchedEffect(isRemarkFocused) {
        if (isRemarkFocused) {
            try {
                kotlinx.coroutines.delay(80)
                remarkFocusRequester.requestFocus()
                keyboardController?.show()
            } catch (_: Exception) {}
        }
    }

    val toast = com.yuanman.app.ui.components.LocalToastHostState.current

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            toast.error(msg)
            viewModel.clearErrorMessage()
        }
    }

    LaunchedEffect(uiState.savedFeedbackMessage) {
        uiState.savedFeedbackMessage?.let { msg ->
            toast.success(msg)
            viewModel.clearSavedFeedbackMessage()
        }
    }

    // 当选中的分类变化时，自动滚动使选中分类可见
    LaunchedEffect(uiState.selectedCategory?.id, uiState.type) {
        val selectedId = uiState.selectedCategory?.id
        if (selectedId != null) {
            if (uiState.type == RecordType.EXPENSE && uiState.expenseCategories.isNotEmpty()) {
                val index = uiState.expenseCategories.indexOfFirst { it.id == selectedId }
                if (index >= 0) {
                    expenseGridState.animateScrollToItem(index)
                }
            } else if (uiState.type == RecordType.INCOME && uiState.incomeCategories.isNotEmpty()) {
                val index = uiState.incomeCategories.indexOfFirst { it.id == selectedId }
                if (index >= 0) {
                    incomeGridState.animateScrollToItem(index)
                }
            }
        }
    }

    val isExpense = pagerState.currentPage == 0
    val themeActiveColor = if (isExpense) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            CenterAlignedTopAppBar(
                modifier = Modifier.offset(y = (-4).dp),
                title = {
                    if (uiState.isEditMode) {
                        Text(
                            text = "编辑账单",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    } else {
                        // 🌟 顶部极简分段胶囊（支出 / 收入）
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 支出
                                Surface(
                                    shape = CircleShape,
                                    color = if (isExpense) MaterialTheme.colorScheme.error else Color.Transparent,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable {
                                            keyboardController?.hide()
                                            focusManager.clearFocus()
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(0)
                                            }
                                        }
                                ) {
                                    Text(
                                        text = "支出",
                                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                                        fontSize = 13.sp,
                                        fontWeight = if (isExpense) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isExpense) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // 收入
                                Surface(
                                    shape = CircleShape,
                                    color = if (!isExpense) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable {
                                            keyboardController?.hide()
                                            focusManager.clearFocus()
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(1)
                                            }
                                        }
                                ) {
                                    Text(
                                        text = "收入",
                                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                                        fontSize = 13.sp,
                                        fontWeight = if (!isExpense) FontWeight.Bold else FontWeight.Medium,
                                        color = if (!isExpense) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Close, contentDescription = "取消")
                    }
                },
                actions = {
                    if (uiState.isEditMode) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            // 🌟 1. 金额与当前分类主展示区 (精致主题色轻卡片)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = themeActiveColor.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, themeActiveColor.copy(alpha = 0.2f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // 当前选中的分类胶囊
                    if (uiState.selectedCategory != null) {
                        val category = uiState.selectedCategory!!
                        val catColor = Color(category.colorHex)
                        Surface(
                            shape = CircleShape,
                            color = catColor.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, catColor.copy(alpha = 0.4f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                CategoryIconView(
                                    iconName = category.iconName,
                                    colorHex = category.colorHex,
                                    size = 22.dp,
                                    iconSize = 13.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = category.name,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = catColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "请选择分类",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        )
                    }

                    // 大字号金额与算式预览
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "¥",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = themeActiveColor,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = uiState.expression.ifEmpty { "0.00" },
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Bold,
                                color = themeActiveColor,
                                letterSpacing = (-0.5).sp
                            )
                        }

                        // 算式计算结果预览
                        val resultPreview = KeypadEngine.calculate(uiState.expression)
                        if (resultPreview != null && KeypadEngine.hasOperator(uiState.expression)) {
                            Text(
                                text = "= ¥${resultPreview.setScale(2, RoundingMode.HALF_UP)}",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = themeActiveColor.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            // 🌟 2. 主分类选择矩阵 (支持左右滑动切换支出/收入)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                val isExpensePage = page == 0
                val categories = if (isExpensePage) {
                    uiState.expenseCategories.ifEmpty { uiState.availableCategories.filter { it.type == "EXPENSE" } }
                } else {
                    uiState.incomeCategories.ifEmpty { uiState.availableCategories.filter { it.type == "INCOME" } }
                }
                val gridState = if (isExpensePage) expenseGridState else incomeGridState

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    state = gridState,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(categories, key = { it.id }) { category ->
                        val isSelected = uiState.selectedCategory?.id == category.id
                        val categoryColor = Color(category.colorHex)

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    viewModel.selectCategory(category)
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) categoryColor.copy(alpha = 0.18f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                    )
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(2.dp, categoryColor, CircleShape)
                                        } else {
                                            Modifier
                                        }
                                    )
                            ) {
                                CategoryIconView(
                                    iconName = category.iconName,
                                    colorHex = category.colorHex,
                                    size = 48.dp,
                                    iconSize = 24.dp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) categoryColor else MaterialTheme.colorScheme.onSurface
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // 末尾便捷「+ 自定义」管理入口
                    if (onNavigateToCategoryManage != null) {
                        item(key = "manage_category_item_${page}") {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                        onNavigateToCategoryManage()
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                            CircleShape
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "管理分类",
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "自定义",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    ),
                                    maxLines = 1,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // 🌟 3. 紧贴键盘上方的一体化属性栏与快捷备注
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                // 快捷推荐备注标签行（加大字号与点击区域）
                if (uiState.quickRemarks.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(uiState.quickRemarks) { tag ->
                            val isSelected = uiState.remark.contains(tag)

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) themeActiveColor.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = if (isSelected) BorderStroke(1.dp, themeActiveColor.copy(alpha = 0.6f)) else null,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { viewModel.selectQuickRemark(tag) }
                            ) {
                                Text(
                                    text = tag,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) themeActiveColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                }

                // 属性配置区域：
                // 当 isRemarkFocused 为 true（进入输入态）时：吸附系统输入法的整行都替换为全宽输入框 + [完成] 按钮
                // 当 isRemarkFocused 为 false（常态）时：紧凑单行三胶囊展示 [日期胶囊] + [支付方式胶囊] + [备注输入胶囊]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isRemarkFocused) {
                        // 1. 日期与时分选择胶囊
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    showRecordDateSheet = true
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CalendarToday,
                                    contentDescription = "选择日期时间",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = DateTimeUtils.formatRecordDateShort(uiState.recordTime),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // 2. 支付方式 / 入账账户胶囊
                        val hasPaymentMethod = uiState.paymentMethod.isNotBlank()
                        val hasSpread = isExpense && uiState.spreadMonths > 1
                        val isPaymentActive = hasPaymentMethod || hasSpread

                        val paymentDisplayText = when {
                            !isExpense -> if (hasPaymentMethod) uiState.paymentMethod else "入账账户"
                            hasPaymentMethod && hasSpread -> "${uiState.paymentMethod} · 分摊${uiState.spreadMonths}月"
                            hasPaymentMethod -> uiState.paymentMethod
                            hasSpread -> "分摊 ${uiState.spreadMonths} 个月"
                            else -> "支付方式"
                        }

                        val paymentIcon = if (isExpense) {
                            if (isPaymentActive) Icons.Default.CreditCard else Icons.Outlined.CreditCard
                        } else {
                            if (isPaymentActive) Icons.Default.AccountBalanceWallet else Icons.Outlined.AccountBalanceWallet
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isPaymentActive) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            },
                            border = if (isPaymentActive) {
                                BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                            } else null,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    showPaymentSheet = true
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = paymentIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = if (isPaymentActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = paymentDisplayText,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isPaymentActive) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isPaymentActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (hasPaymentMethod || hasSpread) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "清除",
                                        modifier = Modifier
                                            .size(13.dp)
                                            .clickable {
                                                viewModel.setPaymentMethod("")
                                                viewModel.setSpreadMonths(1)
                                            },
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }

                    // 3. 备注输入框 (常态下为单行胶囊，点击或输入态下整行独占全宽)
                    val hasRemark = uiState.remark.isNotBlank()
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isRemarkFocused) themeActiveColor.copy(alpha = 0.12f) else if (hasRemark) themeActiveColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(
                            if (isRemarkFocused) 1.5.dp else 1.dp,
                            if (isRemarkFocused) themeActiveColor else if (hasRemark) themeActiveColor.copy(alpha = 0.4f) else Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = if (isRemarkFocused) 8.dp else 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.EditNote,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (hasRemark || isRemarkFocused) themeActiveColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (uiState.remark.isEmpty()) {
                                    Text(
                                        text = if (isRemarkFocused) "添加备注（如：麦当劳、打车回家）..." else "写备注...",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                BasicTextField(
                                    value = uiState.remark,
                                    onValueChange = { viewModel.setRemark(it) },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 13.5.sp,
                                        fontWeight = if (hasRemark || isRemarkFocused) FontWeight.Medium else FontWeight.Normal
                                    ),
                                    cursorBrush = SolidColor(themeActiveColor),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Text,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            keyboardController?.hide()
                                            focusManager.clearFocus()
                                            isRemarkFocused = false
                                        }
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged {
                                            isRemarkFocused = it.isFocused
                                        }
                                )
                            }
                            if (hasRemark) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "清空备注",
                                    modifier = Modifier
                                        .size(15.dp)
                                        .clip(CircleShape)
                                        .clickable { viewModel.setRemark("") },
                                    tint = themeActiveColor.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    // 输入态时右侧清晰的「完成」收起按钮
                    if (isRemarkFocused) {
                        Button(
                            onClick = {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                isRemarkFocused = false
                            },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            modifier = Modifier.height(36.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = themeActiveColor),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("完成", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            // 🌟 4. 底部沉浸式计算器键盘 / 软键盘自适应切换
            if (isImeVisible || isRemarkFocused) {
                Spacer(
                    modifier = Modifier
                        .imePadding()
                        .navigationBarsPadding()
                )
            } else {
                CustomKeypad(
                    expression = uiState.expression,
                    onExpressionChange = { viewModel.setExpression(it) },
                    onComplete = { viewModel.saveRecord(continueNext = false) },
                    onSaveAndContinue = { viewModel.saveRecord(continueNext = true) },
                    isEditMode = uiState.isEditMode,
                    hapticEnabled = uiState.hapticEnabled
                )
            }
        }
    }

    if (showRecordDateSheet) {
        YuanmanDatePickerSheet(
            initialDateMillis = uiState.recordTime,
            onDateTimeSelected = viewModel::setRecordTime,
            onDismiss = { showRecordDateSheet = false }
        )
    }



    // 🌟 6. 支付方式 / 入账账户与分摊设置 ModalBottomSheet
    if (showPaymentSheet) {
        YuanmanModalBottomSheet(
            onDismissRequest = { showPaymentSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 36.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isExpense) "选择支付方式" else "选择入账账户",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (uiState.paymentMethod.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    viewModel.setPaymentMethod("")
                                    showPaymentSheet = false
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) {
                                Text("清空/不设", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Button(
                            onClick = { showPaymentSheet = false },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("完成", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                val methodsList = if (isExpense) PaymentMethod.EXPENSE_METHODS else PaymentMethod.INCOME_ACCOUNTS

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(methodsList) { method ->
                        val isSelected = uiState.paymentMethod == method
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.setPaymentMethod(method)
                                    if (!isExpense) {
                                        showPaymentSheet = false
                                    }
                                }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Text(
                                    text = method,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // 支出场景专属：跨月分摊 / 分期记账设置
                if (isExpense && !uiState.isEditMode) {
                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.EventRepeat,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "跨月分摊 (选填)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "将总金额平摊到未来月份预算中（如房租、半年付、分期购物）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val spreadOptions = listOf(
                        1 to "单笔(不分摊)",
                        2 to "分摊 2 个月",
                        3 to "分摊 3 个月",
                        6 to "分摊 6 个月",
                        12 to "分摊 12 个月",
                        24 to "分摊 24 个月"
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(spreadOptions) { (months, label) ->
                            val isSelected = uiState.spreadMonths == months
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { viewModel.setSpreadMonths(months) }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    ConfirmDeleteDialog(
        visible = showDeleteConfirm,
        title = "删除账单",
        message = "确定要删除这条账单记录吗？删除后不可恢复。",
        onConfirm = { viewModel.deleteRecord() },
        onDismiss = { showDeleteConfirm = false }
    )
}
