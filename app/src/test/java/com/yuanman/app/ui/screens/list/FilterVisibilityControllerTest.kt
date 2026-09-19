package com.yuanman.app.ui.screens.list

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 筛选区随滚动显隐的判定护栏。
 *
 * 符号约定（实测确定，别再翻）：**负值 = 手指上滑**（往下翻看更多账单）→ 收起；
 * 正值 = 手指下滑（往回翻看）→ 展开。这块逻辑上一版被整体移除过（滚动时高度反复伸缩
 * 导致抖动卡顿），所以把「阈值 + 方向锁 + 动画冻结」三条纪律固化下来，避免以后改坏。
 */
class FilterVisibilityControllerTest {

    private val threshold = 56f
    private fun controller() = FilterVisibilityController(threshold)

    /** 手指上滑（往下翻看更多账单） */
    private fun swipeUp(px: Float) = -px

    /** 手指下滑（往回翻看） */
    private fun swipeDown(px: Float) = px

    @Test
    fun `未达阈值不切换`() {
        val c = controller()
        assertNull(c.onScroll(swipeUp(30f), canJudge = true))
        assertNull(c.onScroll(swipeUp(25f), canJudge = true)) // 累计 55 < 56
    }

    @Test
    fun `手指上滑累计到阈值收起筛选区`() {
        val c = controller()
        assertNull(c.onScroll(swipeUp(30f), canJudge = true))
        assertEquals(false, c.onScroll(swipeUp(26f), canJudge = true)) // 累计 56 → 收起
    }

    @Test
    fun `手指下滑累计到阈值展开筛选区`() {
        val c = controller()
        assertNull(c.onScroll(swipeDown(30f), canJudge = true))
        assertEquals(true, c.onScroll(swipeDown(30f), canJudge = true)) // 累计 60 → 展开
    }

    @Test
    fun `方向反转时累计清零`() {
        val c = controller()
        assertNull(c.onScroll(swipeUp(40f), canJudge = true))
        assertNull(c.onScroll(swipeDown(40f), canJudge = true)) // 反转清零，只累计到 40
        assertEquals(true, c.onScroll(swipeDown(16f), canJudge = true)) // 再滑够 56 → 展开
    }

    @Test
    fun `动画期间冻结判定且不累计`() {
        val c = controller()
        assertNull(c.onScroll(swipeUp(200f), canJudge = false)) // 收起动画中，滚多少都不算
        assertNull(c.onScroll(swipeUp(55f), canJudge = true)) // 冻结期未累计，这里仍不够阈值
        assertEquals(false, c.onScroll(swipeUp(1f), canJudge = true)) // 凑满 56 → 收起
    }

    @Test
    fun `零滚动量不参与判定`() {
        val c = controller()
        assertNull(c.onScroll(0f, canJudge = true)) // 下拉刷新在顶部时列表不滚动，不能误判
        assertNull(c.onScroll(swipeUp(55f), canJudge = true))
        assertEquals(false, c.onScroll(swipeUp(1f), canJudge = true))
    }

    @Test
    fun `阈值附近来回微抖不会反复切换`() {
        val c = controller()
        // 上滑刚过阈值 → 收起一次
        assertEquals(false, c.onScroll(swipeUp(60f), canJudge = true))
        // 接着手指来回微抖：每个方向都不到阈值，必须一次都不切换
        assertNull(c.onScroll(swipeUp(12f), canJudge = true))
        assertNull(c.onScroll(swipeDown(12f), canJudge = true))
        assertNull(c.onScroll(swipeUp(20f), canJudge = true))
        assertNull(c.onScroll(swipeDown(25f), canJudge = true)) // 反转清零，只累计 25
        // 真正往回滑够阈值了，才展开
        assertEquals(true, c.onScroll(swipeDown(31f), canJudge = true)) // 25 + 31 = 56 → 展开
    }
}
