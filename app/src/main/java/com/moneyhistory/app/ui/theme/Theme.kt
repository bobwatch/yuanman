package com.moneyhistory.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = YuanmanBlue,
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
    onPrimary = Color.White,
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
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                !darkTheme
        }
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
