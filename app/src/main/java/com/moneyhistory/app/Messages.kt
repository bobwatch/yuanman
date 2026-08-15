package com.moneyhistory.app

/** Toast 消息类型：信息 / 成功 / 警告 / 异常。 */
enum class MessageVariant {
    INFO,
    SUCCESS,
    WARNING,
    ERROR
}

/** 一条全局提示消息（含类型，UI 端据此着色）。 */
data class UiMessage(
    val text: String,
    val variant: MessageVariant = MessageVariant.INFO
)
