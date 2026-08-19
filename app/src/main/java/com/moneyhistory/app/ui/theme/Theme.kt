package com.moneyhistory.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = YuanmanBlueDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3ECFA),
    onPrimaryContainer = Color(0xFF0B3D55),
    secondary = YuanmanBlueDark,
    onSecondary = Color.White,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = LightBackground,
    surfaceContainerHighest = LightSurfaceVariant
)

private val DarkColors = darkColorScheme(
    primary = YuanmanBlue,
    onPrimary = OnYuanmanBlueDark,
    primaryContainer = Color(0xFF1E4B66),
    onPrimaryContainer = Color(0xFFD3ECFA),
    secondary = Color(0xFF7FB8DF),
    onSecondary = DarkBackground,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = Color(0xFF1B2735),
    surfaceContainer = DarkSurface,
    surfaceContainerHighest = DarkSurfaceVariant
)

// 全局圆角体系：大卡 20dp / 中卡 16dp / 控件 12dp，各页统一
private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp)
)

/** 当前主题是否深色（首页滚动时切换状态栏图标深浅需要）。 */
val LocalDarkTheme = staticCompositionLocalOf { false }

/** 支出金额文本色：按当前主题取对比度达标的一档（普通字号用，图形元素仍用 [ExpenseRed]）。 */
@Composable
fun expenseAmountColor(): Color =
    if (LocalDarkTheme.current) ExpenseRedTextDark else ExpenseRedText

/** 收入金额文本色：按当前主题取对比度达标的一档（普通字号用，图形元素仍用 [IncomeGreen]）。 */
@Composable
fun incomeAmountColor(): Color =
    if (LocalDarkTheme.current) IncomeGreenTextDark else IncomeGreenText

/** 沅满蓝 Material 3 主题。 */
@Composable
fun YuanmanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // 全面屏沉浸：状态栏/导航栏全透明，页面内容（页头渐变 / 背景色）
            // 一路绘制到屏幕最顶端，时间、电量浮在内容上；图标深浅按页面背景切换。
            // 布局上各页头自绘渐变 + statusBarsPadding，页面底部 navigationBarsPadding，
            // 无需系统条占位。
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    // M3 1.2.x 的 MaterialTheme 只提供 colorScheme/shapes/typography 等六个 Local，
    // 并不提供 LocalContentColor（其默认值是 Color.Black）——只有 Surface/Card 这类
    // 自带 contentColor 的容器才会覆盖它。页面根容器是 Box 时，所有未显式指定 color
    // 的 Text/Icon 在深色主题下会渲染成纯黑（v0.0.3 深色主题文字不可读的根因）。
    // 这里显式提供主题 onSurface：浅色主题下与默认近黑等价，深色主题下变成亮色文字。
    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalContentColor provides colorScheme.onSurface
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = AppShapes,
            content = content
        )
    }
}
