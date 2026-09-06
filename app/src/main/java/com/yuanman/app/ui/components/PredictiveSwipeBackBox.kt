package com.yuanman.app.ui.components

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 通用全面屏预测性返回与 Telegram 风格缩放手势容器。
 *
 * 核心特性：
 * 1. 原生预测性返回（Android 13/14+ Predictive Back）：监听系统全面屏返回手势事件流，实时跟手缩放。
 * 2. 边缘手势兜底（In-App Edge Swipe）：在屏幕左侧边缘 (<= 32dp) 向右拖动时同样支持缩放返回，兼容老系统与虚拟按键设备。
 * 3. 防误触设计：严格限制应用内手势触控起点必须在左边缘，不干扰屏幕中部的 Pager、列表或图表滑动。
 * 4. 物理回弹：未达阈值松手时 Spring 弹性恢复；超过阈值松手加速滑出并触发返回。
 */
@Composable
fun PredictiveSwipeBackBox(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    edgeSwipeEnabled: Boolean = true,
    minScale: Float = 0.88f,
    maxCornerRadius: Dp = 28.dp,
    commitThreshold: Float = 0.32f,
    edgeTouchWidth: Dp = 32.dp,
    content: @Composable () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var swipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var isHandlingGesture by remember { mutableStateOf(false) }

    // 1. Android 13/14+ 系统级全面屏返回手势 (Predictive Back)
    PredictiveBackHandler(enabled = enabled) { progressFlow ->
        try {
            isHandlingGesture = true
            progressFlow.collect { backEvent ->
                swipeEdge = backEvent.swipeEdge
                progress.snapTo(backEvent.progress)
            }
            // 用户完成手势（确认返回）：平滑加速滑出屏幕并回调 onBack
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
            )
            onBack()
        } catch (e: CancellationException) {
            // 用户放弃手势（中途滑回取消）：物理弹性回弹复原
            progress.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        } finally {
            isHandlingGesture = false
        }
    }

    // 2. 布局与图形渲染层
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val density = LocalDensity.current
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val edgeTouchWidthPx = with(density) { edgeTouchWidth.toPx() }

        // 应用内边缘拖拽手势检测 (用于兜底或全屏边缘手势)
        val gestureModifier = if (edgeSwipeEnabled && enabled && !isHandlingGesture) {
            Modifier.pointerInput(screenWidthPx, edgeTouchWidthPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // 仅当触控起始点在屏幕最左侧边缘区域内才捕获，避免与内部 HorizontalPager 冲突
                    if (down.position.x > edgeTouchWidthPx) {
                        return@awaitEachGesture
                    }

                    val velocityTracker = VelocityTracker()
                    velocityTracker.addPosition(down.uptimeMillis, down.position)

                    var totalDragX = 0f
                    var isDragStarted = false
                    val touchSlop = viewConfiguration.touchSlop
                    val pointerId = down.id

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break

                        if (!change.pressed) {
                            // 手指抬起 (Touch UP)
                            if (isDragStarted) {
                                val velocityX = velocityTracker.calculateVelocity().x
                                val shouldCommit = progress.value >= commitThreshold || velocityX > 1200f

                                coroutineScope.launch {
                                    if (shouldCommit) {
                                        progress.animateTo(
                                            targetValue = 1f,
                                            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                                        )
                                        onBack()
                                    } else {
                                        progress.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessLow
                                            )
                                        )
                                    }
                                }
                            }
                            break
                        }

                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        val dragAmountX = change.position.x - change.previousPosition.x

                        if (!isDragStarted) {
                            totalDragX += dragAmountX
                            // 必须是向右且水平优势的滑动才激活
                            if (totalDragX > touchSlop) {
                                if (totalDragX > abs(change.position.y - down.position.y)) {
                                    isDragStarted = true
                                    swipeEdge = BackEventCompat.EDGE_LEFT
                                    change.consume()
                                } else {
                                    // 纵向滑动为主，放弃捕获
                                    break
                                }
                            }
                        } else {
                            totalDragX += dragAmountX
                            change.consume()
                            val currentProgress = (totalDragX / (screenWidthPx * 0.85f)).coerceIn(0f, 1f)
                            coroutineScope.launch {
                                progress.snapTo(currentProgress)
                            }
                        }
                    }
                }
            }
        } else {
            Modifier
        }

        val currentProgress = progress.value
        val currentScale = 1f - currentProgress * (1f - minScale)
        val currentCornerRadius = (currentProgress * maxCornerRadius.value).dp
        val maxTranslationXPx = screenWidthPx * 0.35f
        val currentTranslationXPx = if (swipeEdge == BackEventCompat.EDGE_RIGHT) {
            -currentProgress * maxTranslationXPx
        } else {
            currentProgress * maxTranslationXPx
        }
        val currentElevation = (currentProgress * 20f).dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(gestureModifier)
                .graphicsLayer {
                    scaleX = currentScale
                    scaleY = currentScale
                    translationX = currentTranslationXPx
                    clip = currentProgress > 0.001f
                    shape = RoundedCornerShape(currentCornerRadius)
                }
                .shadow(
                    elevation = currentElevation,
                    shape = RoundedCornerShape(currentCornerRadius),
                    clip = false
                )
                .background(
                    color = MaterialTheme.colorScheme.background,
                    shape = RoundedCornerShape(currentCornerRadius)
                )
        ) {
            content()
        }
    }
}
