package com.moneyhistory.app.ui.theme

import androidx.compose.ui.graphics.Color

// 沅满蓝：设计体系主色
val YuanmanBlue = Color(0xFF2AABEE)
val YuanmanBlueDark = Color(0xFF517DA2)
// Light 主题压暗变体（白字对比度达标）；dark 主色上的深色文字
val YuanmanBlueDeep = Color(0xFF167FB8)
val OnYuanmanBlueDark = Color(0xFF06283D)
// 品牌渐变：页头「沅满蓝」自上而下压深，保证白色文字对比度
val YuanmanGradientTop = Color(0xFF2AABEE)
val YuanmanGradientBottom = Color(0xFF178ACB)

// Light：页面背景微灰，卡片纯白「浮」在上面
val LightBackground = Color(0xFFF5F7FA)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFE4EAF0)
val LightOnSurface = Color(0xFF1C2733)
val LightOnSurfaceVariant = Color(0xFF6B7A8A)

// Dark：深灰蓝
val DarkBackground = Color(0xFF0E1621)
val DarkSurface = Color(0xFF17212B)
val DarkSurfaceVariant = Color(0xFF232E3C)
val DarkOnSurface = Color(0xFFF1F4F8)
val DarkOnSurfaceVariant = Color(0xFF8FA1B3)

// 语义色：支出红 / 收入绿 / 预算警示橙
val ExpenseRed = Color(0xFFE53935)
val IncomeGreen = Color(0xFF34A853)
val WarningOrange = Color(0xFFFF9800)

// 语义色「文本」变体：浅色主题压深一档保证白底对比度（WCAG AA 4.5:1），
// 深色主题提亮一档保证深底可读。亮色原版只留给色块/色条等图形元素
// （进度条、Toast 色条、滑动删除底、收支药丸等）。
val ExpenseRedText = Color(0xFFC62828)
val IncomeGreenText = Color(0xFF1E8E3E)
val ExpenseRedTextDark = Color(0xFFEF5350)
val IncomeGreenTextDark = Color(0xFF66BB6A)
// 预算警示的「文本」变体：浅色主题压深一档（白底对比度达标），
// 用于页头预算卡接近上限时的数值/进度条（卡片恒为白底，只用到浅色档）
val WarningOrangeText = Color(0xFFE65100)
