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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    fun info(message: String) = show(message, ToastType.INFO)

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
 * 🌟 顶部全局 Toast 宿主容器（绝不被底部 TabBar 遮挡，优雅自顶部自然落下）
 */
@Composable
fun TopToastHost(
    state: ToastHostState,
    modifier: Modifier = Modifier
) {
    val toasts by state.toasts.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopToastItem(
    toast: ToastData,
    onDismiss: () -> Unit,
    onRemoved: () -> Unit
) {
    val container = MaterialTheme.colorScheme.inverseSurface
    val content = MaterialTheme.colorScheme.inverseOnSurface

    val primaryGreen = Color(0xFF059669)
    val expenseRed = Color(0xFFEF4444)
    val warningOrange = Color(0xFFF59E0B)

    val (variantColor, variantIcon) = when (toast.type) {
        ToastType.SUCCESS -> primaryGreen to Icons.Filled.CheckCircle
        ToastType.ERROR -> expenseRed to Icons.Filled.ErrorOutline
        ToastType.WARNING -> warningOrange to Icons.Filled.Warning
        ToastType.INFO -> MaterialTheme.colorScheme.primary to Icons.Filled.Info
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
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(end = 14.dp).size(18.dp)
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
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = variantColor.copy(alpha = 0.16f),
                            modifier = Modifier.size(30.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = variantIcon,
                                    contentDescription = null,
                                    tint = variantColor,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text = toast.message,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.5.sp
                            ),
                            color = content,
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
                                    color = variantColor,
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
