package com.yuanman.app.data.model

enum class PaymentMethod(val displayName: String) {
    WECHAT("微信支付"),
    ALIPAY("支付宝"),
    BANK_CARD("银行卡"),
    CASH("现金"),
    CREDIT_CARD("信用卡"),
    OTHER("其他");

    companion object {
        val ALL = values().map { it.displayName }
        val EXPENSE_METHODS = listOf("微信支付", "支付宝", "现金", "银行卡", "信用卡", "花呗/白条", "其他")
        val INCOME_ACCOUNTS = listOf("微信零钱", "支付宝", "银行卡", "现金", "投资理财", "其他")

        fun defaultMethod(): String = ""
    }
}
