package com.yuanman.app.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
