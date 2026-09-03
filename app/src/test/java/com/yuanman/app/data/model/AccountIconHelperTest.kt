package com.yuanman.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountIconHelperTest {

    @Test
    fun getIcon_resolvesKnownIcons() {
        val walletIcon = AccountIconHelper.getIcon("account_balance_wallet")
        val creditIcon = AccountIconHelper.getIcon("credit_card")
        val trendingIcon = AccountIconHelper.getIcon("trending_up")

        assertNotNull(walletIcon)
        assertNotNull(creditIcon)
        assertNotNull(trendingIcon)
    }

    @Test
    fun getIcon_resolvesFallbackOnUnknownOrEmpty() {
        val defaultIcon = AccountIconHelper.getIcon("account_balance_wallet")
        val fallbackIcon = AccountIconHelper.getIcon("non_existent_random_icon")
        val emptyIcon = AccountIconHelper.getIcon("")

        assertEquals(defaultIcon, fallbackIcon)
        assertEquals(defaultIcon, emptyIcon)
    }

    @Test
    fun availableIcons_containsAllExpectedPresets() {
        val names = AccountIconHelper.availableIcons.map { it.name }
        assertTrue(names.contains("account_balance_wallet"))
        assertTrue(names.contains("account_balance"))
        assertTrue(names.contains("credit_card"))
        assertTrue(names.contains("savings"))
        assertTrue(names.contains("trending_up"))
        assertTrue(names.contains("show_chart"))
        assertTrue(names.contains("house"))
        assertTrue(names.contains("directions_car"))
    }

    @Test
    fun defaultColorHexes_areValidHexColors() {
        assertTrue(AccountIconHelper.defaultColorHexes.isNotEmpty())
        AccountIconHelper.defaultColorHexes.forEach { hex ->
            assertTrue(hex.startsWith("#"))
            assertEquals(7, hex.length)
        }
    }
}
