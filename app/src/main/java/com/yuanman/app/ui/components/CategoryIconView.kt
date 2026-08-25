package com.yuanman.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yuanman.app.data.model.CategoryIconHelper

@Composable
fun CategoryIconView(
    iconName: String,
    colorHex: Long,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 24.dp
) {
    val bgColor = Color(colorHex)
    val iconVector = CategoryIconHelper.getIcon(iconName)

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bgColor.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = iconVector,
            contentDescription = null,
            tint = bgColor,
            modifier = Modifier.size(iconSize)
        )
    }
}
