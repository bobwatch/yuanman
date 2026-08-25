package com.yuanman.app.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

data class CategoryIconInfo(
    val key: String,
    val name: String,
    val group: String,
    val icon: ImageVector
)

object CategoryIconHelper {
    val AVAILABLE_ICONS = listOf(
        // 🍔 餐饮美食
        CategoryIconInfo("food", "餐饮", "餐饮", Icons.Default.Restaurant),
        CategoryIconInfo("lunch", "早午餐", "餐饮", Icons.Default.LunchDining),
        CategoryIconInfo("dinner", "正餐晚餐", "餐饮", Icons.Default.DinnerDining),
        CategoryIconInfo("coffee", "咖啡茶饮", "餐饮", Icons.Default.LocalCafe),
        CategoryIconInfo("tea", "奶茶甜饮", "餐饮", Icons.Default.EmojiFoodBeverage),
        CategoryIconInfo("snack", "快餐小吃", "餐饮", Icons.Default.Fastfood),
        CategoryIconInfo("pizza", "西餐披萨", "餐饮", Icons.Default.LocalPizza),
        CategoryIconInfo("ramen", "面条拉面", "餐饮", Icons.Default.RamenDining),
        CategoryIconInfo("bakery", "面包烘焙", "餐饮", Icons.Default.BakeryDining),
        CategoryIconInfo("cake", "甜品蛋糕", "餐饮", Icons.Default.Cake),
        CategoryIconInfo("icecream", "冷饮雪糕", "餐饮", Icons.Default.Icecream),
        CategoryIconInfo("bar", "酒吧夜宵", "餐饮", Icons.Default.LocalBar),
        CategoryIconInfo("kitchen", "买菜做饭", "餐饮", Icons.Default.Kitchen),

        // 🚗 交通出行
        CategoryIconInfo("traffic", "汽车交通", "交通", Icons.Default.DirectionsCar),
        CategoryIconInfo("subway", "地铁轻轨", "交通", Icons.Default.Subway),
        CategoryIconInfo("bus", "公共汽车", "交通", Icons.Default.DirectionsBus),
        CategoryIconInfo("taxi", "出租打车", "交通", Icons.Default.LocalTaxi),
        CategoryIconInfo("train", "火车高铁", "交通", Icons.Default.Train),
        CategoryIconInfo("flight", "飞机航班", "交通", Icons.Default.Flight),
        CategoryIconInfo("gas", "加油加气", "交通", Icons.Default.LocalGasStation),
        CategoryIconInfo("parking", "停车费用", "交通", Icons.Default.LocalParking),
        CategoryIconInfo("bike", "单车骑行", "交通", Icons.Default.PedalBike),
        CategoryIconInfo("motor", "电动摩托", "交通", Icons.Default.TwoWheeler),
        CategoryIconInfo("ship", "轮船航运", "交通", Icons.Default.DirectionsBoat),
        CategoryIconInfo("repair", "养车维修", "交通", Icons.Default.Build),

        // 🛍️ 购物百货
        CategoryIconInfo("shopping", "日常购物", "购物", Icons.Default.ShoppingCart),
        CategoryIconInfo("shopping_bag", "商场百货", "购物", Icons.Default.ShoppingBag),
        CategoryIconInfo("store", "便利超市", "购物", Icons.Default.Storefront),
        CategoryIconInfo("clothes", "服饰鞋帽", "购物", Icons.Default.Checkroom),
        CategoryIconInfo("beauty", "美妆护肤", "购物", Icons.Default.Face),
        CategoryIconInfo("jewelry", "珠宝首饰", "购物", Icons.Default.Diamond),
        CategoryIconInfo("digital", "电脑数码", "购物", Icons.Default.LaptopMac),
        CategoryIconInfo("phone", "手机通讯", "购物", Icons.Default.PhoneAndroid),
        CategoryIconInfo("watch", "手表配饰", "购物", Icons.Default.Watch),
        CategoryIconInfo("delivery", "网购快递", "购物", Icons.Default.LocalShipping),
        CategoryIconInfo("tv", "家电数码", "购物", Icons.Default.Tv),

        // 🏠 居家生活
        CategoryIconInfo("housing", "住房房租", "居家", Icons.Default.Home),
        CategoryIconInfo("water_electric", "水电燃气", "居家", Icons.Default.Bolt),
        CategoryIconInfo("heating", "供暖煤气", "居家", Icons.Default.Whatshot),
        CategoryIconInfo("wifi", "宽带网络", "居家", Icons.Default.Wifi),
        CategoryIconInfo("cleaning", "家政保洁", "居家", Icons.Default.CleaningServices),
        CategoryIconInfo("furniture", "家居家装", "居家", Icons.Default.Weekend),
        CategoryIconInfo("bed", "床上用品", "居家", Icons.Default.Bed),
        CategoryIconInfo("key", "物业钥匙", "居家", Icons.Default.Key),
        CategoryIconInfo("repair_home", "房屋修缮", "居家", Icons.Default.HomeRepairService),
        CategoryIconInfo("pet", "萌宠宝贝", "居家", Icons.Default.Pets),
        CategoryIconInfo("baby", "母婴育儿", "居家", Icons.Default.ChildCare),
        CategoryIconInfo("toy", "玩具潮玩", "居家", Icons.Default.SmartToy),

        // 🎮 休闲娱乐
        CategoryIconInfo("entertainment", "休闲玩乐", "娱乐", Icons.Default.SportsEsports),
        CategoryIconInfo("movie", "影音院线", "娱乐", Icons.Default.Movie),
        CategoryIconInfo("music", "音乐演出", "娱乐", Icons.Default.Headphones),
        CategoryIconInfo("fitness", "运动健身", "娱乐", Icons.Default.FitnessCenter),
        CategoryIconInfo("travel", "旅游出游", "娱乐", Icons.Default.Landscape),
        CategoryIconInfo("camping", "户外露营", "娱乐", Icons.Default.Forest),
        CategoryIconInfo("ball", "球类运动", "娱乐", Icons.Default.SportsSoccer),
        CategoryIconInfo("swimming", "游泳水上", "娱乐", Icons.Default.Pool),
        CategoryIconInfo("party", "聚会桌游", "娱乐", Icons.Default.Casino),
        CategoryIconInfo("camera", "摄影摄像", "娱乐", Icons.Default.PhotoCamera),
        CategoryIconInfo("ticket", "演出门票", "娱乐", Icons.Default.ConfirmationNumber),
        CategoryIconInfo("theater", "戏剧展览", "娱乐", Icons.Default.TheaterComedy),

        // 💊 医疗健康
        CategoryIconInfo("medical", "医院门诊", "医疗", Icons.Default.LocalHospital),
        CategoryIconInfo("medicine", "药品购买", "医疗", Icons.Default.Medication),
        CategoryIconInfo("health", "体检保健", "医疗", Icons.Default.MonitorHeart),
        CategoryIconInfo("dental", "口腔牙科", "医疗", Icons.Default.HealthAndSafety),
        CategoryIconInfo("spa", "养生推拿", "医疗", Icons.Default.Spa),

        // 📚 学习办公
        CategoryIconInfo("education", "学校教育", "学习", Icons.Default.School),
        CategoryIconInfo("book", "书籍阅读", "学习", Icons.Default.MenuBook),
        CategoryIconInfo("training", "考证培训", "学习", Icons.Default.EditNote),
        CategoryIconInfo("stationery", "文具用具", "学习", Icons.Default.DesignServices),
        CategoryIconInfo("work", "办公工作", "学习", Icons.Default.Work),
        CategoryIconInfo("software", "会员订阅", "学习", Icons.Default.WorkspacePremium),

        // 🎁 人情往来
        CategoryIconInfo("gift", "人情随礼", "人情", Icons.Default.Redeem),
        CategoryIconInfo("card_gift", "礼物赠送", "人情", Icons.Default.CardGiftcard),
        CategoryIconInfo("family", "孝敬长辈", "人情", Icons.Default.Groups),
        CategoryIconInfo("celebration", "庆典生日", "人情", Icons.Default.Celebration),
        CategoryIconInfo("love", "慈善公益", "人情", Icons.Default.VolunteerActivism),

        // 💰 收入与资产
        CategoryIconInfo("salary", "工资薪酬", "收入", Icons.Default.AccountBalanceWallet),
        CategoryIconInfo("bonus", "奖金分红", "收入", Icons.Default.Paid),
        CategoryIconInfo("finance", "理财收益", "收入", Icons.Default.TrendingUp),
        CategoryIconInfo("part_time", "兼职副业", "收入", Icons.Default.MonetizationOn),
        CategoryIconInfo("refund", "报销退款", "收入", Icons.AutoMirrored.Filled.ReceiptLong),
        CategoryIconInfo("savings", "储蓄结余", "收入", Icons.Default.Savings),
        CategoryIconInfo("transfer", "转账还款", "收入", Icons.Default.SwapHoriz),
        CategoryIconInfo("bank", "银行理财", "收入", Icons.Default.AccountBalance),
        CategoryIconInfo("other", "其他分类", "其他", Icons.Default.Category)
    )

    val ICON_GROUPS = listOf("全部", "餐饮", "交通", "购物", "居家", "娱乐", "医疗", "学习", "人情", "收入")

    val PRESET_COLORS = listOf(
        0xFFFF5722, // 橙红
        0xFF4CAF50, // 翡翠绿
        0xFF2196F3, // 蔚蓝
        0xFFFF9800, // 活力橙
        0xFF9C27B0, // 优雅紫
        0xFFE91E63, // 珊瑚粉
        0xFF009688, // 墨玉青
        0xFF3F51B5, // 靛青蓝
        0xFF795548, // 暖棕
        0xFF607D8B, // 极简灰
        0xFFFFB300, // 晨曦金
        0xFF00BCD4, // 晴空青
        0xFF8BC34A, // 嫩芽绿
        0xFF673AB7, // 深空紫
        0xFFFF7043, // 蜜桃橘
        0xFF26A69A  // 薄荷绿
    )

    fun getIcon(key: String): ImageVector {
        return AVAILABLE_ICONS.find { it.key == key }?.icon ?: Icons.Default.Category
    }

    /**
     * 根据分类名称智能推荐常用快捷子标签
     */
    fun getPresetRemarks(categoryName: String): List<String> {
        return when {
            categoryName.contains("餐饮") || categoryName.contains("美食") || categoryName.contains("吃") || categoryName.contains("饭") ->
                listOf("早餐", "午餐", "晚餐", "外卖", "奶茶咖啡", "水果零食", "聚餐夜宵", "买菜食材")
            categoryName.contains("爱车") || categoryName.contains("养车") || categoryName.contains("加油") ->
                listOf("加油充电", "停车费", "洗车美容", "保养维修", "车辆保险", "车品配饰")
            categoryName.contains("交通") || categoryName.contains("出行") ->
                listOf("地铁", "公交", "打车网约车", "高铁火车", "机票飞机", "共享单车", "过路费")
            categoryName.contains("充值") || categoryName.contains("缴费") ->
                listOf("手机话费", "宽带网费", "水费", "电费", "燃气费", "会员充值")
            categoryName.contains("服饰") || categoryName.contains("装扮") ->
                listOf("衣服裤装", "鞋靴箱包", "内衣配饰", "帽子手套", "手表珠宝")
            categoryName.contains("家居") || categoryName.contains("家装") ->
                listOf("家具家纺", "五金建材", "家装软装", "日用百货", "厨具餐具", "收纳整理")
            categoryName.contains("数码") || categoryName.contains("电器") ->
                listOf("手机数码", "电脑办公", "智能家电", "数码配件", "摄影器材", "影音娱乐")
            categoryName.contains("运动") || categoryName.contains("户外") ->
                listOf("健身打卡", "球类运动", "户外露营", "徒步骑行", "运动装备", "场馆门票")
            categoryName.contains("美容") || categoryName.contains("美发") ->
                listOf("美发理发", "护肤美妆", "美甲美睫", "美容SPA", "个护清洁")
            categoryName.contains("母婴") || categoryName.contains("亲子") ->
                listOf("奶粉辅食", "纸尿裤", "童装童鞋", "玩具绘本", "早教亲子", "产检育儿")
            categoryName.contains("住房") || categoryName.contains("物业") ->
                listOf("房屋租金", "房贷月供", "物业管理费", "房屋修缮", "车位租金")
            categoryName.contains("酒店") || categoryName.contains("旅游") ->
                listOf("酒店住宿", "景点门票", "跟团旅游", "自由行", "度假旅行", "旅行装备")
            categoryName.contains("文化") || categoryName.contains("休闲") ->
                listOf("电影院线", "剧场演出", "展览看展", "书店阅读", "桌游密室", "游戏充值")
            categoryName.contains("教育") || categoryName.contains("培训") ->
                listOf("学费教材", "职业考证", "技能培训", "语言学习", "在线课程", "兴趣辅导")
            categoryName.contains("医疗") || categoryName.contains("健康") ->
                listOf("门诊挂号", "西药中药", "体检筛查", "疫苗接种", "牙科齿科", "保健补剂")
            categoryName.contains("生活") || categoryName.contains("服务") ->
                listOf("家政保洁", "快递跑腿", "干洗修鞋", "搬家拉货", "宠物服务", "废品回收")
            categoryName.contains("人情") || categoryName.contains("往来") || categoryName.contains("礼") ->
                listOf("礼金随礼", "节日红包", "长辈孝敬", "晚辈压岁", "请客送礼", "聚会买单")
            categoryName.contains("投资") || categoryName.contains("理财收益") || categoryName.contains("理财") ->
                listOf("基金买入", "股票证券", "黄金理财", "定投定存", "商业保险", "外汇理财")
            categoryName.contains("工资") || categoryName.contains("薪") ->
                listOf("基本月薪", "绩效奖金", "岗位津贴", "年终奖", "全勤奖", "餐补房补")
            categoryName.contains("红包") || categoryName.contains("转账") ->
                listOf("微信红包", "支付宝转账", "节日长辈红包", "生日礼物红包", "亲友转账")
            categoryName.contains("兼职") || categoryName.contains("外快") ->
                listOf("副业兼职", "设计外包", "投稿稿酬", "劳务咨询", "私域带货", "闲置出清")
            categoryName.contains("退款") ->
                listOf("网购退款", "差价返还", "押金退还", "活动返现", "退税入账")
            else ->
                listOf("日常支出", "生活消费", "微信支付", "支付宝支付", "固定开销")
        }
    }
}
