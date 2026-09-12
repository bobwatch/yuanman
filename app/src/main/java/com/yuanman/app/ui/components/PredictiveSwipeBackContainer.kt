package com.yuanman.app.ui.components

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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ln

/**
 * 边缘常量（与系统 BackEvent.swipeEdge 取值一致：0 = 左缘，1 = 右缘）
 */
private const val EDGE_LEFT = 0
private const val EDGE_RIGHT = 1

/**
 * 分层预测性返回容器 (PredictiveSwipeBackContainer)
 *
 * 核心交互特性与物理边界：
 * 1. 缩放边界 (Min Scale Boundary)：
 *    设定刚性保底极限 minScale = 0.90f。无论手指滑得多远，卡片最多缩小 10%，绝不无限缩小成小方块。
 * 2. 位移边界 (Max Translation Boundary)：
 *    在手指按住滑动（Tracking）阶段，卡片向右平移具有绝对上限（最大仅平移屏幕宽度的 22%，约 80~90dp）。
 *    保证滑动过程中卡片始终占据屏幕 78% 以上的主体视野，上一级页面仅在外围边框与圆角处优雅透视，绝不提前完全露底。
 * 3. 边界阻尼与松手决策 (Rubber-banding & Release Commitment)：
 *    滑动到达边界后施加物理阻尼；只有在手指真正离开屏幕（Release）时：
 *    - 若达到返回阈值 -> 启动加速退场动画，卡片顺滑飞出屏幕，完全呈现上一级页面；
 *    - 若未达到阈值 -> 弹性回弹原位（恢复 1.0 满屏）。
 * 4. 双通道手势支持 (Dual Gesture Pipeline)：
 *    完美适配系统原生全面屏手势 (PredictiveBackHandler) 与应用内左边缘侧滑手势 (In-App Edge Swipe)。
 * 5. 方向感知 (Edge-Aware Direction)：
 *    Android 13+ 全面屏返回可从屏幕左、右两缘触发。卡片退场 / 跟手位移方向随手势侧镜像：
 *    左缘手势 → 卡片向右滑出（从左缘揭开下一层）；右缘手势 → 卡片向左滑出（从右缘揭开）。
 *    手势侧经 [onEdgeChange] 上报，供外层把底座/下层页面的缩放锚点同步到手势侧。
 * 6. 全场景适配 (一级 Tab 退出与二级页面返回同构)：
 *    通过 hasEnterAnimation 参数区分首屏与推入页面，退出后自动重置状态，保证后台恢复全屏无跳动。
 */
/**
 * 供二级页面左上角返回按钮获取父级容器的平滑退场触发器。
 * 替代直接 removeLast() 造成的瞬间黑底/闪屏，统一走 200ms 加速滑出动效。
 */
val LocalPredictiveBackExit = compositionLocalOf<(() -> Unit)?> { null }

@Composable
fun PredictiveSwipeBackContainer(
    isTop: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    hasEnterAnimation: Boolean = true,
    onProgressChange: (Float) -> Unit = {},
    onEdgeChange: (Int) -> Unit = {},
    edgeTouchWidth: Dp = 72.dp,
    minScale: Float = 0.90f,
    maxTrackingTranslationRatio: Float = 0.22f,
    content: @Composable (requestBack: () -> Unit) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val dragProgress = remember { Animatable(0f) }
    val enterAnim = remember { Animatable(if (hasEnterAnimation) 1f else 0f) }
    val exitAnim = remember { Animatable(0f) }  // 0f = 正常位置，1f = 加速完全滑出屏幕
    var isExiting by remember { mutableStateOf(false) }

    // 当前手势来源侧（系统右缘手势 = 1）；应用内左缘侧滑恒为左缘
    var gestureEdge by remember { mutableIntStateOf(EDGE_LEFT) }

    /** 手势侧切换时更新镜像方向并上报外层 */
    fun updateEdge(edge: Int) {
        if (edge != gestureEdge) {
            gestureEdge = edge
            onEdgeChange(edge)
        }
    }

    val requestBack: () -> Unit = {
        if (!isExiting) {
            isExiting = true
            updateEdge(EDGE_LEFT)
            coroutineScope.launch {
                exitAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                )
                onDismiss()
                exitAnim.snapTo(0f)
                dragProgress.snapTo(0f)
                isExiting = false
            }
        }
    }

    // 页面初次进入时平滑从右侧滑入（仅当启用入场动画时执行）
    if (hasEnterAnimation) {
        LaunchedEffect(Unit) {
            enterAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
            )
        }
    }

    // 计算综合进度：退出动画阶段优先使用 1f，否则使用手势拖拽进度
    val effectiveProgress = if (isExiting) {
        maxOf(dragProgress.value, exitAnim.value).coerceIn(0f, 1f)
    } else {
        dragProgress.value.coerceIn(0f, 1f)
    }

    // 实时联动底座层缩放与遮罩淡出
    LaunchedEffect(effectiveProgress) {
        onProgressChange(effectiveProgress)
    }

    // 1. 系统全面屏侧滑手势监听
    PredictiveBackHandler(enabled = isTop && !isExiting) { progressFlow ->
        try {
            progressFlow.collect { backEvent ->
                // 系统手势滑动中：严格约束在 [0, 1] 边界内，驱动卡片微缩
                dragProgress.snapTo(backEvent.progress.coerceIn(0f, 1f))
                updateEdge(backEvent.swipeEdge)
            }
            // 手势释放确认返回：触发退场动画，卡片滑出屏幕
            isExiting = true
            exitAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
            )
            onDismiss()
            // 状态重置（便于前台唤醒时恢复完整视界）
            coroutineScope.launch {
                exitAnim.snapTo(0f)
                dragProgress.snapTo(0f)
                isExiting = false
            }
        } catch (e: CancellationException) {
            // 手势取消：平滑回弹原位
            isExiting = false
            dragProgress.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val edgeTouchWidthPx = with(density) { edgeTouchWidth.toPx() }

        // 2. 应用内左边缘侧滑手势监听
        val gestureModifier = if (isTop && !isExiting) {
            Modifier.pointerInput(screenWidthPx, edgeTouchWidthPx) {
                awaitEachGesture {
                    // Initial pass：在子组件（如 Pager 或 List）消费前检测触控起点
                    val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
                    if (down.position.x > edgeTouchWidthPx) {
                        return@awaitEachGesture
                    }

                    var totalDragX = 0f
                    var isDragStarted = false
                    val touchSlop = viewConfiguration.touchSlop
                    val pointerId = down.id
                    val velocityTracker = VelocityTracker()
                    velocityTracker.addPosition(down.uptimeMillis, down.position)

                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break

                        if (!change.pressed) {
                            // 手指抬起（松手）时刻：唯一决定去留的时机！
                            if (isDragStarted) {
                                val velocityX = velocityTracker.calculateVelocity().x
                                val currentP = dragProgress.value
                                // 达到确认返回阈值（滑动进度超过 0.35 且未向左倒划，或快速向右甩动）
                                val shouldCommit = (currentP >= 0.35f && velocityX > -200f) || velocityX > 1000f

                                coroutineScope.launch {
                                    if (shouldCommit) {
                                        // 达到边界且松手确认：卡片冲破边界滑出屏幕
                                        isExiting = true
                                        exitAnim.animateTo(
                                            targetValue = 1f,
                                            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                                        )
                                        onDismiss()
                                        // 状态重置
                                        exitAnim.snapTo(0f)
                                        dragProgress.snapTo(0f)
                                        isExiting = false
                                    } else {
                                        // 未达到阈值：平滑弹力回弹原位（恢复 1.0 满屏）
                                        dragProgress.animateTo(
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
                            // 水平右滑判定（水平距离大于垂直距离且超过 touchSlop）
                            if (totalDragX > touchSlop && totalDragX > abs(change.position.y - down.position.y) * 1.2f) {
                                isDragStarted = true
                                change.consume()
                                // 应用内侧滑仅监听左缘，方向恒为左缘
                                updateEdge(EDGE_LEFT)
                            }
                        } else {
                            totalDragX += dragAmountX
                            change.consume()

                            // 计算滑动比例：滑动基准距离设为屏幕宽度的 55%
                            val baseDragRatio = (totalDragX / (screenWidthPx * 0.55f)).coerceAtLeast(0f)
                            // 到达 1.0 边界后施加极强阻尼（Rubber-banding），最多溢出 0.1，无法继续大幅拉动
                            val p = if (baseDragRatio <= 1.0f) {
                                baseDragRatio
                            } else {
                                1.0f + (ln(baseDragRatio) * 0.12f).coerceAtMost(0.10f)
                            }

                            coroutineScope.launch {
                                dragProgress.snapTo(p)
                            }
                        }
                    }
                }
            }
        } else {
            Modifier
        }

        // ================= 严格的物理边界约束 =================
        val boundedProgress = effectiveProgress.coerceIn(0f, 1f)

        // 1. 缩放边界：严格锁定在 [minScale, 1.0f]，默认下限 0.90f，绝不无限变小
        val currentScale = (1f - (boundedProgress * (1f - minScale))).coerceIn(minScale, 1.0f)

        // 2. 圆角渐变：从 0dp 随滑动过渡到 24dp
        val currentCornerRadius = (boundedProgress * 24f).dp

        // 3. 位移边界：
        // - 手指按住滑动期间（Tracking）：位移死死限制在屏幕宽度的 22% 刚性上限（约 80~90dp），卡片绝不滑飞出屏幕
        // - 手指松开确认退场时（Exiting）：通过 exitAnim 将卡片顺滑加速推至屏幕外
        // - 方向镜像：系统返回手势来自右缘时（gestureEdge == RIGHT），卡片朝左滑出（从右缘揭开下一层）
        val edgeMirror = if (gestureEdge == EDGE_RIGHT) -1f else 1f
        val maxTrackingTranslationPx = screenWidthPx * maxTrackingTranslationRatio
        val trackingTranslationX = (dragProgress.value.coerceIn(0f, 1.1f) * maxTrackingTranslationPx).coerceIn(0f, maxTrackingTranslationPx * 1.05f)
        val exitTranslationX = exitAnim.value * (screenWidthPx - trackingTranslationX + with(density) { 60.dp.toPx() })
        val enterTranslationX = enterAnim.value * screenWidthPx
        // 入场恒从右缘滑入（推入语义）；跟手与退场方向随手势侧镜像
        val currentTranslationX = enterTranslationX + edgeMirror * (trackingTranslationX + exitTranslationX)

        // 4. 卡片立体阴影：0dp -> 16dp
        val currentElevation = (boundedProgress * 16f).dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(gestureModifier)
                .graphicsLayer {
                    scaleX = currentScale
                    scaleY = currentScale
                    translationX = currentTranslationX
                    clip = boundedProgress > 0.001f || enterAnim.value > 0.001f || isExiting
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
            CompositionLocalProvider(LocalPredictiveBackExit provides requestBack) {
                content(requestBack)
            }
        }
    }
}

@Composable
fun PredictiveSwipeBackContainer(
    isTop: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    hasEnterAnimation: Boolean = true,
    onProgressChange: (Float) -> Unit = {},
    onEdgeChange: (Int) -> Unit = {},
    edgeTouchWidth: Dp = 72.dp,
    minScale: Float = 0.90f,
    maxTrackingTranslationRatio: Float = 0.22f,
    content: @Composable () -> Unit
) {
    PredictiveSwipeBackContainer(
        isTop = isTop,
        onDismiss = onDismiss,
        modifier = modifier,
        hasEnterAnimation = hasEnterAnimation,
        onProgressChange = onProgressChange,
        onEdgeChange = onEdgeChange,
        edgeTouchWidth = edgeTouchWidth,
        minScale = minScale,
        maxTrackingTranslationRatio = maxTrackingTranslationRatio
    ) { _ ->
        content()
    }
}
