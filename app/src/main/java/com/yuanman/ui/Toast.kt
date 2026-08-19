package com.yuanman.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuanman.MessageVariant
import com.yuanman.ui.theme.ExpenseRed
import com.yuanman.ui.theme.IncomeGreen
import com.yuanman.ui.theme.WarningOrange
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
        // 上限 3 条：连发操作时旧提示自然被顶走，避免遮满屏幕
        _toasts.update { (it + toast).takeLast(3) }
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
    // 深浅色走主题的 inverseSurface / inverseOnSurface（浅色主题深底、深色主题浅底），
    // 用户手动切换主题时 Toast 自动跟着换，不手工维护两套色值
    val container = MaterialTheme.colorScheme.inverseSurface
    val content = MaterialTheme.colorScheme.inverseOnSurface
    // 按消息类型着色（信息蓝 / 成功绿 / 警告橙 / 异常红），统一引用主题语义色
    val variantColor = when (toast.variant) {
        MessageVariant.INFO -> MaterialTheme.colorScheme.primary
        MessageVariant.SUCCESS -> IncomeGreen
        MessageVariant.WARNING -> WarningOrange
        MessageVariant.ERROR -> ExpenseRed
    }
    // 类型图标：彩色浅底圆 + 同色图标，一眼识别消息类型（比色条/色点更直观）
    val variantIcon = when (toast.variant) {
        MessageVariant.INFO -> Icons.Filled.Info
        MessageVariant.SUCCESS -> Icons.Filled.CheckCircle
        MessageVariant.WARNING -> Icons.Filled.Warning
        MessageVariant.ERROR -> Icons.Filled.ErrorOutline
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
        // 入场：顶部滑下 + 轻微缩放弹出（0.92→1），比纯位移更有「落下来」的质感
        enter = slideInVertically(initialOffsetY = { -it }) +
            scaleIn(
                initialScale = 0.92f,
                animationSpec = tween(220, easing = FastOutSlowInEasing)
            ) +
            fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it / 2 }) + fadeOut()
    ) {
        SwipeToDismissBox(
            state = dismissState,
            // 滑开时露出的底色 + 右侧关闭指示：让「可滑动关闭」这个手势看得见
            backgroundContent = {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(end = 14.dp)
                            .size(20.dp)
                    )
                }
            },
            content = {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = container,
                        contentColor = content
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                    // 无障碍：Toast 是瞬时状态消息，礼貌式播报让 TalkBack 用户
                    // 也能听到「已保存 / 已删除」这类结果反馈
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite }
                ) {
                    Row(
                        Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 类型图标：彩色浅底圆 + 同色图标（18dp），视觉焦点一眼可见
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(variantColor.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = variantIcon,
                                contentDescription = null,
                                tint = variantColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = toast.message,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
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
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        )
    }
}
