package com.yuanman.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyUtilsTest {

    @Test
    fun testCentsToYuanString() {
        assertEquals("12.34", MoneyUtils.centsToYuanString(1234L))
        assertEquals("10.00", MoneyUtils.centsToYuanString(1000L))
        assertEquals("0.50", MoneyUtils.centsToYuanString(50L))
        assertEquals("0.05", MoneyUtils.centsToYuanString(5L))
        assertEquals("0.00", MoneyUtils.centsToYuanString(0L))
    }

    @Test
    fun testParseYuanToCents() {
        assertEquals(1234L, MoneyUtils.parseYuanToCents("12.34"))
        assertEquals(1000L, MoneyUtils.parseYuanToCents("10"))
        assertEquals(1000L, MoneyUtils.parseYuanToCents("10.0"))
        assertEquals(1000L, MoneyUtils.parseYuanToCents("10.00"))
        assertEquals(50L, MoneyUtils.parseYuanToCents("0.5"))
        assertEquals(5L, MoneyUtils.parseYuanToCents("0.05"))
        assertEquals(0L, MoneyUtils.parseYuanToCents("0"))
        assertEquals(0L, MoneyUtils.parseYuanToCents("-10"))
        assertEquals(0L, MoneyUtils.parseYuanToCents("abc"))
    }

    @Test
    fun testIsValidAmountInput() {
        assertTrue(MoneyUtils.isValidAmountInput("12.34"))
        assertTrue(MoneyUtils.isValidAmountInput("100"))
        assertTrue(MoneyUtils.isValidAmountInput("0.01"))
        assertTrue(MoneyUtils.isValidAmountInput("0.5"))

        assertFalse(MoneyUtils.isValidAmountInput("0"))
        assertFalse(MoneyUtils.isValidAmountInput("0.00"))
        assertFalse(MoneyUtils.isValidAmountInput("-10"))
        assertFalse(MoneyUtils.isValidAmountInput("12.345"))
        assertFalse(MoneyUtils.isValidAmountInput(""))
        assertFalse(MoneyUtils.isValidAmountInput("abc"))
    }

    @Test
    fun testIsValidNonNegativeAmountInput() {
        assertTrue(MoneyUtils.isValidNonNegativeAmountInput("0"))
        assertTrue(MoneyUtils.isValidNonNegativeAmountInput("0.00"))
        assertTrue(MoneyUtils.isValidNonNegativeAmountInput("12.34"))

        assertFalse(MoneyUtils.isValidNonNegativeAmountInput("-1"))
        assertFalse(MoneyUtils.isValidNonNegativeAmountInput("12.345"))
        assertFalse(MoneyUtils.isValidNonNegativeAmountInput("999999999999999999999999"))
        assertFalse(MoneyUtils.isValidNonNegativeAmountInput("abc"))
    }
}
