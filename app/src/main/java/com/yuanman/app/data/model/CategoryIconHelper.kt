package com.yuanman.app.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

data class CategoryIconInfo(
    val key: String,
    val name: String,
    val icon: ImageVector
)

object CategoryIconHelper {
    val AVAILABLE_ICONS = listOf(
        CategoryIconInfo("food", "餐饮", Icons.Default.Restaurant),
        CategoryIconInfo("coffee", "饮品", Icons.Default.LocalCafe),
        CategoryIconInfo("snack", "零食", Icons.Default.Fastfood),
        CategoryIconInfo("traffic", "交通", Icons.Default.DirectionsCar),
        CategoryIconInfo("car_service", "加油/养车", Icons.Default.LocalGasStation),
        CategoryIconInfo("shopping", "购物", Icons.Default.ShoppingCart),
        CategoryIconInfo("clothes", "服饰", Icons.Default.Checkroom),
        CategoryIconInfo("beauty", "美妆护肤", Icons.Default.Face),
        CategoryIconInfo("entertainment", "娱乐", Icons.Default.SportsEsports),
        CategoryIconInfo("movie", "影音", Icons.Default.Movie),
        CategoryIconInfo("housing", "住房/房租", Icons.Default.Home),
        CategoryIconInfo("water_electric", "水电燃气", Icons.Default.Bolt),
        CategoryIconInfo("daily_necessities", "日用品", Icons.Default.ShoppingBag),
        CategoryIconInfo("medical", "医疗健康", Icons.Default.LocalHospital),
        CategoryIconInfo("education", "学习培训", Icons.Default.School),
        CategoryIconInfo("communication", "话费宽带", Icons.Default.PhoneAndroid),
        CategoryIconInfo("digital", "数码科技", Icons.Default.LaptopMac),
        CategoryIconInfo("pet", "萌宠宝贝", Icons.Default.Pets),
        CategoryIconInfo("baby", "母婴育儿", Icons.Default.ChildCare),
        CategoryIconInfo("fitness", "运动健身", Icons.Default.FitnessCenter),
        CategoryIconInfo("travel", "旅行出游", Icons.Default.Flight),
        CategoryIconInfo("book", "书籍阅读", Icons.Default.MenuBook),
        CategoryIconInfo("gift", "人情往来", Icons.Default.Redeem),
        CategoryIconInfo("salary", "工资薪酬", Icons.Default.AccountBalanceWallet),
        CategoryIconInfo("bonus", "奖金福利", Icons.Default.CardGiftcard),
        CategoryIconInfo("finance", "理财收益", Icons.Default.TrendingUp),
        CategoryIconInfo("part_time", "兼职副业", Icons.Default.Work),
        CategoryIconInfo("transfer", "转账转存", Icons.Default.SwapHoriz),
        CategoryIconInfo("other", "其他分类", Icons.Default.Category)
    )

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
     * 根据分类名称智能推荐常用快捷备注标签
     */
    fun getPresetRemarks(categoryName: String): List<String> {
        return when {
            categoryName.contains("餐") || categoryName.contains("吃") || categoryName.contains("饭") ->
                listOf("早餐", "午餐", "晚餐", "奶茶咖啡", "外卖", "超市买菜", "朋友聚餐", "夜宵")
            categoryName.contains("交") || categoryName.contains("车") || categoryName.contains("行") ->
                listOf("地铁", "打车", "公交", "加油", "停车费", "高铁/火车", "机票", "共享单车")
            categoryName.contains("购") || categoryName.contains("买") || categoryName.contains("物") ->
                listOf("日用品", "服饰鞋包", "零食水果", "网购快递", "电子数码", "家居好物", "美妆护肤")
            categoryName.contains("住") || categoryName.contains("房") ->
                listOf("房租", "物业费", "水电费", "燃气费", "宽带网络", "维修换新")
            categoryName.contains("娱") || categoryName.contains("玩") ->
                listOf("电影", "游戏充值", "KTV/聚会", "桌游密室", "门票演出", "剧本杀")
            categoryName.contains("医") || categoryName.contains("药") ->
                listOf("买药", "门诊挂号", "体检检查", "保健养生", "牙科")
            categoryName.contains("宠") ->
                listOf("猫粮狗粮", "宠物零食", "驱虫疫苗", "宠物洗护", "宠物玩具")
            categoryName.contains("学") || categoryName.contains("教") ->
                listOf("课程培训", "书籍教材", "考试报名", "文具用具", "会员订阅")
            categoryName.contains("工") || categoryName.contains("薪") ->
                listOf("基本工资", "绩效奖金", "年终奖", "公积金", "补贴福利")
            categoryName.contains("理财") || categoryName.contains("财") ->
                listOf("基金收益", "股票分红", "银行利息", "理财赎回")
            categoryName.contains("兼") || categoryName.contains("副") ->
                listOf("稿费兼职", "设计外包", "咨询收入", "二手闲置出清")
            categoryName.contains("人情") || categoryName.contains("礼") ->
                listOf("结婚随礼", "生日红包", "节日过节", "孝敬长辈", "请客送礼")
            else ->
                listOf("日常支出", "生活消费", "微信支付", "支付宝支付", "固定开销")
        }
    }
}

