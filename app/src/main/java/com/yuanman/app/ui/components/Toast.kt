package com.yuanman.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

enum class ToastType {
    SUCCESS,
    ERROR,
    WARNING,
    INFO
}

data class ToastData(
    val id: Long,
    val message: String,
    val type: ToastType = ToastType.INFO,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val leaving: Boolean = false
)

/**
 * 🌟 全局顶部 Toast 状态控制器
 */
class ToastHostState {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _toasts = MutableStateFlow<List<ToastData>>(emptyList())
    val toasts: StateFlow<List<ToastData>> = _toasts.asStateFlow()
    private val idCounter = AtomicLong(0)

    fun show(
        message: String,
        type: ToastType = ToastType.INFO,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
        durationMillis: Long? = null
    ) {
        val toast = ToastData(idCounter.incrementAndGet(), message, type, actionLabel, onAction)
        _toasts.update { (it + toast).takeLast(2) }
        scope.launch {
            delay(durationMillis ?: if (actionLabel != null) 4500L else 2800L)
            dismiss(toast.id)
        }
    }

    fun success(message: String) = show(message, ToastType.SUCCESS)
    fun error(message: String) = show(message, ToastType.ERROR)
    fun warning(message: String) = show(message, ToastType.WARNING)
    fun info(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) =
        show(message, ToastType.INFO, actionLabel, onAction)

    fun dismiss(id: Long) {
        _toasts.update { list ->
            list.map { if (it.id == id) it.copy(leaving = true) else it }
        }
    }

    fun remove(id: Long) {
        _toasts.update { list -> list.filterNot { it.id == id } }
    }

    fun close() {
        scope.cancel()
    }
}

val LocalToastHostState = staticCompositionLocalOf<ToastHostState> {
    ToastHostState()
}

/**
 * 🌟 顶部全局 Toast 宿主容器（置于顶层 Popup 视窗，浮于所有 BottomSheet、遮罩及页面之上）
 */
@Composable
fun TopToastHost(
    state: ToastHostState,
    modifier: Modifier = Modifier
) {
    val toasts by state.toasts.collectAsStateWithLifecycle()
    if (toasts.isEmpty()) return

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp

    Popup(
        alignment = Alignment.TopCenter,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Column(
            modifier = Modifier
                .width(screenWidth)
                .padding(top = statusBarTop + 8.dp)
                .padding(horizontal = 16.dp)
                .then(modifier),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            toasts.forEach { toast ->
                key(toast.id) {
                    TopToastItem(
                        toast = toast,
                        onDismiss = { state.dismiss(toast.id) },
                        onRemoved = { state.remove(toast.id) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopToastItem(
    toast: ToastData,
    onDismiss: () -> Unit,
    onRemoved: () -> Unit
) {
    // 🌟 四种不同类型的独立高亮质感配色方案（高对比深底 + 强边框 + 发光投影 + 晶莹图标）
    val (containerColor, borderColor, badgeColor, accentColor, glowColor, variantIcon) = when (toast.type) {
        ToastType.SUCCESS -> {
            Tuple6(
                Color(0xFF092419),                     // 墨玉深绿
                Color(0xFF10B981).copy(alpha = 0.85f), // 翠绿高亮边框
                Color(0xFF10B981).copy(alpha = 0.25f), // 图标光晕底色
                Color(0xFF34D399),                     // 高亮翡翠绿
                Color(0xFF10B981).copy(alpha = 0.45f), // 外部高亮发光投影
                Icons.Filled.CheckCircle
            )
        }
        ToastType.ERROR -> {
            Tuple6(
                Color(0xFF2B0E12),                     // 珊瑚赤红
                Color(0xFFEF4444).copy(alpha = 0.85f), // 赤红高亮边框
                Color(0xFFEF4444).copy(alpha = 0.25f), // 图标光晕底色
                Color(0xFFF87171),                     // 高亮赤红
                Color(0xFFEF4444).copy(alpha = 0.45f), // 外部高亮发光投影
                Icons.Filled.ErrorOutline
            )
        }
        ToastType.WARNING -> {
            Tuple6(
                Color(0xFF281706),                     // 琥珀金棕
                Color(0xFFF59E0B).copy(alpha = 0.85f), // 琥珀金边框
                Color(0xFFF59E0B).copy(alpha = 0.25f), // 图标光晕底色
                Color(0xFFFBBF24),                     // 高亮琥珀金
                Color(0xFFF59E0B).copy(alpha = 0.45f), // 外部高亮发光投影
                Icons.Filled.Warning
            )
        }
        ToastType.INFO -> {
            Tuple6(
                Color(0xFF0A1F36),                     // 霁蓝夜空
                Color(0xFF38BDF8).copy(alpha = 0.85f), // 天青高亮边框
                Color(0xFF38BDF8).copy(alpha = 0.25f), // 图标光晕底色
                Color(0xFF38BDF8),                     // 高亮天青蓝
                Color(0xFF38BDF8).copy(alpha = 0.45f), // 外部高亮发光投影
                Icons.Filled.Info
            )
        }
    }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    LaunchedEffect(toast.leaving) {
        if (toast.leaving) {
            visible = false
            delay(300)
            onRemoved()
        }
    }

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
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = tween(260, easing = FastOutSlowInEasing)
        ) + scaleIn(
            initialScale = 0.92f,
            animationSpec = tween(260, easing = FastOutSlowInEasing)
        ) + fadeIn(),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(200)
        ) + fadeOut()
    ) {
        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF374151).copy(alpha = 0.5f)),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = Color(0xFF9CA3AF),
                        modifier = Modifier.padding(end = 14.dp).size(18.dp)
                    )
                }
            },
            content = {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = containerColor,
                        contentColor = Color(0xFFFFFFFF)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.2.dp, borderColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 14.dp,
                            shape = RoundedCornerShape(16.dp),
                            ambientColor = Color.Black.copy(alpha = 0.45f),
                            spotColor = glowColor
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = badgeColor,
                            border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
                            modifier = Modifier.size(30.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = variantIcon,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(11.dp))

                        Text(
                            text = toast.message,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp
                            ),
                            color = Color(0xFFFFFFFF),
                            modifier = Modifier.weight(1f)
                        )

                        if (toast.actionLabel != null) {
                            TextButton(
                                onClick = {
                                    toast.onAction?.invoke()
                                    onDismiss()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text(
                                    text = toast.actionLabel,
                                    color = accentColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        )
    }
}

private data class Tuple6<A, B, C, D, E, F>(
    val a: A,
    val b: B,
    val c: C,
    val d: D,
    val e: E,
    val f: F
)
