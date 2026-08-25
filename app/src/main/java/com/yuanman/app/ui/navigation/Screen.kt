package com.yuanman.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.yuanman.app.data.model.RecordType

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object RecordList : Screen("record_list")
    object Statistics : Screen("statistics")
    object CategoryManage : Screen("category_manage")
    object Settings : Screen("settings")

    object AddEditRecord : Screen("add_edit_record?recordId={recordId}&type={type}") {
        fun createRoute(recordId: Long = 0L, type: RecordType? = null): String {
            val typeParam = type?.name ?: ""
            return "add_edit_record?recordId=$recordId&type=$typeParam"
        }
    }

    object RecordDetail : Screen("record_detail/{recordId}") {
        fun createRoute(recordId: Long): String {
            return "record_detail/$recordId"
        }
    }

    object AddEditCategory : Screen("add_edit_category?categoryId={categoryId}&type={type}") {
        fun createRoute(categoryId: Long = 0L, type: RecordType? = null): String {
            val typeParam = type?.name ?: ""
            return "add_edit_category?categoryId=$categoryId&type=$typeParam"
        }
    }
}

sealed class BottomNavTab(
    val screen: Screen,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Home : BottomNavTab(
        screen = Screen.Home,
        title = "首页",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    )

    object List : BottomNavTab(
        screen = Screen.RecordList,
        title = "明细",
        selectedIcon = Icons.AutoMirrored.Filled.ReceiptLong,
        unselectedIcon = Icons.AutoMirrored.Outlined.ReceiptLong
    )

    object Statistics : BottomNavTab(
        screen = Screen.Statistics,
        title = "统计",
        selectedIcon = Icons.Filled.PieChart,
        unselectedIcon = Icons.Outlined.PieChart
    )

    object Category : BottomNavTab(
        screen = Screen.CategoryManage,
        title = "分类",
        selectedIcon = Icons.Filled.Category,
        unselectedIcon = Icons.Outlined.Category
    )

    object Settings : BottomNavTab(
        screen = Screen.Settings,
        title = "设置",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )

    companion object {
        val ALL = listOf(Home, List, Statistics, Settings)
    }
}
