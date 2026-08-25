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
            categoryName.contains("餐") || categoryName.contains("吃") || categoryName.contains("饭") ->
                listOf("早餐", "午餐", "晚餐", "奶茶咖啡", "外卖", "买菜做饭", "朋友聚餐", "夜宵")
            categoryName.contains("交") || categoryName.contains("车") || categoryName.contains("行") ->
                listOf("地铁", "打车", "公交", "加油", "停车费", "高铁火车", "飞机机票", "共享单车")
            categoryName.contains("购") || categoryName.contains("买") || categoryName.contains("物") ->
                listOf("日用品", "服饰鞋包", "零食水果", "网购快递", "电子数码", "家居好物", "美妆护肤")
            categoryName.contains("住") || categoryName.contains("房") ->
                listOf("房租", "物业费", "水电费", "燃气费", "宽带网络", "维修换新", "保洁")
            categoryName.contains("娱") || categoryName.contains("玩") ->
                listOf("电影", "游戏充值", "KTV聚会", "桌游密室", "门票演出", "剧本杀", "盲盒")
            categoryName.contains("医") || categoryName.contains("药") ->
                listOf("买药", "门诊挂号", "体检检查", "保健养生", "牙科", "眼科")
            categoryName.contains("宠") ->
                listOf("猫粮狗粮", "宠物零食", "驱虫疫苗", "宠物洗护", "宠物玩具", "看病就诊")
            categoryName.contains("学") || categoryName.contains("教") ->
                listOf("课程培训", "书籍教材", "考试报名", "文具用具", "会员订阅", "自习室")
            categoryName.contains("工") || categoryName.contains("薪") ->
                listOf("基本工资", "绩效奖金", "年终奖", "公积金", "餐补车补", "津贴福利")
            categoryName.contains("理财") || categoryName.contains("财") ->
                listOf("基金收益", "股票分红", "银行利息", "理财赎回", "加密货币")
            categoryName.contains("兼") || categoryName.contains("副") ->
                listOf("稿费兼职", "设计外包", "咨询收入", "二手闲置出清", "劳务报酬")
            categoryName.contains("人情") || categoryName.contains("礼") ->
                listOf("结婚随礼", "生日红包", "节日过节", "孝敬长辈", "请客送礼", "探病问候")
            else ->
                listOf("日常支出", "生活消费", "微信支付", "支付宝支付", "固定开销")
        }
    }
}
