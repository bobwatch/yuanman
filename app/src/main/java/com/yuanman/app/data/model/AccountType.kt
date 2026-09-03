package com.yuanman.app.data.model

/**
 * 账户分类体系
 */
enum class AccountType(
    val typeName: String,
    val groupTitle: String,
    val defaultIconName: String,
    val defaultColorHex: String
) {
    /** 流动资金：微信、支付宝、银行借记卡、现金 */
    CHECKING("流动资金", "资金账户", "account_balance_wallet", "#059669"),

    /** 投资理财：股票、基金、黄金、加密货币、理财产品 */
    INVESTMENT("投资理财", "投资账户", "trending_up", "#2563EB"),

    /** 信用负债：信用卡、花呗/白条、借款、房贷车贷 */
    CREDIT("信用负债", "负债账户", "credit_card", "#DC2626"),

    /** 资产/其他：公积金、押金、借出款、固定资产 */
    ASSET("其他资产", "资产账户", "savings", "#D97706");

    val isLiability: Boolean
        get() = this == CREDIT

    companion object {
        fun fromString(value: String): AccountType {
            return try {
                valueOf(value.uppercase())
            } catch (e: Exception) {
                CHECKING
            }
        }
    }
}
