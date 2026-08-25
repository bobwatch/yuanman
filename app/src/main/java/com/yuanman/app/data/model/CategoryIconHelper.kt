package com.yuanman.app.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class CategoryIconInfo(
    val key: String,
    val name: String,
    val icon: ImageVector
)

object CategoryIconHelper {
    val AVAILABLE_ICONS = listOf(
        CategoryIconInfo("food", "餐饮", Icons.Default.Restaurant),
        CategoryIconInfo("traffic", "交通", Icons.Default.DirectionsCar),
        CategoryIconInfo("shopping", "购物", Icons.Default.ShoppingCart),
        CategoryIconInfo("entertainment", "娱乐", Icons.Default.SportsEsports),
        CategoryIconInfo("housing", "住房", Icons.Default.Home),
        CategoryIconInfo("medical", "医疗", Icons.Default.LocalHospital),
        CategoryIconInfo("education", "教育", Icons.Default.School),
        CategoryIconInfo("communication", "通讯", Icons.Default.PhoneAndroid),
        CategoryIconInfo("salary", "工资", Icons.Default.AccountBalanceWallet),
        CategoryIconInfo("bonus", "奖金", Icons.Default.CardGiftcard),
        CategoryIconInfo("finance", "理财", Icons.Default.TrendingUp),
        CategoryIconInfo("part_time", "兼职", Icons.Default.Work),
        CategoryIconInfo("travel", "旅行", Icons.Default.Flight),
        CategoryIconInfo("fitness", "运动", Icons.Default.FitnessCenter),
        CategoryIconInfo("digital", "数码", Icons.Default.LaptopMac),
        CategoryIconInfo("pet", "宠物", Icons.Default.Pets),
        CategoryIconInfo("book", "书籍", Icons.Default.MenuBook),
        CategoryIconInfo("gift", "人情", Icons.Default.Redeem),
        CategoryIconInfo("coffee", "饮品", Icons.Default.LocalCafe),
        CategoryIconInfo("snack", "零食", Icons.Default.Fastfood),
        CategoryIconInfo("other", "其他", Icons.Default.Category)
    )

    val PRESET_COLORS = listOf(
        0xFFFF5722, // 橙红
        0xFF4CAF50, // 绿色
        0xFF2196F3, // 蓝色
        0xFFFF9800, // 橙色
        0xFF9C27B0, // 紫色
        0xFFE91E63, // 粉红
        0xFF009688, // 青绿
        0xFF3F51B5, // 靛蓝
        0xFF795548, // 棕色
        0xFF607D8B, // 蓝灰
        0xFFFFC107, // 琥珀黄
        0xFF00BCD4  // 青色
    )

    fun getIcon(key: String): ImageVector {
        return AVAILABLE_ICONS.find { it.key == key }?.icon ?: Icons.Default.Category
    }
}
