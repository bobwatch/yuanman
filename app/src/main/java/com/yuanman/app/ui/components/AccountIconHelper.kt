package com.yuanman.app.ui.components

import androidx.compose.ui.graphics.vector.ImageVector
import compose.icons.FontAwesomeIcons
import compose.icons.TablerIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Coins
import compose.icons.fontawesomeicons.solid.FileInvoiceDollar
import compose.icons.fontawesomeicons.solid.Gem
import compose.icons.fontawesomeicons.solid.GraduationCap
import compose.icons.fontawesomeicons.solid.HandHoldingUsd
import compose.icons.fontawesomeicons.solid.Landmark
import compose.icons.fontawesomeicons.solid.MoneyBill
import compose.icons.fontawesomeicons.solid.PiggyBank
import compose.icons.tablericons.ArrowUpRight
import compose.icons.tablericons.BuildingBank
import compose.icons.tablericons.Bus
import compose.icons.tablericons.Car
import compose.icons.tablericons.Cash
import compose.icons.tablericons.ChartCandle
import compose.icons.tablericons.ChartLine
import compose.icons.tablericons.ChartPie
import compose.icons.tablericons.CreditCard
import compose.icons.tablericons.CurrencyBitcoin
import compose.icons.tablericons.Gift
import compose.icons.tablericons.Home2
import compose.icons.tablericons.ReceiptRefund
import compose.icons.tablericons.ReceiptTax
import compose.icons.tablericons.ShieldCheck
import compose.icons.tablericons.ShoppingCart
import compose.icons.tablericons.ToolsKitchen2
import compose.icons.tablericons.Umbrella
import compose.icons.tablericons.Wallet

/**
 * 账户专用现代矢量图标库（基于 Tabler Icons 与 FontAwesome 官方开源图标库）
 * 完全替代手写绘制矢量，提供专业设计质感；
 * 覆盖现金、卡包、投资理财、高价值资产、借贷应收、生活专卡全品类。
 */
object AccountIconHelper {
    /** 账户专用矢量映射表（随账户选中主题色着色） */
    private val ACCOUNT_ICONS: Map<String, ImageVector> = mapOf(
        "cash" to TablerIcons.Cash,
        "bank_card" to TablerIcons.BuildingBank,
        "credit_card" to TablerIcons.CreditCard,
        "wallet" to TablerIcons.Wallet,
        "safe_box" to FontAwesomeIcons.Solid.PiggyBank,
        "savings" to FontAwesomeIcons.Solid.Landmark,
        "stock" to TablerIcons.ChartLine,
        "funds" to TablerIcons.ChartCandle,
        "gold" to FontAwesomeIcons.Solid.Coins,
        "crypto" to TablerIcons.CurrencyBitcoin,
        "real_estate" to TablerIcons.Home2,
        "vehicle" to TablerIcons.Car,
        "gem" to FontAwesomeIcons.Solid.Gem,
        "bonus" to FontAwesomeIcons.Solid.MoneyBill,
        "debt" to FontAwesomeIcons.Solid.HandHoldingUsd,
        "loan_out" to TablerIcons.ArrowUpRight,
        "reimbursement" to TablerIcons.ReceiptRefund,
        "invoice" to FontAwesomeIcons.Solid.FileInvoiceDollar,
        "provident_fund" to TablerIcons.ShieldCheck,
        "insurance" to TablerIcons.Umbrella,
        "education" to FontAwesomeIcons.Solid.GraduationCap,
        "card_gift" to TablerIcons.Gift,
        "shopping_card" to TablerIcons.ShoppingCart,
        "meal_card" to TablerIcons.ToolsKitchen2,
        "transport_card" to TablerIcons.Bus,
        "tax" to TablerIcons.ReceiptTax,
        "other" to TablerIcons.ChartPie
    )

    fun isMonochromeVector(key: String): Boolean = key in ACCOUNT_ICONS
    fun isAccountIcon(key: String): Boolean = key in ACCOUNT_ICONS

    fun getIcon(key: String): ImageVector? = ACCOUNT_ICONS[key]

    /**
     * 账户新增/编辑弹窗完整可选图标词表（精选自 Simple Icons、Tabler Icons、FontAwesome 官方标准库）
     */
    val ALL_AVAILABLE_ACCOUNT_ICONS: List<String> = listOf(
        // 1. 品牌渠道与主流支付（Simple Icons 官方高精矢量）
        "wechat", "alipay", "unionpay", "qq", "taobao", "jd", "applepay", "visa", "mastercard", "paypal", "baidu",
        // 2. 现金、储蓄与卡包（Tabler Icons & FontAwesome）
        "cash", "bank_card", "credit_card", "wallet", "safe_box", "savings",
        // 3. 投资理财、大件资产与贵金属（K线/基金/股票/黄金/房车等）
        "stock", "funds", "gold", "crypto", "real_estate", "vehicle", "gem",
        // 4. 薪酬、借贷往来与发票报销
        "bonus", "debt", "loan_out", "reimbursement", "invoice",
        // 5. 保障、生活卡券与专用账户（公积金/保险/教育金/商超/餐饮/交通/税费）
        "provident_fund", "insurance", "education", "card_gift", "shopping_card", "meal_card", "transport_card", "tax", "other"
    )
}
