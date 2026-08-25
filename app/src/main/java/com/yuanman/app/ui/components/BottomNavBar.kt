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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.yuanman.app.ui.navigation.BottomNavTab

/**
 * 沅满·纯Icon灵动悬浮导航栏 (Minimalist Icon Dock with Delicate Micro-Indicator)
 */
@Composable
fun BottomNavBar(
    navController: NavController,
    onAddRecord: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val haptic = LocalHapticFeedback.current

    val tabs = BottomNavTab.ALL
    val selectedIndex = tabs.indexOfFirst { it.screen.route == currentRoute }.coerceAtLeast(0)
    val selectedSlotIndex = if (selectedIndex >= 2) selectedIndex + 1 else selectedIndex

    val activeColor = if (isDark) Color(0xFF34D399) else Color(0xFF059669)
    val inactiveColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 32.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = if (isDark) Color(0xF5141922) else Color(0xF8FFFFFF),
            tonalElevation = 6.dp,
            shadowElevation = 10.dp,
            border = BorderStroke(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = if (isDark) {
                        listOf(
                            Color.White.copy(alpha = 0.16f),
                            Color(0xFF34D399).copy(alpha = 0.20f)
                        )
                    } else {
                        listOf(
                            Color.White.copy(alpha = 0.95f),
                            Color.Black.copy(alpha = 0.08f)
                        )
                    }
                )
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                val tabCount = tabs.size + 1
                val itemWidth = maxWidth / tabCount

                // 🌟 微型滑动光晕小块 (比 Icon 还小，直径 18dp 的微型柔光球 + 底部小滑点)
                val indicatorCenterOffset by animateDpAsState(
                    targetValue = itemWidth * selectedSlotIndex + (itemWidth - 18.dp) / 2,
                    animationSpec = spring(
                        dampingRatio = 0.72f,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "tabIndicatorOffset"
                )

                // 1. 图标背后的小微光层 (直径 18dp，小于 24dp 图标，形成立体层次)
                Box(
                    modifier = Modifier
                        .offset(x = indicatorCenterOffset)
                        .size(18.dp)
                        .align(Alignment.CenterStart)
                        .clip(CircleShape)
                        .background(
                            if (isDark) {
                                Color(0xFF34D399).copy(alpha = 0.22f)
                            } else {
                                Color(0xFF059669).copy(alpha = 0.15f)
                            }
                        )
                )

                // 2. 底部极细小光标条 (12dp x 3dp)
                val bottomDotOffset by animateDpAsState(
                    targetValue = itemWidth * selectedSlotIndex + (itemWidth - 12.dp) / 2,
                    animationSpec = spring(
                        dampingRatio = 0.72f,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "bottomDotOffset"
                )

                Box(
                    modifier = Modifier
                        .offset(x = bottomDotOffset)
                        .padding(bottom = 5.dp)
                        .size(width = 12.dp, height = 3.dp)
                        .align(Alignment.BottomStart)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(activeColor)
                )

                // 🌟 3. 纯 Icon 层
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEachIndexed { index, tab ->
                        if (index == 2) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onAddRecord()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = activeColor,
                                    shadowElevation = 6.dp,
                                    modifier = Modifier.size(52.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "记一笔",
                                            tint = if (isDark) Color(0xFF062E24) else MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            }
                        }

                        val isSelected = index == selectedIndex
                        val targetRoute = tab.screen.route

                        val iconScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.15f else 1.0f,
                            animationSpec = spring(
                                dampingRatio = 0.55f,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "tabIconScale"
                        )

                        val iconTint by animateColorAsState(
                            targetValue = if (isSelected) activeColor else inactiveColor,
                            animationSpec = tween(180),
                            label = "tabIconColor"
                        )

                        val interactionSource = remember { MutableInteractionSource() }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null
                                ) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (currentRoute != targetRoute) {
                                        navController.navigate(targetRoute) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.title,
                                tint = iconTint,
                                modifier = Modifier
                                    .scale(iconScale)
                                    .size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
