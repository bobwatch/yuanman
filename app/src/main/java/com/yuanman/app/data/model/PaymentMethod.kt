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

        fun defaultMethod(): String = ""
    }
}
