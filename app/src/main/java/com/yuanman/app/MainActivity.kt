package com.yuanman.app

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.navigation.compose.rememberNavController
import com.yuanman.app.data.model.ThemeMode
import com.yuanman.app.ui.components.LocalToastHostState
import com.yuanman.app.ui.components.ToastHostState
import com.yuanman.app.ui.components.TopToastHost
import com.yuanman.app.ui.navigation.YuanmanNavGraph
import com.yuanman.app.ui.theme.YuanmanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        val app = application as YuanmanApplication

        setContent {
            val themeMode by app.preferencesRepository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val toastHostState = remember { ToastHostState() }

            YuanmanTheme(themeMode = themeMode) {
                CompositionLocalProvider(LocalToastHostState provides toastHostState) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            val navController = rememberNavController()

                            // 页面导航图
                            YuanmanNavGraph(
                                navController = navController,
                                app = app
                            )

                            // 🌟 全局顶部 Toast 悬浮层 (永不被底部 TabBar 遮挡)
                            TopToastHost(
                                state = toastHostState,
                                modifier = Modifier
                                    .statusBarsPadding()
                                    .padding(top = 8.dp)
                                    .align(Alignment.TopCenter)
                            )
                        }
                    }
                }
            }
        }
    }
}
