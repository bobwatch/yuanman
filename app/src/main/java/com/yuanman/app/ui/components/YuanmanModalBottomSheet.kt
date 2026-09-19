package com.yuanman.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** sheet 统一左右内边距：全 App 的弹层正文都以此为基准 */
val SheetHorizontalPadding = 20.dp

/** sheet 统一底部主按钮高度 */
private val SheetPrimaryButtonHeight = 48.dp

/**
 * Shared bottom-sheet chrome used throughout the app.
 * Keeping the surface, elevation, shape and drag handle in one place prevents
 * subtle differences between the payment, settings and detail sheets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YuanmanModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 6.dp,
        // 全屏遮罩压暗：包含顶部状态栏区域，保持与父级页面一致弱化展示，避免状态栏突兀高亮
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.40f),
        // windowInsets 设为 0，使遮罩自顶部 (0,0) 开始覆盖全屏（含状态栏），状态栏与页面一同被压暗弱化
        windowInsets = WindowInsets(0, 0, 0, 0),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        // Apply the gesture/navigation inset once, at the shared container
        // level, so every sheet reaches the edge cleanly on gesture devices.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            content = content
        )
    }
}

/**
 * sheet 统一标题块：标题 16sp Bold + 可选副标题 11.5sp outline。
 *
 * 各 sheet 此前分别用 15/16/20sp 三档标题、11 与 14sp 两档副标题，同一 App 里
 * 打开不同弹层会有肉眼可见的字号跳变；统一走这里。
 */
@Composable
fun SheetTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

/** sheet 底部主按钮：统一 48dp 高、12dp 圆角、15sp Bold 文案 */
@Composable
fun SheetPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(SheetPrimaryButtonHeight)
    ) {
        Text(text = text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}
