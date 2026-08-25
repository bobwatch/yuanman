package com.yuanman.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.yuanman.app.data.model.BrandTheme
import com.yuanman.app.data.model.ThemeMode
import com.yuanman.app.ui.navigation.YuanmanNavGraph
import com.yuanman.app.ui.theme.YuanmanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val app = application as YuanmanApplication

        setContent {
            val themeMode by app.preferencesRepository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val brandTheme by app.preferencesRepository.brandTheme.collectAsState(initial = BrandTheme.EMERALD)

            YuanmanTheme(
                themeMode = themeMode,
                brandTheme = brandTheme
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    YuanmanNavGraph(
                        navController = navController,
                        app = app
                    )
                }
            }
        }
    }
}
