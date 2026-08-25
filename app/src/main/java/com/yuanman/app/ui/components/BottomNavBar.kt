package com.yuanman.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.yuanman.app.ui.navigation.BottomNavTab

/**
 * 沅满·极简典雅悬浮 Dock (Sleek Minimalist Floating Dock)
 *
 * 核心审美升级：
 * 1. 【黄金紧凑比例】：彻底去除臃肿层叠与大色块，采用紧凑胶囊宽度 (264dp × 56dp)，告别空旷稀疏感。
 * 2. 【纯粹通透毛玻璃】：超细 0.8dp 细腻光感发丝描边 + 柔和弥散环境光影。
 * 3. 【极简灵动小绿点】：正下方点缀晶莹小绿点，伴随物理阻尼弹簧自如滑动。
 * 4. 【生机色彩与浮动动效】：选中图标上浮 (-2dp) + 弹性缩放 (1.14x) + 翠绿/森林绿品牌色。
 */
@Composable
fun BottomNavBar(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry = navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry.value?.destination?.route
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val tabs = BottomNavTab.ALL
    val selectedIndex = tabs.indexOfFirst { it.screen.route == currentRoute }.coerceAtLeast(0)

    val activeColor = if (isDark) {
        Color(0xFF4ADE80) // 深色模式明快薄荷翠绿
    } else {
        Color(0xFF16A34A) // 浅色模式经典生机森林绿
    }

    val inactiveColor = if (isDark) {
        Color(0xFF6B7280) // 优雅暗灰
    } else {
        Color(0xFF9CA3AF) // 柔和浅灰
    }

    // 外层悬浮容器
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        // 紧凑高级感胶囊外框 (264dp 紧凑宽度)
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = if (isDark) {
                Color(0xF0181B19) // 深色黑曜墨玉
            } else {
                Color(0xF5FFFFFF) // 浅色羊脂白玉
            },
            tonalElevation = 4.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(
                width = 0.8.dp,
                brush = Brush.verticalGradient(
                    colors = if (isDark) {
                        listOf(
                            Color.White.copy(alpha = 0.20f),
                            Color.White.copy(alpha = 0.04f)
                        )
                    } else {
                        listOf(
                            Color.White.copy(alpha = 0.95f),
                            Color.Black.copy(alpha = 0.06f)
                        )
                    }
                )
            ),
            modifier = Modifier
                .height(56.dp)
                .width(264.dp)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                val tabCount = tabs.size
                val tabWidth = maxWidth / tabCount

                // 🌟 1. 灵动滑动小绿点 (Sliding Emerald Dot)
                val dotOffset by animateDpAsState(
                    targetValue = tabWidth * selectedIndex + (tabWidth - 5.dp) / 2,
                    animationSpec = spring(
                        dampingRatio = 0.72f,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "dockDotOffset"
                )

                Box(
                    modifier = Modifier
                        .offset(x = dotOffset)
                        .padding(bottom = 5.dp)
                        .align(Alignment.BottomStart)
                        .size(5.dp)
                        .background(
                            color = activeColor,
                            shape = CircleShape
                        )
                )

                // 🌟 2. 纯净 Tab 图标层
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val isSelected = index == selectedIndex

                        SleekTabItem(
                            tab = tab,
                            isSelected = isSelected,
                            activeColor = activeColor,
                            inactiveColor = inactiveColor,
                            onClick = {
                                if (currentRoute != tab.screen.route) {
                                    navController.navigate(tab.screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SleekTabItem(
    tab: BottomNavTab,
    isSelected: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    // 动画：图标弹性微放大
    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.14f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "sleekIconScale"
    )

    // 动画：图标轻盈向上浮动
    val iconOffsetY by animateDpAsState(
        targetValue = if (isSelected) (-2).dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "sleekIconOffsetY"
    )

    // 动画：图标色彩平滑渐变
    val iconColor by animateColorAsState(
        targetValue = if (isSelected) activeColor else inactiveColor,
        animationSpec = tween(durationMillis = 200),
        label = "sleekIconColor"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(percent = 50))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // 极微弱环境柔光 (仅在选中时渲染极淡微光，不破坏整体极简纯净感)
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = activeColor.copy(alpha = 0.08f),
                        shape = CircleShape
                    )
            )
        }

        Icon(
            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
            contentDescription = tab.title,
            tint = iconColor,
            modifier = Modifier
                .offset(y = iconOffsetY)
                .size(24.dp)
                .scale(iconScale)
        )
    }
}
