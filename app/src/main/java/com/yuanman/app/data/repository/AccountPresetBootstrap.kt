package com.yuanman.app.data.repository

import com.yuanman.app.data.local.dao.RecordDao
import com.yuanman.app.data.model.PaymentMethod
import com.yuanman.app.ui.screens.account.AccountUiModel
import com.yuanman.app.ui.screens.account.serializeAccountsJson
import kotlinx.coroutines.flow.first

/**
 * 老用户「常用账户」预置引导（v0.0.4.6，需求3「支出/收入账户选择合一」）。
 *
 * 背景：账户体系上线前，记账页的支出/收入选择器依赖内置支付方式字符串（微信支付、支付宝…），
 * 老用户的历史流水里存的就是这些字符串；而账户页的流水聚合靠「账户名启发式匹配」。
 * 为了让「账户」成为记账页与账户页的唯一概念，对从未创建过账户的老用户（accounts_data 为空，
 * 但存在未删除历史流水）在应用启动时自动落一套零余额预设账户，名称与 PaymentMethod 内置文案
 * 同源（PaymentMethod.COMMON_ACCOUNTS），这样历史流水能直接匹配进对应账户、且记账页选择器
 * 语义一致。
 *
 * 守卫：
 *  - 全新安装（无任何未删除流水）不预置 —— 产品约定首启不给假数据；
 *  - accounts_data 非空即视为已初始化，幂等守卫：哪怕用户手动删光了账户（残留空数组），也不再写入。
 *
 * 挂载点：YuanmanApplication.onCreate 中 appScope 启动协程内（数据库恢复/默认分类就绪之后）；
 * 全部异常静默吞掉，失败不影响启动。
 */
object AccountPresetBootstrap {

    private data class AccountPresetStyle(val iconName: String, val colorHex: Long)

    /** 与 PaymentMethod.COMMON_ACCOUNTS 一一对应的图标与主题色（品牌户用品牌图标 key，其余用账户词表 key） */
    private val presetStyles = listOf(
        AccountPresetStyle("wechat", 0xFF07C160L),    // 微信支付（微信绿 + 品牌气泡）
        AccountPresetStyle("alipay", 0xFF1677FFL),    // 支付宝（支付蓝 + 品牌字标）
        AccountPresetStyle("wallet", 0xFF26A69AL),    // 现金（青）
        AccountPresetStyle("bank", 0xFF3F51B5L),      // 银行卡（靛蓝）
        AccountPresetStyle("card_gift", 0xFFE53935L), // 信用卡（红）
        AccountPresetStyle("part_time", 0xFF9C27B0L)  // 花呗/白条（紫）
    )

    suspend fun ensurePresetAccountsForLegacyUsers(
        recordDao: RecordDao,
        preferencesRepository: PreferencesRepository
    ) {
        runCatching {
            // 1) 幂等守卫：accounts_data 已有内容（含用户手动清空后残留的空数组 "[]"）即视为已初始化
            if (!preferencesRepository.accountsData.first().isNullOrBlank()) return

            // 2) 全新安装判定：没有任何未删除流水 → 不预置（首启不给假数据）
            if (recordDao.getTotalRecordCountDirect() <= 0) return

            // 3) 落一套零余额常用账户，手法同 AccountViewModel.createAccount / AccountDataCodec 契约
            val accounts = PaymentMethod.COMMON_ACCOUNTS.mapIndexed { index, name ->
                val style = presetStyles.getOrNull(index) ?: presetStyles.first()
                AccountUiModel(
                    id = index + 1L,
                    name = name,
                    label = "", // 未分组（账户页归入「未分组」，展示由 UI 决定）
                    iconName = style.iconName,
                    colorHex = style.colorHex,
                    openingBalanceCents = 0L,
                    balanceCents = 0L,
                    inCents = 0L,
                    outCents = 0L,
                    sortOrder = index + 1
                )
            }
            preferencesRepository.saveAccountsData(serializeAccountsJson(accounts))
        }
    }
}
