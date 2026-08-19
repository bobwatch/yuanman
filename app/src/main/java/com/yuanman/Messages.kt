package com.yuanman

/** Toast 消息类型：信息 / 成功 / 警告 / 异常。 */
enum class MessageVariant {
    INFO,
    SUCCESS,
    WARNING,
    ERROR
}

/** 一条全局提示消息（含类型与可选操作按钮，UI 端据此着色/渲染）。 */
data class UiMessage(
    val text: String,
    val variant: MessageVariant = MessageVariant.INFO,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null
)
