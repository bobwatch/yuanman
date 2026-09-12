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
        // 注意：EXPENSE_METHODS / INCOME_ACCOUNTS 仍被首页/账单筛选页等消费，内容不可改动（只读消费方）。
        val EXPENSE_METHODS = listOf("微信支付", "支付宝", "现金", "银行卡", "信用卡", "花呗/白条", "其他")
        val INCOME_ACCOUNTS = listOf("微信零钱", "支付宝", "银行卡", "现金", "投资理财", "其他")

        /**
         * v0.0.4.6「支出/收入账户合一」：记账页内置常用账户预设（收支通用，选择器与老用户预置引导共用同一文案源）。
         * 由支出侧（银行卡/信用卡/花呗/白条）与收入侧（微信支付/支付宝/现金/银行卡）去重合并而来，
         * 名称沿用支出侧 EXPENSE_METHODS 文案（收入侧历史的「微信零钱」流水可经账户名含「微信」启发式匹配）。
         */
        val COMMON_ACCOUNTS = listOf("微信支付", "支付宝", "现金", "银行卡", "信用卡", "花呗/白条")

        fun defaultMethod(): String = ""
    }
}
