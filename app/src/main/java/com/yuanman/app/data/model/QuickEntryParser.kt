package com.yuanman.app.data.model

import com.yuanman.app.data.local.entity.CategoryEntity
import com.yuanman.app.data.local.entity.QuickEntryLearningEntity
import java.math.BigDecimal
import java.util.Locale

data class QuickEntryResult(
    val remark: String,
    val amountYuan: BigDecimal,
    val category: CategoryEntity?,
    val paymentMethod: String? = null,
    val confidence: Float = 0f,
    val alternatives: List<CategoryEntity> = emptyList()
)

/** Parses compact entries such as "奶茶 18" or "18元 午餐". */
object QuickEntryParser {
    private val amountPattern = Regex("(?i)(?<!\\d)[¥￥$]?\\s*(\\d+(?:[.,]\\d{1,2})?)(?:\\s*(?:块钱|块|元|圆|快|rmb))?(?!\\d)")

    private data class PaymentAlias(
        val displayName: String,
        val alias: String
    )

    // 支付方式优先按长词匹配，避免“微信支付”被“微信”提前截断。
    private val PAYMENT_ALIASES = listOf(
        PaymentMethod.WECHAT.displayName to listOf("微信支付", "wechat", "微信", "wx"),
        "微信零钱" to listOf("微信零钱", "零钱通", "零钱"),
        PaymentMethod.ALIPAY.displayName to listOf("支付宝支付", "alipay", "支付宝", "zfb"),
        "花呗/白条" to listOf("花呗支付", "京东白条", "白条支付", "花呗", "白条"),
        PaymentMethod.BANK_CARD.displayName to listOf("银行卡支付", "银行卡", "借记卡", "储蓄卡", "银联卡", "银联", "刷卡"),
        PaymentMethod.CREDIT_CARD.displayName to listOf("信用卡支付", "信用卡"),
        PaymentMethod.CASH.displayName to listOf("现金支付", "现金", "现付"),
        "投资理财" to listOf("投资理财", "理财账户", "余额宝", "理财"),
        PaymentMethod.OTHER.displayName to listOf("其他支付", "其他方式")
    ).flatMap { (displayName, aliases) ->
        aliases.map { PaymentAlias(displayName, it) }
    }.sortedByDescending { it.alias.length }

    fun normalizeLearningText(text: String): String = text
        .trim()
        .lowercase(Locale.ROOT)
        .replace(Regex("[\\s\\p{Punct}，。！？；：、“”‘’（）【】《》…·]+"), "")

    fun parse(
        input: String,
        categories: List<CategoryEntity>,
        learningRules: List<QuickEntryLearningEntity> = emptyList()
    ): QuickEntryResult? {
        val text = input.trim()
        val amountMatch = amountPattern.findAll(text).lastOrNull() ?: return null
        val amount = runCatching {
            BigDecimal(amountMatch.groupValues[1].replace(',', '.'))
        }.getOrNull() ?: return null
        if (amount <= BigDecimal.ZERO) return null

        val rawDescription = text.removeRange(amountMatch.range)
            .replace(Regex("(?i)[¥￥$]"), "")
            .replace(Regex("(?i)块钱|块|元|圆|快|rmb"), "")
            .replace(Regex("[：:，,、\\-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val paymentMatch = PAYMENT_ALIASES.firstOrNull { rawDescription.contains(it.alias, ignoreCase = true) }
        val description = rawDescription
            .let { value ->
                paymentMatch?.let { value.replace(Regex(Regex.escape(it.alias), RegexOption.IGNORE_CASE), "") } ?: value
            }
            // 去除“用/使用/通过”等支付方式前缀残留，避免备注只剩一个连接词。
            .replace(Regex("^(?:用|使用|通过|从|走)"), "")
            .replace(Regex("(?:用|使用|通过|从|走)$"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        val match = findCategory(description, categories, learningRules)
        return QuickEntryResult(
            remark = description,
            amountYuan = amount,
            category = match?.first,
            paymentMethod = paymentMatch?.displayName,
            confidence = match?.second ?: 0f,
            alternatives = match?.third.orEmpty()
        )
    }

    private val PRECOMPUTED_ALIASES: Map<String, List<String>> by lazy {
        (DEFAULT_ALIASES.keys + SUPPLEMENTAL_ALIASES.keys + EXTENDED_ALIASES.keys).distinct().associateWith { catName ->
            (DEFAULT_ALIASES[catName].orEmpty() + SUPPLEMENTAL_ALIASES[catName].orEmpty() + EXTENDED_ALIASES[catName].orEmpty())
                .flatMap { alias -> listOf(alias) + alias.split(Regex("[/、·和及&+\\s]+")) }
                .map(::normalizeLearningText)
                .filter { it.length >= 2 }
                .distinct()
        }
    }

    private fun findCategory(
        description: String,
        categories: List<CategoryEntity>,
        learningRules: List<QuickEntryLearningEntity>
    ): Triple<CategoryEntity, Float, List<CategoryEntity>>? {
        if (categories.isEmpty()) return null
        val normalized = normalizeLearningText(description)
        if (normalized.isBlank()) return Triple(categories.first(), 0.05f, categories.drop(1).take(2))

        // 1. 用户历史学习规则匹配
        val matchedRule = learningRules.firstOrNull { rule ->
            rule.phrase.isNotBlank() && normalizeLearningText(rule.phrase) == normalized
        }
        if (matchedRule != null) {
            val cat = categories.firstOrNull { it.syncId == matchedRule.categorySyncId && it.type == matchedRule.type }
            if (cat != null) {
                val confidence = (0.85f + (matchedRule.sampleCount.coerceAtMost(5) * 0.03f)).coerceIn(0f, 1f)
                return Triple(cat, confidence, categories.filter { it.id != cat.id }.take(2))
            }
        }

        // 2. 快速全词匹配 (Exact Match)
        for (category in categories) {
            val aliases = buildAliases(category)
            if (aliases.any { it == normalized }) {
                return Triple(category, 0.95f, categories.filter { it.id != category.id }.take(2))
            }
        }

        // 3. 包含匹配 (Containment Match)
        val containmentScored = categories.mapNotNull { category ->
            val aliases = buildAliases(category)
            var maxScore = 0f
            for (alias in aliases) {
                if (normalized.contains(alias)) {
                    val s = 90f + alias.length.coerceAtMost(12) * 5f
                    if (s > maxScore) maxScore = s
                } else if (alias.contains(normalized) && normalized.length >= 2) {
                    val s = 45f + normalized.length * 4f
                    if (s > maxScore) maxScore = s
                }
            }
            if (maxScore > 0f) category to maxScore else null
        }.sortedByDescending { it.second }

        if (containmentScored.isNotEmpty()) {
            val top = containmentScored.first()
            val second = containmentScored.getOrNull(1)?.second ?: 0f
            val margin = ((top.second - second) / top.second).coerceIn(0f, 1f)
            val confidence = (0.70f + margin * 0.25f).coerceIn(0f, 1f)
            return Triple(top.first, confidence, containmentScored.drop(1).take(2).map { it.first })
        }

        // 4. 模糊相似度匹配 (仅在无直接匹配时使用轻量算法)
        val fuzzyScored = categories.map { category ->
            var score = 0f
            for (alias in buildAliases(category)) {
                val sim = fastSimilarity(normalized, alias)
                if (sim >= 0.50f) {
                    score = maxOf(score, sim * 105f)
                }
            }
            category to score
        }.sortedByDescending { it.second }

        val topFuzzy = fuzzyScored.firstOrNull() ?: return null
        if (topFuzzy.second <= 0f) return Triple(topFuzzy.first, 0.05f, fuzzyScored.drop(1).take(2).map { it.first })
        val secondFuzzy = fuzzyScored.getOrNull(1)?.second ?: 0f
        val margin = ((topFuzzy.second - secondFuzzy) / topFuzzy.second).coerceIn(0f, 1f)
        val confidence = (0.50f + margin * 0.35f).coerceIn(0f, 1f)
        return Triple(topFuzzy.first, confidence, fuzzyScored.drop(1).take(2).map { it.first })
    }

    private fun buildAliases(category: CategoryEntity): List<String> {
        val precomputed = PRECOMPUTED_ALIASES[category.name].orEmpty()
        val customTags = if (category.tags.isBlank()) emptyList() else {
            category.tags.split(',').map { normalizeLearningText(it) }.filter { it.length >= 2 }
        }
        val catNameNorm = normalizeLearningText(category.name)
        return listOf(catNameNorm) + customTags + precomputed
    }

    /** 返回可展示、可持久化的系统预置词；用户学习记录不包含在内。 */
    fun defaultLearningPhrases(category: CategoryEntity): List<String> {
        return PRECOMPUTED_ALIASES[category.name].orEmpty()
    }

    private fun fastSimilarity(first: String, second: String): Float {
        if (first.isBlank() || second.isBlank()) return 0f
        if (first == second) return 1f
        var matches = 0
        for (i in 0 until first.length) {
            if (second.contains(first[i])) matches++
        }
        return (2f * matches / (first.length + second.length)).coerceIn(0f, 1f)
    }

    private val DEFAULT_ALIASES = mapOf(
        "餐饮美食" to listOf("奶茶", "咖啡", "早餐", "午餐", "晚餐", "夜宵", "外卖", "吃饭", "餐厅", "饭店", "火锅", "烧烤", "面馆", "快餐", "食堂", "买菜", "菜市场", "水果", "零食", "饮料", "矿泉水", "蛋糕", "面包", "瑞幸", "星巴克", "肯德基", "麦当劳", "汉堡王", "必胜客", "海底捞", "美团外卖", "饿了么", "盒马"),
        "交通出行" to listOf("地铁", "公交", "打车", "网约车", "滴滴", "出租车", "高铁", "火车", "动车", "机票", "飞机", "机场", "共享单车", "单车", "过路费", "高速费", "公交卡", "地铁卡", "船票", "轮渡"),
        "爱车养车" to listOf("加油", "油费", "充电", "充电桩", "停车", "停车费", "洗车", "洗车美容", "保养", "维修", "修车", "轮胎", "机油", "车险", "保险", "年检", "车位", "违章", "拖车"),
        "充值缴费" to listOf("话费", "手机充值", "电话费", "宽带", "网费", "水费", "电费", "燃气费", "燃气", "供暖费", "有线电视", "会员充值", "充值", "缴费", "物业费"),
        "服饰装扮" to listOf("衣服", "服装", "上衣", "裤子", "裙子", "外套", "羽绒服", "鞋", "运动鞋", "靴子", "包", "背包", "钱包", "帽子", "手表", "眼镜", "首饰", "美妆", "化妆品", "护肤", "口红", "理发", "美发"),
        "家居家装" to listOf("家具", "家电", "家居", "家纺", "床品", "床垫", "沙发", "桌椅", "窗帘", "五金", "建材", "装修", "软装", "日用品", "日用百货", "厨具", "餐具", "收纳", "清洁用品", "纸巾", "洗衣液", "宜家"),
        "数码电器" to listOf("手机", "电脑", "笔记本", "平板", "ipad", "耳机", "耳麦", "充电器", "数据线", "手机壳", "相机", "摄影", "键盘", "鼠标", "显示器", "硬盘", "打印机", "家电", "冰箱", "洗衣机", "电视", "空调", "小米", "华为", "苹果"),
        "运动户外" to listOf("健身", "健身房", "瑜伽", "游泳", "羽毛球", "篮球", "足球", "网球", "跑步", "骑行", "露营", "徒步", "登山", "户外", "运动装备", "球鞋", "场馆", "体育课"),
        "美容美发" to listOf("理发", "剪发", "烫发", "染发", "美发", "护肤", "美容", "美容院", "美甲", "美睫", "spa", "按摩", "洗脸", "面膜", "个护", "沐浴", "牙刷", "牙膏"),
        "母婴亲子" to listOf("奶粉", "辅食", "纸尿裤", "尿不湿", "童装", "童鞋", "玩具", "绘本", "早教", "亲子", "产检", "育儿", "母婴", "婴儿车", "安全座椅", "托儿所"),
        "住房物业" to listOf("房租", "租金", "房贷", "月供", "物业", "物业费", "车位租金", "房屋修缮", "维修费", "水电房租", "中介费", "押金", "公寓", "租房"),
        "酒店旅游" to listOf("酒店", "住宿", "民宿", "宾馆", "景点", "门票", "旅游", "旅行", "度假", "机票", "跟团", "自由行", "导游", "旅行社", "签证", "行李", "纪念品"),
        "文化休闲" to listOf("电影", "电影院", "电影票", "演出", "剧场", "音乐会", "展览", "博物馆", "书店", "书籍", "杂志", "桌游", "密室", "游戏", "网吧", "直播", "会员", "视频会员", "音乐会员"),
        "教育培训" to listOf("学费", "教材", "书本", "培训", "课程", "网课", "在线课程", "考证", "考试", "报名费", "语言学习", "英语", "托福", "雅思", "技能培训", "兴趣班", "补习", "家教"),
        "医疗健康" to listOf("医院", "门诊", "挂号", "看病", "药", "买药", "西药", "中药", "体检", "疫苗", "牙科", "洗牙", "眼科", "保健", "维生素", "医疗", "医药费", "住院"),
        "生活服务" to listOf("家政", "保洁", "清洁", "快递", "跑腿", "干洗", "修鞋", "搬家", "宠物", "宠物粮", "宠物医院", "维修服务", "开锁", "洗衣", "废品回收", "服务费"),
        "人情往来" to listOf("礼金", "随礼", "份子钱", "红包", "长辈", "孝敬", "压岁钱", "请客", "送礼", "礼物", "生日", "婚礼", "满月", "聚会买单"),
        "投资理财" to listOf("基金", "买基金", "股票", "证券", "黄金", "理财", "定投", "存款", "定存", "保险", "外汇", "买入", "申购", "手续费", "投资"),
        "工资" to listOf("工资", "月薪", "薪资", "发薪", "年终奖", "绩效", "奖金", "全勤奖", "岗位津贴", "补贴", "餐补", "房补"),
        "红包转账" to listOf("红包", "微信红包", "支付宝红包", "转账", "微信转账", "支付宝转账", "礼物", "压岁钱", "亲友转账"),
        "理财收益" to listOf("分红", "利息", "银行利息", "基金收益", "基金分红", "股票盈利", "股票收益", "理财收益", "理财赎回", "国债利息"),
        "兼职外快" to listOf("兼职", "副业", "外快", "接单", "设计稿", "稿费", "投稿", "劳务", "咨询费", "外包", "带货", "闲置出清"),
        "退款" to listOf("退款", "网购退款", "退货", "返现", "差价返还", "退税", "押金退还", "报销", "赔付"),
        "其他" to listOf("其他", "杂项", "零星", "临时支出", "不明支出", "其他收入", "中奖", "中奖收入", "意外所得", "补贴津贴")
    )

    /** 补充口语、平台名、场景词，覆盖用户从一个字到完整短句的常见输入。 */
    private val SUPPLEMENTAL_ALIASES = mapOf(
        "餐饮美食" to listOf("早饭", "午饭", "晚饭", "饭钱", "加餐", "下午茶", "奶茶店", "咖啡店", "早餐店", "堂食", "打包", "外卖配送", "烧鹅", "饺子", "包子", "面条", "米粉", "日料", "寿司", "麻辣烫", "串串", "烤肉", "甜品", "冰淇淋", "酸奶", "坚果", "可乐", "啤酒", "白酒", "茶饮", "喜茶", "奈雪", "霸王茶姬", "瑞幸咖啡"),
        "交通出行" to listOf("车费", "路费", "通勤", "公交地铁", "地铁票", "公交票", "电动车", "电单车", "共享电单车", "网约车费", "出租车费", "顺风车", "快车", "专车", "打车软件", "高速通行", "ETC", "停车场", "船票", "车票", "出差交通", "通勤卡"),
        "爱车养车" to listOf("加油站", "汽油", "柴油", "油价", "电车充电", "快充", "慢充", "充电服务", "停车场缴费", "停车券", "洗车店", "车美容", "保养套餐", "保养工时", "换机油", "换轮胎", "补胎", "汽车维修", "车辆年检", "交强险", "车船税", "汽车用品", "行车记录仪"),
        "充值缴费" to listOf("电话充值", "手机费", "流量费", "流量充值", "宽带费", "网络费", "水电费", "水电煤", "燃气充值", "电费充值", "水费充值", "暖气费", "物业缴费", "停车缴费", "电视费", "会员续费", "自动续费", "生活缴费"),
        "服饰装扮" to listOf("买衣服", "买鞋", "买包", "短袖", "长袖", "衬衫", "牛仔裤", "休闲裤", "内衣", "袜子", "凉鞋", "运动服", "羽绒服", "雨衣", "围巾", "手套", "饰品", "耳环", "项链", "眼镜店", "美妆用品", "防晒", "香水", "护发", "理容"),
        "家居家装" to listOf("家里用品", "家具城", "家电维修", "床单", "被子", "枕头", "衣架", "灯具", "插座", "水龙头", "工具", "油漆", "装修材料", "窗帘店", "锅具", "杯子", "保鲜膜", "垃圾袋", "抽纸", "卫生纸", "洗洁精", "消毒液", "扫把", "拖把", "收纳盒", "超市日用品"),
        "数码电器" to listOf("买手机", "手机维修", "手机配件", "手机膜", "电脑维修", "办公用品", "显示器支架", "键鼠", "路由器", "网络设备", "移动硬盘", "u盘", "内存条", "显卡", "游戏机", "手柄", "音箱", "麦克风", "智能手表", "手环", "无人机", "镜头", "相片打印", "小家电"),
        "运动户外" to listOf("健身卡", "私教", "团课", "瑜伽课", "游泳卡", "球场费", "羽毛球馆", "篮球场", "足球场", "跑鞋", "运动服装", "运动手表", "自行车", "骑行装备", "帐篷", "睡袋", "登山鞋", "户外用品", "滑雪", "滑板", "钓鱼", "露营地"),
        "美容美发" to listOf("剪头发", "烫染", "理发店", "洗剪吹", "美容护理", "皮肤管理", "纹眉", "脱毛", "美甲店", "美睫店", "按摩店", "足疗", "肩颈", "洗浴", "沐浴露", "洗发水", "护发素", "护肤品", "彩妆", "口腔护理"),
        "母婴亲子" to listOf("宝宝奶粉", "婴儿辅食", "宝宝零食", "尿布", "拉拉裤", "宝宝衣服", "童鞋", "儿童玩具", "积木", "婴儿用品", "推车", "婴儿床", "儿童座椅", "亲子乐园", "儿童乐园", "绘本故事", "幼儿园", "月嫂", "育儿嫂", "儿童医院"),
        "住房物业" to listOf("租房款", "租房押金", "房租水电", "房贷还款", "按揭", "物业管理", "物业维修", "房屋中介", "中介服务费", "家装维修", "家电维修", "小区停车", "车位费", "公寓租金", "宿舍费", "房屋保险"),
        "酒店旅游" to listOf("住酒店", "订酒店", "酒店房费", "民宿房费", "旅馆", "青旅", "景区门票", "旅游团", "旅行团", "火车票", "飞机票", "机场服务", "租车旅游", "导游费", "签证费", "旅游保险", "行程用品", "特产", "伴手礼", "游乐园"),
        "文化休闲" to listOf("看电影", "电影购票", "话剧", "音乐节", "演唱会", "剧本杀", "密室逃脱", "游乐场", "书本", "电子书", "阅读会员", "视频网站", "视频续费", "音乐软件", "游戏点券", "游戏道具", "桌球", "麻将", "娱乐消费", "拍照写真"),
        "教育培训" to listOf("报名学习", "学杂费", "教材费", "辅导班", "补课", "家教费", "驾校", "驾照考试", "职业培训", "技能课", "编程课", "画画课", "音乐课", "钢琴课", "舞蹈课", "考试报名", "考研", "公务员考试", "英语课", "学习资料"),
        "医疗健康" to listOf("看医生", "医院挂号", "门诊费", "住院费", "药店买药", "感冒药", "处方药", "中医", "针灸", "理疗", "体检费", "核酸", "口腔医院", "补牙", "配眼镜", "隐形眼镜", "护眼", "营养品", "保健品", "医疗保险"),
        "生活服务" to listOf("钟点工", "保姆", "家电清洗", "上门维修", "快递费", "寄快递", "跑腿费", "洗衣店", "干洗店", "修理", "配钥匙", "搬家公司", "搬家费", "宠物店", "宠物美容", "宠物寄养", "宠物疫苗", "家政服务", "摄影服务", "打印复印"),
        "人情往来" to listOf("红包钱", "礼物钱", "送红包", "结婚随礼", "婚宴", "生日礼物", "节日礼物", "过节送礼", "探亲礼", "孝敬父母", "给孩子红包", "压岁红包", "请朋友吃饭", "聚餐", "团建聚餐", "份子礼", "人情费"),
        "投资理财" to listOf("买股票", "股票交易", "股票手续费", "基金定投", "基金申购", "基金赎回", "买黄金", "黄金首饰投资", "银行理财", "理财产品", "存钱", "存定期", "定期存款", "保险缴费", "证券开户", "交易费", "外币兑换", "数字资产", "投资本金"),
        "工资" to listOf("发工资", "工资到账", "本月工资", "上月工资", "基本工资", "底薪", "薪酬", "绩效工资", "绩效奖", "季度奖", "年终奖金", "加班费", "津贴", "补助", "出差补贴", "住房补贴", "通讯补贴", "工资条"),
        "红包转账" to listOf("收红包", "发红包", "微信收款", "支付宝收款", "转入", "转账收入", "亲友转钱", "家人转账", "节日红包", "生日红包", "礼金收入", "压岁钱收入", "红包到账"),
        "理财收益" to listOf("投资分红", "基金分红到账", "股票分红", "利息收入", "存款利息", "理财到账", "理财回款", "赎回到账", "债券利息", "国债收益", "黄金收益", "投资收益", "收益到账"),
        "兼职外快" to listOf("做兼职", "兼职收入", "副业收入", "接私活", "私活收入", "项目款", "项目结算", "劳务报酬", "咨询收入", "设计费", "开发费", "翻译费", "写作收入", "稿费到账", "直播收入", "带货佣金", "出售闲置", "二手转卖"),
        "退款" to listOf("退款到账", "购物退款", "订单退款", "退货退款", "售后退款", "满减返还", "优惠返现", "平台返现", "押金退回", "押金到账", "报销到账", "费用报销", "赔偿款", "退税到账"),
        "其他" to listOf("零用钱", "杂费", "临时花费", "小额支出", "其他开销", "其他款项", "中奖奖金", "彩票中奖", "意外奖金", "补贴到账", "政府补助", "不确定分类")
    )

    /**
     * 第三层系统语料：覆盖更口语化的短句、消费场景和平台表达。
     * 这些词会和上面的基础词一样幂等写入分类学习表，既能参与识别，也能在设置中查看。
     */
    private val EXTENDED_ALIASES = mapOf(
        "餐饮美食" to listOf(
            "餐费", "饭费", "吃饭钱", "吃饭消费", "买饭", "买早餐", "买午餐", "买晚餐", "早茶", "饭馆消费",
            "餐馆消费", "吃喝消费", "菜钱", "买菜", "菜市场买菜", "生鲜食品", "超市买菜", "食材采购", "调味品",
            "便当", "盒饭", "食堂饭", "点餐", "点外卖", "外卖餐", "外卖费", "奶茶饮品", "咖啡饮品", "水果蔬菜",
            "面包店", "糕点甜品", "小吃店", "快餐店", "自助餐", "聚餐消费", "酒水饮料", "烟酒消费", "夜宵摊"
        ),
        "交通出行" to listOf(
            "出行费", "通勤费", "上班交通", "交通费用", "乘地铁", "坐公交", "公交车费", "地铁车费", "打车出行",
            "网约车费", "叫车费", "出租车费", "顺风车费", "快车出行", "专车出行", "高铁车票", "火车车票", "动车车票",
            "飞机票款", "机票费用", "机场大巴", "机场停车", "租车出行", "租车费用", "共享单车费", "单车骑行",
            "电动车出行", "过路桥费", "高速通行费", "停车缴费", "停车场费", "交通卡充值"
        ),
        "爱车养车" to listOf(
            "汽车加油", "加油付款", "油站加油", "汽油费", "柴油费", "新能源充电", "汽车充电", "充电桩费",
            "充电服务费", "车库租金", "洗车服务", "洗车美容", "汽车保养", "保养维修", "汽修厂", "汽车维修",
            "修车费用", "轮胎更换", "轮胎维修", "机油保养", "汽车保险", "交强险", "车船税", "车辆年检",
            "违章罚款", "汽车配件", "车载用品", "拖车服务", "道路救援"
        ),
        "充值缴费" to listOf(
            "手机话费", "电话话费", "通讯费用", "充值话费", "手机流量", "流量套餐", "流量充值", "宽带缴费",
            "网络宽带", "宽带安装", "水费缴费", "电费缴费", "燃气缴费", "天然气费", "水电煤费", "暖气缴费",
            "供暖费用", "有线电视费", "电视会员", "视频订阅", "音乐订阅", "云盘会员", "会员自动续费", "软件续费",
            "生活缴费", "物业缴费", "话费账单", "宽带账单", "自动扣费"
        ),
        "服饰装扮" to listOf(
            "买衣服", "购买服装", "衣服裤子", "鞋子购买", "买运动鞋", "买靴子", "箱包购买", "买背包", "买钱包",
            "内衣内裤", "袜子购买", "帽子购买", "围巾手套", "服装配饰", "珠宝首饰", "手表购买", "眼镜购买",
            "美妆购买", "化妆用品", "护肤用品", "香水购买", "防晒用品", "理发消费", "发型设计", "服装店消费"
        ),
        "家居家装" to listOf(
            "家居用品", "家用家具", "家具购买", "家纺用品", "床上用品", "床垫购买", "沙发购买", "桌椅购买",
            "窗帘购买", "五金用品", "建材购买", "装修费用", "装修材料", "家装设计", "日用杂货", "生活用品",
            "厨具用品", "餐具购买", "厨房用品", "收纳用品", "清洁用品", "纸巾购买", "洗衣用品", "垃圾袋",
            "灯具购买", "插座开关", "家电维修", "家电购买", "超市日用品"
        ),
        "数码电器" to listOf(
            "电子产品", "数码产品", "手机购买", "手机换机", "手机维修", "手机配件", "手机壳膜", "电脑购买",
            "笔记本电脑", "电脑配件", "平板电脑", "平板配件", "耳机购买", "蓝牙耳机", "充电设备", "数据线购买",
            "相机购买", "摄影器材", "键盘鼠标", "显示器购买", "硬盘购买", "打印设备", "智能家电", "电视购买",
            "冰箱购买", "洗衣机购买", "空调购买", "游戏主机", "办公电子"
        ),
        "运动户外" to listOf(
            "运动消费", "健身房卡", "健身私教", "瑜伽课程", "游泳课程", "羽毛球费", "篮球活动", "足球活动",
            "网球活动", "跑步装备", "骑行活动", "自行车购买", "户外活动", "露营装备", "徒步活动", "爬山登山",
            "滑雪活动", "钓鱼装备", "运动服装", "运动鞋购买", "体育场馆", "球场费用"
        ),
        "美容美发" to listOf(
            "美容消费", "美发消费", "理发消费", "剪头发", "烫发消费", "染发消费", "洗剪吹", "护肤消费",
            "美容护理", "美容院消费", "美甲消费", "美睫消费", "纹眉消费", "脱毛消费", "按摩消费", "足疗消费",
            "洗浴消费", "洗发水购买", "沐浴用品", "面膜护肤", "口腔清洁", "牙刷牙膏"
        ),
        "母婴亲子" to listOf(
            "宝宝用品", "婴儿用品", "婴儿奶粉", "宝宝辅食", "纸尿裤", "尿不湿", "拉拉裤", "母婴食品",
            "宝宝衣服", "儿童服装", "儿童鞋子", "儿童玩具", "益智玩具", "积木玩具", "绘本图书", "早教课程",
            "亲子活动", "托儿服务", "育儿服务", "产检费用", "婴儿车", "儿童座椅"
        ),
        "住房物业" to listOf(
            "住房费用", "房屋租金", "租房费用", "租房押金", "房贷月供", "房贷还款", "按揭还款", "物业管理费",
            "物业维修费", "房屋维修", "家装维修", "水电房租", "小区物业", "车位租金", "车位管理费", "公寓租赁",
            "宿舍租金", "中介服务费", "房屋中介", "房屋保险"
        ),
        "酒店旅游" to listOf(
            "旅行消费", "旅游费用", "出差住宿", "酒店预订", "酒店住宿费", "民宿预订", "宾馆住宿", "景区门票",
            "景区消费", "旅游门票", "旅行团费", "跟团费用", "自由行费用", "机票购买", "火车票购买", "租车费用",
            "导游服务", "签证服务", "旅游保险", "行李托运", "纪念品购买", "特产购买", "游乐园门票"
        ),
        "文化休闲" to listOf(
            "休闲娱乐", "娱乐消费", "电影票", "电影院消费", "演出票", "演唱会票", "话剧门票", "音乐会票",
            "展览门票", "博物馆门票", "书店购书", "阅读消费", "杂志购买", "桌游消费", "密室消费", "剧本杀消费",
            "游戏充值", "游戏点券", "游戏道具", "网吧上网", "视频会员", "音乐会员", "直播打赏", "拍照写真"
        ),
        "教育培训" to listOf(
            "教育费用", "学费缴纳", "教材购买", "学习资料", "培训课程", "辅导课程", "补习费用", "家教费用",
            "驾校报名", "驾照考试", "职业培训", "技能课程", "编程课程", "画画课程", "音乐课程", "钢琴课程",
            "舞蹈课程", "考试报名", "考研报名", "公务员考试", "英语培训", "网课购买", "在线教育"
        ),
        "医疗健康" to listOf(
            "医疗费用", "看病花费", "医院缴费", "门诊费用", "住院费用", "挂号费用", "药品购买", "药店消费",
            "感冒用药", "处方药品", "中药购买", "西药购买", "体检费用", "疫苗费用", "核酸检测", "牙科治疗",
            "补牙费用", "洗牙费用", "配眼镜", "眼镜费用", "保健食品", "营养补充", "理疗费用", "针灸费用"
        ),
        "生活服务" to listOf(
            "生活服务费", "家政服务", "保洁服务", "钟点工费", "保姆费用", "家电清洗", "维修服务", "上门维修",
            "快递寄件", "快递运费", "跑腿服务", "洗衣服务", "干洗服务", "修鞋服务", "配钥匙", "开锁服务",
            "搬家服务", "搬家运输", "宠物服务", "宠物看病", "宠物美容", "宠物寄养", "摄影服务", "打印服务"
        ),
        "人情往来" to listOf(
            "人情消费", "礼金支出", "随礼支出", "份子钱", "婚礼随礼", "结婚礼金", "生日礼物", "生日红包",
            "节日礼物", "节日红包", "长辈礼物", "孝敬父母", "孩子红包", "压岁钱", "请客吃饭", "宴请朋友",
            "聚会消费", "团建费用", "送礼消费", "礼品购买", "探亲礼物"
        ),
        "投资理财" to listOf(
            "投资费用", "理财投资", "基金购买", "基金定投", "基金申购", "基金赎回", "股票购买", "股票交易",
            "证券交易", "黄金投资", "黄金购买", "银行理财", "理财产品", "存款办理", "定期存款", "定投计划",
            "保险缴费", "商业保险", "外汇交易", "数字货币", "交易手续费", "证券开户", "投资本金", "资产配置"
        ),
        "工资" to listOf(
            "薪资到账", "工资收入", "月薪到账", "发薪日", "工资发放", "工资奖金", "绩效奖金", "年终奖金",
            "季度奖金", "加班工资", "津贴收入", "补贴收入", "餐补收入", "房补收入", "通讯补贴", "全勤奖励",
            "岗位补助", "工资结算", "劳务工资", "薪酬到账"
        ),
        "红包转账" to listOf(
            "收款到账", "转账到账", "微信收款", "支付宝收款", "银行卡转账", "亲友转账", "家人转账", "朋友转账",
            "红包收入", "收到红包", "节日红包收入", "生日红包收入", "礼金收入", "婚礼礼金", "压岁钱到账", "退款转账",
            "代收款", "借款收回", "还款收入", "收回借款"
        ),
        "理财收益" to listOf(
            "理财收益到账", "基金收益", "基金分红", "股票分红", "股票收益", "股票卖出盈利", "利息到账", "银行存款利息",
            "定期利息", "余额宝收益", "债券利息", "国债收益", "投资回报", "投资收益到账", "理财赎回", "基金赎回到账",
            "分红到账", "收益结算", "本金利息"
        ),
        "兼职外快" to listOf(
            "兼职工资", "兼职结算", "副业收入", "外快收入", "接单收入", "订单结算", "项目收入", "项目结款",
            "设计稿费", "稿酬收入", "写作稿费", "翻译稿费", "开发报酬", "咨询服务费", "劳务收入", "直播收入",
            "直播带货", "销售佣金", "推广佣金", "二手出售", "闲置卖出", "租赁收入"
        ),
        "退款" to listOf(
            "退款收入", "退款到账", "购物退款", "退货退款", "售后退款", "订单退款", "差价退款", "价保返还",
            "平台返现", "优惠返现", "押金退回", "押金返还", "报销款到账", "费用报销", "赔偿收入", "保险理赔",
            "退税收入", "税款退回", "撤销交易", "冲正收入"
        ),
        "其他" to listOf(
            "零用收入", "其他款项", "杂项收入", "临时收入", "意外收入", "中奖奖金", "彩票奖金", "抽奖奖金",
            "政府补贴", "补助到账", "补贴收入", "奖励金", "押金收入", "借款收入", "还款到账", "未分类收入"
        )
    )
}
