package com.yuanman.app.utils

import android.os.SystemClock
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/**
 * 全局点击防抖扩展修饰符 (Debounce Click)
 *
 * 防止用户快速手抖或快速连按导致重复触发跳转或重复执行业务逻辑
 * 默认防抖阈值为 500ms
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.clickableDebounce(
    debounceTimeMs: Long = 500L,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = composed {
    var lastClickTime by remember { mutableLongStateOf(0L) }

    this.combinedClickable(
        enabled = enabled,
        onLongClick = onLongClick?.let { longAction ->
            {
                val currentTime = SystemClock.uptimeMillis()
                if (currentTime - lastClickTime >= debounceTimeMs) {
                    lastClickTime = currentTime
                    longAction()
                }
            }
        },
        onClick = {
            val currentTime = SystemClock.uptimeMillis()
            if (currentTime - lastClickTime >= debounceTimeMs) {
                lastClickTime = currentTime
                onClick()
            }
        }
    )
}

/**
 * 函数式防抖包装器，适用于无法直接使用 Modifier 的回调场合
 */
class DebounceHelper(private val debounceTimeMs: Long = 500L) {
    private var lastClickTime = 0L

    fun run(action: () -> Unit) {
        val currentTime = SystemClock.uptimeMillis()
        if (currentTime - lastClickTime >= debounceTimeMs) {
            lastClickTime = currentTime
            action()
        }
    }
}
