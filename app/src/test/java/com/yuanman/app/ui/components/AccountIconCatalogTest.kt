package com.yuanman.app.ui.components

import com.yuanman.app.data.model.CategoryIconHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 账户图标词表护栏：词表里每个 key 都必须能画出图标，且下架品牌不再出现在可选列表里。
 */
class AccountIconCatalogTest {

    @Test
    fun `淘宝与百度已从可选图标词表移除`() {
        assertFalse(
            "账户图标词表仍包含 taobao",
            "taobao" in AccountIconHelper.ALL_AVAILABLE_ACCOUNT_ICONS
        )
        assertFalse(
            "账户图标词表仍包含 baidu",
            "baidu" in AccountIconHelper.ALL_AVAILABLE_ACCOUNT_ICONS
        )
        assertFalse("taobao 仍能画出品牌图标", BrandAccountIcons.isBrand("taobao"))
        assertFalse("baidu 仍能画出品牌图标", BrandAccountIcons.isBrand("baidu"))
    }

    @Test
    fun `下架品牌的归一化目标必须是有效图标`() {
        AccountIconHelper.RETIRED_BRAND_ICONS.forEach { (retired, replacement) ->
            assertTrue(
                "「$retired」的归一化目标「$replacement」不是有效账户图标",
                replacement in AccountIconHelper.ALL_AVAILABLE_ACCOUNT_ICONS
            )
            assertTrue(
                "「$retired」的归一化目标「$replacement」画不出任何图形",
                resolvesToIcon(replacement)
            )
        }
    }

    @Test
    fun `词表中每个 key 都能解析出图标`() {
        val unresolved = AccountIconHelper.ALL_AVAILABLE_ACCOUNT_ICONS.filterNot { resolvesToIcon(it) }
        assertTrue("以下账户图标 key 解析不到任何图形：$unresolved", unresolved.isEmpty())
    }

    @Test
    fun `词表无重复项`() {
        val icons = AccountIconHelper.ALL_AVAILABLE_ACCOUNT_ICONS
        assertEquals("账户图标词表存在重复 key", icons.size, icons.distinct().size)
    }

    private fun resolvesToIcon(key: String): Boolean =
        BrandAccountIcons.isBrand(key) ||
            AccountIconHelper.isAccountIcon(key) ||
            CategoryIconHelper.AVAILABLE_ICONS.any { it.key == key }
}
