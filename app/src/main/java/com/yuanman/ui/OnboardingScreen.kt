package com.yuanman.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuanman.R
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch

/** 首次启动引导（3 页 HorizontalPager，品牌渐变整屏）。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pages = listOf(
        Triple(Icons.Outlined.Bolt, R.string.onboard_1_title, R.string.onboard_1_desc),
        Triple(Icons.Outlined.TaskAlt, R.string.onboard_2_title, R.string.onboard_2_desc),
        Triple(Icons.Outlined.Lock, R.string.onboard_3_title, R.string.onboard_3_desc)
    )
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .background(brandHeaderBrush())
            // 状态栏/手势条与内容隔离：刘海屏状态栏更高也不顶头，底部按钮不贴手势条
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            val (icon, titleRes, descRes) = pages[page]
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // 翻页视差：页面滑出时内容微微缩小 + 淡出 + 下浮，
                        // 当前页完全回正——滑动不再是「贴纸平移」，有层次感。
                        // 公式与官方一致（currentPage - page + fraction）：滑向
                        // 下一页时 fraction 为正，进入页 offset 从 1 平滑回 0
                        val offset = ((pagerState.currentPage - page) +
                            pagerState.currentPageOffsetFraction)
                            .coerceIn(-1f, 1f)
                        val distance = offset.absoluteValue
                        scaleX = 1f - distance * 0.1f
                        scaleY = 1f - distance * 0.1f
                        alpha = 1f - distance * 0.35f
                        translationY = distance * 28.dp.toPx()
                    },
                contentAlignment = Alignment.Center
            ) {
                // 内容超高（小屏/超大字号）时可滚动，常规高度下居中显示
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                // 半透明白底矢量图标
                Box(
                    Modifier
                        .size(128.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(56.dp)
                    )
                }
                Spacer(Modifier.height(32.dp))
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(descRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center
                )
                }
            }
        }

        // 页码指示点（当前页圆点放大 + 变亮，带过渡动画；点击直接跳页）
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(pages.size) { index ->
                val active = pagerState.currentPage == index
                val dotSize by animateDpAsState(
                    targetValue = if (active) 10.dp else 8.dp,
                    label = "onboardDot"
                )
                val dotAlpha by animateFloatAsState(
                    targetValue = if (active) 1f else 0.35f,
                    label = "onboardDotAlpha"
                )
                val dotLabel = stringResource(
                    R.string.onboard_page_dot,
                    index + 1,
                    pages.size
                )
                // 外层 48dp 触达区承载点击（远超 8dp 圆点本体），内层圆点只做动画视觉；
                // 无障碍读出「第 X 页，共 Y 页」+ 当前页选中态
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .semantics {
                            contentDescription = dotLabel
                            selected = active
                        }
                        .clickable {
                            scope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(dotSize)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = dotAlpha))
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        val isLast = pagerState.currentPage == pages.size - 1
        Button(
            onClick = {
                if (isLast) {
                    onFinish()
                } else {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                stringResource(
                    if (isLast) R.string.onboard_start else R.string.onboard_next
                ),
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }
        if (!isLast) {
            TextButton(
                onClick = onFinish,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.onboard_skip), color = Color.White)
            }
        }
    }
}
