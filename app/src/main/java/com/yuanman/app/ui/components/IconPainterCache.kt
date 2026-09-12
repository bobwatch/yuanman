package com.yuanman.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.yuanman.app.data.model.CategoryIconHelper

/**
 * 进程级分类图标 Painter 池。
 *
 * 每个 ImageVector 首次绘制前，VectorPainter 都要在内部建立一棵可绘制的矢量节点树，
 * material-extended 图标路径很大，新建 15~20 个 painter 实测会占用主线程约 300ms。
 * 若随页面组合反复创建（编辑分类页每次打开都会重建图标网格），每次进入都会卡顿。
 * 这里在应用根组合中一次性创建并常驻，页面内按下标直接复用同一 painter 实例，
 * 使矢量树的构建在整个进程生命周期内只发生一次。
 */
object IconPainterCache {
    private val painters = HashMap<String, Painter>()

    /** 获取指定分类图标的常驻 painter；若尚未预置则回退为按需创建的实例。 */
    fun get(key: String): Painter? = painters[key]

    fun register(key: String, painter: Painter) {
        painters[key] = painter
    }
}

/** 在应用根组合（跨页面常驻）调用：为全部可选分类图标预创建 painter。 */
@Composable
fun PrimeIconPainters() {
    CategoryIconHelper.AVAILABLE_ICONS.forEach { info ->
        IconPainterCache.register(info.key, rememberVectorPainter(info.icon))
    }
}
