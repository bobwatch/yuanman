package com.yuanman.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.yuanman.app.ui.navigation.BottomNavTab

import androidx.compose.ui.graphics.luminance

@Composable
fun BottomNavBar(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry = navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry.value?.destination?.route
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    // 悬浮居中毛玻璃胶囊容器
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(percent = 50),
            color = if (isDark) {
                Color(0xDD202422)
            } else {
                Color(0xF0FFFFFF)
            },
            tonalElevation = 6.dp,
            shadowElevation = 10.dp,
            border = BorderStroke(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = if (isDark) {
                        listOf(
                            Color.White.copy(alpha = 0.22f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    } else {
                        listOf(
                            Color.White.copy(alpha = 0.95f),
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )
                    }
                )
            ),
            modifier = Modifier
                .height(62.dp)
                .fillMaxWidth(0.92f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavTab.ALL.forEach { tab ->
                    val isSelected = currentRoute == tab.screen.route

                    FloatingTabItem(
                        tab = tab,
                        isSelected = isSelected,
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

@Composable
private fun FloatingTabItem(
    tab: BottomNavTab,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    // 动画：图标缩放 (Q弹微动效)
    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.18f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "iconScale"
    )

    // 动画：图标颜色
    val iconColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        },
        label = "iconColor"
    )

    // 动画：底部灵动胶囊指示条
    val dotWidth by animateDpAsState(
        targetValue = if (isSelected) 18.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "dotWidth"
    )
    val dotAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        label = "dotAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(percent = 50))
            .clickable(
                interactionSource = interactionSource,
                indication = null, // 无整块底色，以纯动效与微光呈现创意
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 微光晕 + 图标
        Box(
            modifier = Modifier.size(36.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                // 柔和微光晕 (非整块背景)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = CircleShape
                        )
                )
            }

            Icon(
                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                contentDescription = tab.title,
                tint = iconColor,
                modifier = Modifier
                    .size(24.dp)
                    .scale(iconScale)
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // 底部指示条
        Box(
            modifier = Modifier
                .height(3.5.dp)
                .width(dotWidth)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = dotAlpha),
                    shape = RoundedCornerShape(percent = 50)
                )
        )
    }
}
