package com.yuanman.app.data.model

import com.yuanman.app.data.local.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 统一图标色板的护栏测试。
 *
 * 这套约束是「色彩搭配方案」的验收口径：任何新增/调整色值都必须同时满足
 * ①浅色可读 ②深色可读 ③同屏可分辨，否则直接失败，避免又退回「好看但看不清」。
 */
class IconPaletteTest {

    @Test
    fun `每个预设色在白底上都达到正文对比度`() {
        IconPalette.PRESET_COLORS.forEach { color ->
            val ratio = IconPalette.contrastRatio(color, IconPalette.LIGHT_SURFACE)
            assertTrue(
                "色值 ${hex(color)} 在白底上对比度仅 ${"%.2f".format(ratio)}，低于 4.5:1（选中分类名会用该色作文字色）",
                ratio >= IconPalette.MIN_TEXT_CONTRAST
            )
        }
    }

    @Test
    fun `每个预设色在深色卡片提亮后仍达到正文对比度`() {
        IconPalette.PRESET_COLORS.forEach { color ->
            val adapted = IconPalette.adaptForDarkSurface(color)
            val ratio = IconPalette.contrastRatio(adapted, IconPalette.DARK_SURFACE)
            assertTrue(
                "色值 ${hex(color)} 提亮为 ${hex(adapted)} 后对比度仅 ${"%.2f".format(ratio)}，低于 4.5:1",
                ratio >= IconPalette.MIN_TEXT_CONTRAST
            )
        }
    }

    @Test
    fun `图标图形在自绘底衬圆上满足非文字对比度`() {
        IconPalette.PRESET_COLORS.forEach { color ->
            val lightBg = IconPalette.blend(color, IconPalette.LIGHT_SURFACE, IconPalette.LIGHT_TINT_ALPHA.toDouble())
            val lightRatio = IconPalette.contrastRatio(color, lightBg)
            assertTrue(
                "色值 ${hex(color)} 在浅色底衬圆上对比度仅 ${"%.2f".format(lightRatio)}，低于 3:1",
                lightRatio >= IconPalette.MIN_GRAPHIC_CONTRAST
            )

            val adapted = IconPalette.adaptForDarkSurface(color)
            val darkBg = IconPalette.blend(adapted, IconPalette.DARK_SURFACE, IconPalette.DARK_TINT_ALPHA.toDouble())
            val darkRatio = IconPalette.contrastRatio(adapted, darkBg)
            assertTrue(
                "色值 ${hex(color)} 在深色底衬圆上对比度仅 ${"%.2f".format(darkRatio)}，低于 3:1",
                darkRatio >= IconPalette.MIN_GRAPHIC_CONTRAST
            )
        }
    }

    @Test
    fun `深色适配是提亮且不塌陷色间层次`() {
        val adapted = IconPalette.PRESET_COLORS.map { IconPalette.adaptForDarkSurface(it) }

        IconPalette.PRESET_COLORS.forEachIndexed { index, color ->
            val lifted = IconPalette.relativeLuminance(adapted[index])
            val original = IconPalette.relativeLuminance(color)
            assertTrue(
                "深色适配把 ${hex(color)} 压暗了（${"%.3f".format(original)} -> ${"%.3f".format(lifted)}）",
                lifted >= original - 1e-6
            )
        }

        // 旧实现把所有色统一压到同一亮度，色与色之间的明暗关系被抹平；
        // 这里要求每个色都提升到各自不同的亮度（暗色模式仍有层次）。
        assertEquals(
            "深色适配后出现亮度塌陷，色间层次被抹平",
            adapted.size,
            adapted.distinct().size
        )
    }

    @Test
    fun `色板内任意两色都能分辨`() {
        val colors = IconPalette.PRESET_COLORS
        for (i in colors.indices) {
            for (j in i + 1 until colors.size) {
                val delta = labDistance(colors[i], colors[j])
                assertTrue(
                    "${hex(colors[i])} 与 ${hex(colors[j])} 色差仅 ${"%.1f".format(delta)}，同屏会撞色",
                    delta >= 14.0
                )
            }
        }
    }

    @Test
    fun `色板中相邻展示位色差更大`() {
        val colors = IconPalette.PRESET_COLORS
        for (i in 0 until colors.size - 1) {
            val delta = labDistance(colors[i], colors[i + 1])
            assertTrue(
                "相邻色位 ${hex(colors[i])} 与 ${hex(colors[i + 1])} 色差仅 ${"%.1f".format(delta)}",
                delta >= 22.0
            )
        }
    }

    @Test
    fun `色板无重复且覆盖全部默认分类`() {
        assertEquals(IconPalette.PRESET_COLORS.size, IconPalette.PRESET_COLORS.distinct().size)

        val defaults = AppDatabase.getDefaultCategories()
        assertTrue("默认分类为空", defaults.isNotEmpty())
        defaults.forEach { category ->
            assertTrue(
                "默认分类「${category.name}」用的是 ${hex(category.colorHex)}，不在统一色板内",
                category.colorHex in IconPalette.PRESET_COLORS
            )
        }
    }

    @Test
    fun `同页默认分类颜色互不重复`() {
        AppDatabase.getDefaultCategories().groupBy { it.type }.forEach { (type, categories) ->
            val colors = categories.map { it.colorHex }
            assertEquals(
                "$type 页存在重复主题色：${colors.groupingBy { it }.eachCount().filter { it.value > 1 }}",
                colors.size,
                colors.distinct().size
            )
        }
    }

    /** CIELAB 色差（ΔE76）：比 RGB 距离更贴近人眼「是否撞色」的判定 */
    private fun labDistance(a: Long, b: Long): Double {
        val (l1, a1, b1) = toLab(a)
        val (l2, a2, b2) = toLab(b)
        return sqrt((l1 - l2).pow(2) + (a1 - a2).pow(2) + (b1 - b2).pow(2))
    }

    private fun toLab(argb: Long): Triple<Double, Double, Double> {
        fun linearize(channel: Long): Double {
            val c = channel / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }

        val r = linearize((argb shr 16) and 0xFF)
        val g = linearize((argb shr 8) and 0xFF)
        val b = linearize(argb and 0xFF)

        val x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
        val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
        val z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883

        fun pivot(t: Double) = if (t > 0.008856) t.pow(1.0 / 3.0) else 7.787 * t + 16.0 / 116.0
        val fx = pivot(x)
        val fy = pivot(y)
        val fz = pivot(z)
        return Triple(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
    }

    private fun hex(color: Long) = "#%06X".format(color and 0xFFFFFF)
}
