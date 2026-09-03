package com.yuanman.app.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 账户与资产图标及预设辅助工具
 */
object AccountIconHelper {

    data class IconItem(
        val name: String,
        val icon: ImageVector,
        val label: String
    )

    val availableIcons: List<IconItem> = listOf(
        IconItem("account_balance_wallet", Icons.Default.AccountBalanceWallet, "钱包"),
        IconItem("wechat", Icons.Default.Payments, "微信"),
        IconItem("alipay", Icons.Default.QrCode, "支付宝"),
        IconItem("bank_card", Icons.Default.CreditCard, "银行卡"),
        IconItem("account_balance", Icons.Default.AccountBalance, "银行/机构"),
        IconItem("credit_card", Icons.Default.CreditCard, "信用卡"),
        IconItem("cash", Icons.Default.Payments, "现金"),
        IconItem("trending_up", Icons.AutoMirrored.Filled.TrendingUp, "基金理财"),
        IconItem("show_chart", Icons.AutoMirrored.Filled.ShowChart, "股票证券"),
        IconItem("savings", Icons.Default.Savings, "储蓄存单"),
        IconItem("currency_exchange", Icons.Default.CurrencyExchange, "外汇/黄金"),
        IconItem("house", Icons.Default.House, "不动产/房产"),
        IconItem("directions_car", Icons.Default.DirectionsCar, "车辆交通"),
        IconItem("card_giftcard", Icons.Default.CardGiftcard, "礼品卡/礼金"),
        IconItem("redeem", Icons.Default.Redeem, "公积金/福利")
    )

    val defaultColorHexes: List<String> = listOf(
        "#059669", // 翠绿 (微信/流动资金)
        "#1677FF", // 支付宝蓝
        "#2563EB", // 科技蓝 (投资)
        "#7C3AED", // 紫色 (理财)
        "#DC2626", // 红色 (信用卡/负债)
        "#EA580C", // 橙色 (借记卡)
        "#D97706", // 琥珀黄 (现金)
        "#0891B2", // 青色
        "#475569"  // 石板灰
    )

    fun getIcon(iconName: String): ImageVector {
        return when (iconName) {
            "account_balance" -> Icons.Default.AccountBalance
            "credit_card" -> Icons.Default.CreditCard
            "trending_up" -> Icons.AutoMirrored.Filled.TrendingUp
            "savings" -> Icons.Default.Savings
            "payments", "cash", "wechat" -> Icons.Default.Payments
            "show_chart", "stock" -> Icons.AutoMirrored.Filled.ShowChart
            "house" -> Icons.Default.House
            "directions_car" -> Icons.Default.DirectionsCar
            "alipay" -> Icons.Default.QrCode
            "bank_card" -> Icons.Default.CreditCard
            "card_giftcard" -> Icons.Default.CardGiftcard
            "redeem" -> Icons.Default.Redeem
            "currency_exchange" -> Icons.Default.CurrencyExchange
            else -> Icons.Default.AccountBalanceWallet
        }
    }
}
