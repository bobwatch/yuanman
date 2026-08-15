package com.moneyhistory.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
            // 页头统一为品牌渐变，状态栏与渐变顶部同色融合
            window.statusBarColor = YuanmanGradientTop.toArgb()
            // 底部导航条跟随主题：与底部 Tab 栏（surface 色）融为一体，
            // 全面屏手势横条浅色主题深色、深色主题浅色
            window.navigationBarColor =
                if (darkTheme) DarkSurface.toArgb() else LightSurface.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = AppShapes,
        content = content
    )
}
