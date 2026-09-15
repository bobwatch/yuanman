package com.yuanman.app.data.model

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 全应用统一的图标主题色板。
 *
 * 分类图标、账户图标、攒钱计划图标共用同一套色，避免各处各写一份（历史上账户弹层还留着
 * 上一版 Material 500 色板，同一屏里两套色系）。
 *
 * 三条硬约束，由 `IconPaletteTest` 锁定：
 *  1. 每个色值在白底上对比度 ≥ 4.5:1。该色同时是图标着色与「选中分类名」的文字色，
 *     低于 4.5:1 在浅色模式下发灰（旧色板 18 色里有 11 色不达标，如 #FB923C 仅 2.26:1）。
 *  2. 同一页（支出 18 类 / 收入 6 类）内任意两色在 CIELAB 明显可分，网格中相邻格位更远，
 *     避免「交通出行 / 教育培训」这类同色系撞脸。
 *  3. 暗色模式统一走 [adaptForDarkSurface]：按明度单调提亮到深色卡片上 ≥ 4.5:1。
 *     单调映射很重要——旧实现把所有色压到同一亮度，色与色之间的明暗关系被抹平。
 *
 * 纯 Kotlin 实现（不依赖 android.graphics / Compose），便于 JVM 单测直接校验。
 */
object IconPalette {

    /** 浅色模式卡片底衬色，同 Theme.kt LightColorScheme.surface */
    const val LIGHT_SURFACE = 0xFFFFFFFFL

    /** 深色模式卡片底衬色，同 Theme.kt DarkColorScheme.surface */
    const val DARK_SURFACE = 0xFF141922L

    /** 文字色 / 图标着色要求的最小对比度（WCAG AA 正文） */
    const val MIN_TEXT_CONTRAST = 4.5

    /** 图形元素要求的最小对比度（WCAG AA 非文字） */
    const val MIN_GRAPHIC_CONTRAST = 3.0

    /** 图标底衬圆的不透明度（浅色 / 深色），图标本身都画在这个底衬之上 */
    const val LIGHT_TINT_ALPHA = 0.15f
    const val DARK_TINT_ALPHA = 0.22f

    /**
     * 语义色：既默认分类的主题色，也是可选色板成员，一处定义两处复用，
     * 避免默认分类里散落魔法色值。
     */
    const val FOOD = 0xFFCC3B0FL       // 暖橙红 —— 餐饮美食
    const val TRAFFIC = 0xFF1762CDL    // 赤诚蓝 —— 交通出行
    const val CAR = 0xFF0F687DL        // 深海青 —— 爱车养车
    const val UTILITY = 0xFF128564L    // 幽谷松石 —— 充值缴费
    const val APPAREL = 0xFFCE1A74L    // 玫红 —— 服饰装扮
    const val HOME = 0xFF916848L       // 暖木棕 —— 家居家装
    const val DIGITAL = 0xFF795FE1L    // 星云紫 —— 数码电器
    const val SPORTS = 0xFF117238L     // 松柏绿 —— 运动户外
    const val BEAUTY = 0xFFC130CCL     // 兰紫 —— 美容美发
    const val BABY = 0xFFD42340L       // 蔷薇红 —— 母婴亲子
    const val HOUSING = 0xFF0E6960L    // 孔雀青 —— 住房物业
    const val TRAVEL = 0xFF157AADL     // 海天蓝 —— 酒店旅游
    const val LEISURE = 0xFF812AD9L    // 幻夜紫 —— 文化休闲
    const val EDUCATION = 0xFF3446D5L  // 靛青 —— 教育培训
    const val MEDICAL = 0xFFDD1313L    // 朱砂红 —— 医疗健康
    const val SERVICE = 0xFF667388L    // 冷灰蓝 —— 生活服务
    const val SOCIAL = 0xFF895C0FL     // 赤金 —— 人情往来
    const val INVEST = 0xFF218224L     // 财富绿 —— 投资理财

    /** 可选主题色（18 色），顺序即「选择主题色」里的展示顺序 */
    val PRESET_COLORS: List<Long> = listOf(
        FOOD, TRAFFIC, CAR, UTILITY, APPAREL, HOME, DIGITAL, SPORTS, BEAUTY,
        BABY, HOUSING, TRAVEL, LEISURE, EDUCATION, MEDICAL, SERVICE, SOCIAL, INVEST
    )

    /** 账户/计划表单里新建时的默认主题色（幽谷松石，与主色 emerald 同族且达标） */
    const val DEFAULT_COLOR = UTILITY

    /** WCAG 相对亮度 */
    fun relativeLuminance(argb: Long): Double {
        fun linearize(channel: Double): Double =
            if (channel <= 0.03928) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)

        val r = linearize(((argb shr 16) and 0xFF).toDouble() / 255.0)
        val g = linearize(((argb shr 8) and 0xFF).toDouble() / 255.0)
        val b = linearize((argb and 0xFF).toDouble() / 255.0)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /** WCAG 对比度，取值范围 1.0 ~ 21.0 */
    fun contrastRatio(a: Long, b: Long): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** 按不透明度把 [foreground] 叠到 [background] 上，用于计算图标底衬的真实底色 */
    fun blend(foreground: Long, background: Long, alpha: Double): Long {
        fun mix(shift: Int): Long {
            val f = (foreground shr shift) and 0xFF
            val b = (background shr shift) and 0xFF
            return (f * alpha + b * (1 - alpha)).roundToInt().coerceIn(0, 255).toLong()
        }
        return 0xFF000000L or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    /**
     * 深色模式适配：把 [argb] 提亮到在深色卡片 [surface] 上对比度 ≥ 4.5:1。
     *
     * 提亮走 `l' = max(l, 0.48 + 0.32 * l)` —— 关于 l 单调不减，因此色与色之间的
     * 相对明暗关系完全保留（深色模式下的层次感不会塌掉），仅在个别色仍不达标时
     * 才二分搜索补足到阈值。色相不变，饱和度过高的会轻微收敛，避免霓虹感。
     */
    fun adaptForDarkSurface(argb: Long, surface: Long = DARK_SURFACE): Long {
        // 目标留 0.05 余量：提亮结果与原始色值都会经过 8bit 量化，贴着 4.5 容易掉到线下
        val target = MIN_TEXT_CONTRAST + 0.05
        if (contrastRatio(argb, surface) >= target) return argb

        val hsl = toHsl(argb)
        val saturation = min(hsl[1], 0.90)
        val lifted = max(hsl[2], 0.48 + 0.32 * hsl[2])
        val liftedColor = fromHsl(hsl[0], saturation, lifted)
        if (contrastRatio(liftedColor, surface) >= target) return liftedColor

        var low = lifted
        var high = 1.0
        repeat(24) {
            val mid = (low + high) / 2
            if (contrastRatio(fromHsl(hsl[0], saturation, mid), surface) >= target) {
                high = mid
            } else {
                low = mid
            }
        }
        return fromHsl(hsl[0], saturation, high)
    }

    /** RGB(0xRRGGBB) -> HSL，h/s/l 均为 0..1 */
    private fun toHsl(argb: Long): DoubleArray {
        val r = ((argb shr 16) and 0xFF).toDouble() / 255.0
        val g = ((argb shr 8) and 0xFF).toDouble() / 255.0
        val b = (argb and 0xFF).toDouble() / 255.0

        val maxC = maxOf(r, g, b)
        val minC = minOf(r, g, b)
        val l = (maxC + minC) / 2.0
        val delta = maxC - minC
        if (delta == 0.0) return doubleArrayOf(0.0, 0.0, l)

        val s = if (l > 0.5) delta / (2.0 - maxC - minC) else delta / (maxC + minC)
        val h = when (maxC) {
            r -> (((g - b) / delta) + if (g < b) 6.0 else 0.0) / 6.0
            g -> (((b - r) / delta) + 2.0) / 6.0
            else -> (((r - g) / delta) + 4.0) / 6.0
        }
        return doubleArrayOf(h, s, l)
    }

    /** HSL(0..1) -> RGB(0xFFRRGGBB) */
    private fun fromHsl(h: Double, s: Double, l: Double): Long {
        if (s <= 0.0) {
            val gray = (l * 255).roundToInt().coerceIn(0, 255).toLong()
            return 0xFF000000L or (gray shl 16) or (gray shl 8) or gray
        }
        val q = if (l < 0.5) l * (1 + s) else l + s - l * s
        val p = 2 * l - q
        val r = hue2Rgb(p, q, h + 1.0 / 3.0)
        val g = hue2Rgb(p, q, h)
        val b = hue2Rgb(p, q, h - 1.0 / 3.0)
        return 0xFF000000L or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
    }

    private fun hue2Rgb(p: Double, q: Double, t0: Double): Double {
        var t = t0
        if (t < 0.0) t += 1.0
        if (t > 1.0) t -= 1.0
        return when {
            t < 1.0 / 6.0 -> p + (q - p) * 6 * t
            t < 1.0 / 2.0 -> q
            t < 2.0 / 3.0 -> p + (q - p) * (2.0 / 3.0 - t) * 6
            else -> p
        }
    }

    private fun channel(value: Double): Long =
        (value * 255).roundToInt().coerceIn(0, 255).toLong()
}
