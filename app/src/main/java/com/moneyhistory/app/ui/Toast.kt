package com.moneyhistory.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyhistory.app.MessageVariant
import com.moneyhistory.app.ui.theme.ExpenseRed
import com.moneyhistory.app.ui.theme.IncomeGreen
import com.moneyhistory.app.ui.theme.LocalDarkTheme
import com.moneyhistory.app.ui.theme.WarningOrange
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 一条 Toast。[leaving] 为 true 表示已进入退场动画，播完才真正移除。 */
data class ToastData(
    val id: Long,
    val message: String,
    val variant: MessageVariant = MessageVariant.INFO,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val leaving: Boolean = false
)

/**
 * shadcn 风格 Toast 状态：悬浮圆角卡片，按类型着色，自动消失、可滑动关闭、可带操作按钮。
 */
class ToastHostState {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _toasts = MutableStateFlow<List<ToastData>>(emptyList())
    val toasts: StateFlow<List<ToastData>> = _toasts.asStateFlow()
    private val idCounter = AtomicLong(0)

    fun show(
        message: String,
        variant: MessageVariant = MessageVariant.INFO,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
        durationMillis: Long? = null
    ) {
        val toast = ToastData(idCounter.incrementAndGet(), message, variant, actionLabel, onAction)
        _toasts.update { it + toast }
        scope.launch {
            // 带操作按钮的提示给足反应时间（撤销/查看），纯通知 3.5s 即走
            delay(durationMillis ?: if (actionLabel != null) 5000L else 3500L)
            dismiss(toast.id)
        }
    }

    /** 标记退场：ToastItem 播完退出动画后调 [remove] 真正移除。 */
    fun dismiss(id: Long) {
        _toasts.update { list ->
            list.map { if (it.id == id) it.copy(leaving = true) else it }
        }
    }

    fun remove(id: Long) {
        _toasts.update { list -> list.filterNot { it.id == id } }
    }
}

/** 顶部悬浮 Toast 容器：最新一条在最下方。 */
@Composable
fun ToastHost(
    state: ToastHostState,
    modifier: Modifier = Modifier
) {
    val toasts by state.toasts.collectAsStateWithLifecycle()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        toasts.forEach { toast ->
            key(toast.id) {
                ToastItem(
                    toast = toast,
                    onDismiss = { state.dismiss(toast.id) },
                    onRemoved = { state.remove(toast.id) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToastItem(
    toast: ToastData,
    onDismiss: () -> Unit,
    onRemoved: () -> Unit
) {
    // 深浅色取应用主题（非系统）：用户手动切换主题时 Toast 也要跟着换
    val darkTheme = LocalDarkTheme.current
    val container = if (darkTheme) Color(0xFFF2F4F8) else Color(0xFF232A35)
    val content = if (darkTheme) Color(0xFF161B22) else Color.White
    // 按消息类型着色（信息蓝 / 成功绿 / 警告橙 / 异常红），统一引用主题语义色
    val variantColor = when (toast.variant) {
        MessageVariant.INFO -> MaterialTheme.colorScheme.primary
        MessageVariant.SUCCESS -> IncomeGreen
        MessageVariant.WARNING -> WarningOrange
        MessageVariant.ERROR -> ExpenseRed
    }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    // 收到退场标记：先播退出动画（默认 300ms 淡出），播完再真正移除，
    // 不会「啪」地消失
    LaunchedEffect(toast.leaving) {
        if (toast.leaving) {
            visible = false
            delay(400)
            onRemoved()
        }
    }

    // 横向滑动关闭
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                onDismiss()
                true
            } else {
                false
            }
        }
    )

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = fadeOut()
    ) {
        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {},
            content = {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = container,
                        contentColor = content
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(start = 14.dp, top = 4.dp, bottom = 4.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 左侧类型色条
                        Box(
                            Modifier
                                .width(4.dp)
                                .height(34.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(variantColor)
                        )
                        Spacer(Modifier.width(10.dp))
                        // 类型色指示点
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(variantColor)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = toast.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = content,
                            modifier = Modifier.weight(1f)
                        )
                        if (toast.actionLabel != null) {
                            TextButton(
                                onClick = {
                                    toast.onAction?.invoke()
                                    onDismiss()
                                },
                                // 操作按钮给足点按面积：撤销这种高频补救入口不能太小
                                modifier = Modifier.height(48.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp)
                            ) {
                                Text(
                                    text = toast.actionLabel,
                                    color = variantColor,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        )
    }
}
