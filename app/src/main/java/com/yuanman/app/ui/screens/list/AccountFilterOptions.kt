package com.yuanman.app.ui.screens.list

import com.yuanman.app.ui.screens.account.AccountUiModel
import com.yuanman.app.ui.screens.account.findAccountForMethod

/**
 * 选中「暂无账单命中」的账户时使用的占位方式值。
 *
 * 筛选参数为空表示「不筛选」，所以不能让这类账户对应空集合，
 * 否则点它会退化成显示全部账单；这里用一个以控制字符开头、不可能被用户输入出来的值，
 * 让 SQL 的 `paymentMethod IN (...)` 命中 0 条，明确显示「该账户暂无账单」。
 */
internal const val ACCOUNT_FILTER_NO_MATCH = "\u0001__yuanman_no_match__"

/**
 * 明细页「全部账户」筛选项：一个资金账户 + 该账户对应的筛选值。
 *
 * 账单表里没有账户外键，只存支付方式文本（如「微信支付」「中国银行」），
 * 因此这里沿用账户页同一套匹配函数 [findAccountForMethod]，
 * 保证「明细页按账户筛选」与「账户详情页本月流水」看到的是同一批账单。
 *
 * @param filterMethods 该账户命中的支付方式；一个都没有时是 [ACCOUNT_FILTER_NO_MATCH]（非空，保证点得动且筛得出空结果）
 */
data class AccountFilterOption(
    val accountId: Long,
    val name: String,
    val colorHex: Long,
    val filterMethods: List<String>
)

data class AccountFilterOptions(
    /** 资金账户筛选项，按账户排序（sortOrder）给出；暂无账单的账户也在其中（点它得到空结果）。 */
    val options: List<AccountFilterOption> = emptyList(),
    /** 未归属任何资金账户的支付方式（历史预置写法），归入「其它方式」筛选项。 */
    val unmatchedMethods: List<String> = emptyList()
)

/**
 * 把账单里出现过的支付方式归类到资金账户。
 *
 * - 列出全部资金账户（与分类筛选行口径一致：分类也是全量展示，允许筛出空结果）；
 * - 暂无账单命中的账户使用 [ACCOUNT_FILTER_NO_MATCH] 占位，点它得到空结果而不是退化成不筛选；
 * - 同一个支付方式只会归到一个账户（匹配函数本身保证唯一性判定）；
 * - 空字符串与纯空白方式直接忽略。
 */
internal fun buildAccountFilterOptions(
    accounts: List<AccountUiModel>,
    distinctPaymentMethods: List<String>
): AccountFilterOptions {
    val orderedAccounts = accounts.sortedBy { it.sortOrder }
    val methodsByAccount = LinkedHashMap<Long, MutableList<String>>()
    val unmatched = mutableListOf<String>()

    distinctPaymentMethods.forEach { raw ->
        val method = raw.trim()
        if (method.isEmpty()) return@forEach
        val account = findAccountForMethod(method, orderedAccounts)
        if (account == null) {
            unmatched += method
        } else {
            methodsByAccount.getOrPut(account.id) { mutableListOf() } += method
        }
    }

    val options = orderedAccounts.map { account ->
        val methods = methodsByAccount[account.id].orEmpty()
        AccountFilterOption(
            accountId = account.id,
            name = account.name,
            colorHex = account.colorHex,
            filterMethods = methods.ifEmpty { listOf(ACCOUNT_FILTER_NO_MATCH) }
        )
    }
    return AccountFilterOptions(options = options, unmatchedMethods = unmatched)
}
