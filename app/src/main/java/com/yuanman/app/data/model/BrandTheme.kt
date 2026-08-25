package com.yuanman.app.data.model

import androidx.compose.ui.graphics.Color

enum class BrandTheme(
    val id: String,
    val title: String,
    val primaryColor: Color,
    val primaryContainerColor: Color,
    val darkPrimaryColor: Color,
    val darkPrimaryContainerColor: Color
) {
    EMERALD(
        id = "emerald",
        title = "翡翠墨玉",
        primaryColor = Color(0xFF2E7D32),
        primaryContainerColor = Color(0xFFE8F5E9),
        darkPrimaryColor = Color(0xFF4ADE80),
        darkPrimaryContainerColor = Color(0xFF1B5E20)
    ),
    OCEAN(
        id = "ocean",
        title = "晴空蔚蓝",
        primaryColor = Color(0xFF1976D2),
        primaryContainerColor = Color(0xFFE3F2FD),
        darkPrimaryColor = Color(0xFF64B5F6),
        darkPrimaryContainerColor = Color(0xFF0D47A1)
    ),
    SAKURA(
        id = "sakura",
        title = "落樱暮粉",
        primaryColor = Color(0xFFD81B60),
        primaryContainerColor = Color(0xFFFCE4EC),
        darkPrimaryColor = Color(0xFFF48FB1),
        darkPrimaryContainerColor = Color(0xFF880E4F)
    ),
    SUNSET(
        id = "sunset",
        title = "日落暖橙",
        primaryColor = Color(0xFFE65100),
        primaryContainerColor = Color(0xFFFFF3E0),
        darkPrimaryColor = Color(0xFFFFB74D),
        darkPrimaryContainerColor = Color(0xFFBF360C)
    ),
    PURPLE(
        id = "purple",
        title = "深空星紫",
        primaryColor = Color(0xFF7B1FA2),
        primaryContainerColor = Color(0xFFF3E5F5),
        darkPrimaryColor = Color(0xFFCE93D8),
        darkPrimaryContainerColor = Color(0xFF4A148C)
    );

    companion object {
        fun fromId(id: String?): BrandTheme {
            return values().firstOrNull { it.id == id } ?: EMERALD
        }
    }
}
