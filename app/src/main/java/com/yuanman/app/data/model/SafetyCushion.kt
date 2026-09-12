package com.yuanman.app.data.model

/**
 * 资金安全垫视觉调性基准（纯数据模型，UI 依据 tone 映射主题色）
 */
enum class CushionTone {
    DANGER,   // 红色（负债倒挂、告急）
    WARNING,  // 琥珀黄（偏紧）
    POSITIVE, // 绿色（稳健达标）
    FRESH,    // 主品牌色（从容底气）
    INFO,     // 淡蓝/青色（自由宽裕）
    MUTED     // 冷灰（数据积累中、资金闲置）
}

/**
 * 资金安全垫等级（高防御、抗周期模型）
 *
 * 标尺定位：
 * - 6 个月为及格安全线；
 * - 1~2 年为稳健的抗周期“底气储备”；
 * - 超过 3 年提示现金闲置与通胀磨损。
 */
enum class SafetyCushionLevel(
    val label: String,
    val tone: CushionTone,
    val minMonths: Float,
    val maxMonths: Float,
    val defaultInsight: String
) {
    DEFICIT(
        label = "透支倒挂",
        tone = CushionTone.DANGER,
        minMonths = 0f,
        maxMonths = 0f,
        defaultInsight = "可用现金无法覆盖即期应还负债，处于净负债运转状态，面临即刻断流风险。"
    ),
    CRITICAL(
        label = "脆弱警戒",
        tone = CushionTone.DANGER,
        minMonths = 0f,
        maxMonths = 3.0f,
        defaultInsight = "储备不足以支撑求职缓冲期（<3个月），极易因突发大额开销或收入中断陷入被动。"
    ),
    TIGHT(
        label = "浅水过渡",
        tone = CushionTone.WARNING,
        minMonths = 3.0f,
        maxMonths = 6.0f,
        defaultInsight = "具备短期周转能力，但不足以抵御长期职业断档或重大变故，建议继续夯实备用金。"
    ),
    HEALTHY(
        label = "稳健防线",
        tone = CushionTone.POSITIVE,
        minMonths = 6.0f,
        maxMonths = 12.0f,
        defaultInsight = "跨过半年安全门槛，足以从容抵御行业周期与突发变动，达到扎实的财务安全基线。"
    ),
    FORTRESS(
        label = "从容底气",
        tone = CushionTone.FRESH,
        minMonths = 12.0f,
        maxMonths = 24.0f,
        defaultInsight = "拥有 1~2 年的生活备粮，不仅防御力拉满，更具备抵御风险与主动选择生活的硬核底气。"
    ),
    ABUNDANT(
        label = "自由宽裕",
        tone = CushionTone.INFO,
        minMonths = 24.0f,
        maxMonths = 36.0f,
        defaultInsight = "纯现金可支撑 2 年以上无忧生活，抗风险能力极强，生活防御已处于极高水准。"
    ),
    EXCESSIVE(
        label = "资金闲置",
        tone = CushionTone.MUTED,
        minMonths = 36.0f,
        maxMonths = Float.MAX_VALUE,
        defaultInsight = "活期现金储备超 3 年，防御边际效用递减，面临通胀磨损，建议配置稳健资产。"
    ),
    COLLECTING(
        label = "数据积累中",
        tone = CushionTone.MUTED,
        minMonths = 0f,
        maxMonths = 0f,
        defaultInsight = "记账数据积累不足，持续记录 30 天生活开销后将自动开启安全垫评估。"
    )
}

/**
 * 安全垫计算输出结果
 */
data class SafetyCushionResult(
    val level: SafetyCushionLevel,
    val runwayMonths: Float,             // 安全月数（如 8.5 个月）
    val netLiquidCents: Long,            // 净可用流动现金（活期 - 待还负债）
    val liquidAssetCents: Long,          // 活期流动资产总值
    val debtCents: Long,                 // 待还负债总值（负数存储）
    val monthlyBurnCents: Long,          // 近期参考月均支出（包含保底基线）
    val progressPercent: Float,          // 仪表进度条（0.0 ~ 1.0，映射 0~24 个月）
    val insightMessage: String           // 财务洞察与行动指引
) {
    companion object {
        fun collecting(
            liquidAssetCents: Long = 0L,
            debtCents: Long = 0L
        ): SafetyCushionResult {
            val netLiquid = liquidAssetCents + debtCents
            return SafetyCushionResult(
                level = SafetyCushionLevel.COLLECTING,
                runwayMonths = 0f,
                netLiquidCents = netLiquid,
                liquidAssetCents = liquidAssetCents,
                debtCents = debtCents,
                monthlyBurnCents = 0L,
                progressPercent = 0f,
                insightMessage = SafetyCushionLevel.COLLECTING.defaultInsight
            )
        }

        fun deficit(
            netLiquidCents: Long,
            liquidAssetCents: Long,
            debtCents: Long,
            monthlyBurnCents: Long
        ): SafetyCushionResult {
            return SafetyCushionResult(
                level = SafetyCushionLevel.DEFICIT,
                runwayMonths = 0f,
                netLiquidCents = netLiquidCents,
                liquidAssetCents = liquidAssetCents,
                debtCents = debtCents,
                monthlyBurnCents = monthlyBurnCents,
                progressPercent = 0f,
                insightMessage = SafetyCushionLevel.DEFICIT.defaultInsight
            )
        }
    }
}

/**
 * 安全垫严苛计算引擎（纯逻辑、防崩溃、自闭环）
 */
object SafetyCushionCalculator {

    // 保底最低生存开支基线：¥2,000.00 / 月，防止用户近期开销极少时安全月数虚假膨胀
    const val DEFAULT_BASELINE_MONTHLY_BURN_CENTS = 200_000L

    /**
     * @param liquidAssetCents 可快速支取的流动资金（如活期存款、零钱钱包、随取理财）
     * @param currentDebtCents 即期应还负债（以负数存储，如 -¥12,000）
     * @param recentExpensesCents 近 3 个月的实际总开支
     * @param recordedDays 记账覆盖天数（如小于 14 天视为冷启动）
     * @param baselineBurnCents 最低月支出兜底
     */
    fun calculate(
        liquidAssetCents: Long,
        currentDebtCents: Long,
        recentExpensesCents: Long,
        recordedDays: Int,
        baselineBurnCents: Long = DEFAULT_BASELINE_MONTHLY_BURN_CENTS
    ): SafetyCushionResult {
        // 1. 冷启动保护：记录天数不足两周，或近几个月根本没有记录开销
        if (recordedDays < 14 || recentExpensesCents <= 0L) {
            return SafetyCushionResult.collecting(
                liquidAssetCents = liquidAssetCents,
                debtCents = currentDebtCents
            )
        }

        // 2. 严控净流动现金：剔除已透支的即期负债（currentDebtCents 是负数）
        val netLiquid = liquidAssetCents + currentDebtCents

        // 3. 计算月均支出（取近3个月均值与保底线的较大者）
        val rawMonthlyBurn = recentExpensesCents / 3
        val monthlyBurn = rawMonthlyBurn.coerceAtLeast(baselineBurnCents).coerceAtLeast(1L)

        // 4. 若净流动资产为负，判定为透支倒挂
        if (netLiquid <= 0L) {
            return SafetyCushionResult.deficit(
                netLiquidCents = netLiquid,
                liquidAssetCents = liquidAssetCents,
                debtCents = currentDebtCents,
                monthlyBurnCents = monthlyBurn
            )
        }

        // 5. 计算安全月数
        val months = (netLiquid.toDouble() / monthlyBurn.toDouble()).toFloat()

        // 6. 等级判定（高防御区间）
        val level = when {
            months < 3.0f -> SafetyCushionLevel.CRITICAL
            months < 6.0f -> SafetyCushionLevel.TIGHT
            months < 12.0f -> SafetyCushionLevel.HEALTHY
            months < 24.0f -> SafetyCushionLevel.FORTRESS
            months < 36.0f -> SafetyCushionLevel.ABUNDANT
            else -> SafetyCushionLevel.EXCESSIVE
        }

        // 7. 进度条以 24 个月（从容底气）为满格 100% 标尺
        val progress = (months / 24.0f).coerceIn(0f, 1f)

        return SafetyCushionResult(
            level = level,
            runwayMonths = months,
            netLiquidCents = netLiquid,
            liquidAssetCents = liquidAssetCents,
            debtCents = currentDebtCents,
            monthlyBurnCents = monthlyBurn,
            progressPercent = progress,
            insightMessage = level.defaultInsight
        )
    }
}
