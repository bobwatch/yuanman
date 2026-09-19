package com.yuanman.app.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 核对码是用户唯一能人工发现中间人的手段：两端必须得到同一个 6 位码，
 * 不同共享密钥必须得到不同码，且始终是 6 位数字。
 */
class SyncVerificationCodeTest {

    private val secretA = ByteArray(32) { it.toByte() }
    private val secretB = ByteArray(32) { (it + 1).toByte() }
    private val deviceOne = "11111111-1111-1111-1111-111111111111"
    private val deviceTwo = "22222222-2222-2222-2222-222222222222"

    @Test
    fun `双方设备号顺序不同也得到相同核对码`() {
        val fromFirst = computeVerificationCode(secretA, deviceOne, deviceTwo)
        val fromSecond = computeVerificationCode(secretA, deviceTwo, deviceOne)
        assertEquals(fromFirst, fromSecond)
    }

    @Test
    fun `不同共享密钥得到不同核对码`() {
        assertNotEquals(
            computeVerificationCode(secretA, deviceOne, deviceTwo),
            computeVerificationCode(secretB, deviceOne, deviceTwo)
        )
    }

    @Test
    fun `核对码固定为六位数字`() {
        repeat(64) { index ->
            val code = computeVerificationCode(ByteArray(32) { (it * index + 7).toByte() }, deviceOne, deviceTwo)
            assertEquals(6, code.length)
            assertTrue("非数字核对码: $code", code.all { it.isDigit() })
        }
    }
}
