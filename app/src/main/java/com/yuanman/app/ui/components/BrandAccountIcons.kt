package com.yuanman.app.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 账户品牌图标（v0.0.4.7）：微信支付 / 支付宝 / 银联（云闪付）。
 *
 * 账户图标体系本身是「单色矢量 + 账户主题色着色」，但品牌标识需要固有配色才有辨识度，
 * 因此这三个 key 走独立 Canvas 渲染（BrandAccountIcon）：微信=绿色双气泡、
 * 支付宝=蓝底白「支」、银联=红蓝绿三竖条；其余 key 仍走 CategoryIconHelper 单色矢量。
 * key 与 AddEditAccountSheet 账户图标词表、AccountPresetBootstrap 预置样式同源。
 */

object BrandAccountIcons {
    val KEYS = listOf("wechat", "alipay", "unionpay")

    fun isBrand(key: String): Boolean = key in KEYS
}

private val WechatGreen = Color(0xFF07C160)
private val AlipayBlue = Color(0xFF1677FF)
private val UnionpayRed = Color(0xFFE60012)
private val UnionpayBlue = Color(0xFF00447C)
private val UnionpayGreen = Color(0xFF007B84)

/** 按品牌 key 渲染对应图形；非品牌 key 时为空内容 */
@Composable
fun BrandAccountIcon(
    iconKey: String,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp
) {
    if (!BrandAccountIcons.isBrand(iconKey)) return
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        when (iconKey) {
            "wechat" -> Canvas(modifier = Modifier.fillMaxSize()) {
                val s = minOf(this.size.width, this.size.height)
                drawWechat(s)
            }
            "unionpay" -> Canvas(modifier = Modifier.fillMaxSize()) {
                val s = minOf(this.size.width, this.size.height)
                drawUnionpay(s)
            }
            "alipay" -> Canvas(modifier = Modifier.fillMaxSize()) {
                val s = minOf(this.size.width, this.size.height)
                drawRoundRect(
                    color = AlipayBlue,
                    topLeft = Offset.Zero,
                    size = Size(s, s),
                    cornerRadius = CornerRadius(s * 0.24f, s * 0.24f)
                )
                drawAlipayCharacter(s)
            }
        }
    }
}

/** 微信：主气泡（白“眼睛”）+ 右上小气泡，整体绿色 */
private fun DrawScope.drawWechat(side: Float) {
    val s = side
    // 主气泡
    drawRoundRect(
        color = WechatGreen,
        topLeft = Offset(s * 0.06f, s * 0.12f),
        size = Size(s * 0.60f, s * 0.56f),
        cornerRadius = CornerRadius(s * 0.27f, s * 0.27f)
    )
    // 主气泡小尾巴
    val tail = Path().apply {
        moveTo(s * 0.22f, s * 0.64f)
        lineTo(s * 0.42f, s * 0.64f)
        lineTo(s * 0.30f, s * 0.84f)
        close()
    }
    drawPath(tail, color = WechatGreen)
    // 右上小气泡（叠层）
    drawRoundRect(
        color = WechatGreen,
        topLeft = Offset(s * 0.44f, s * 0.36f),
        size = Size(s * 0.50f, s * 0.38f),
        cornerRadius = CornerRadius(s * 0.19f, s * 0.19f)
    )
    val smallTail = Path().apply {
        moveTo(s * 0.68f, s * 0.70f)
        lineTo(s * 0.52f, s * 0.82f)
        lineTo(s * 0.58f, s * 0.70f)
        close()
    }
    drawPath(smallTail, color = WechatGreen)
    // 主气泡上两个白色“眼睛”
    val eyeColor = Color.White
    drawOval(
        color = eyeColor,
        topLeft = Offset(s * 0.20f, s * 0.30f),
        size = Size(s * 0.09f, s * 0.09f)
    )
    drawOval(
        color = eyeColor,
        topLeft = Offset(s * 0.41f, s * 0.30f),
        size = Size(s * 0.09f, s * 0.09f)
    )
}

/** 银联（云闪付）：红 / 蓝 / 绿 三根圆角竖条（中条最长） */
private fun DrawScope.drawUnionpay(side: Float) {
    val s = side
    fun bar(leftF: Float, topF: Float, widthF: Float, heightF: Float, color: Color) {
        drawRoundRect(
            color = color,
            topLeft = Offset(s * leftF, s * topF),
            size = Size(s * widthF, s * heightF),
            cornerRadius = CornerRadius(s * 0.07f, s * 0.07f)
        )
    }
    bar(0.13f, 0.22f, 0.19f, 0.55f, UnionpayRed)      // 左：红
    bar(0.40f, 0.10f, 0.19f, 0.80f, UnionpayBlue)     // 中：蓝（最长）
    bar(0.68f, 0.18f, 0.19f, 0.64f, UnionpayGreen)    // 右：绿
}

/** 支付宝字标：白色加粗「支」，垂直/水平居中 */
private fun DrawScope.drawAlipayCharacter(side: Float) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textSize = side * 0.60f
    }
    val fm = paint.fontMetrics
    val baseline = side / 2f - (fm.ascent + fm.descent) / 2f
    drawContext.canvas.nativeCanvas.drawText("支", side / 2f, baseline, paint)
}
