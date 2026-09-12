@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.yuanman.app.ui.screens.account

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuanman.app.ui.components.YuanmanModalBottomSheet

/**
 * 对账周期选择 sheet（全局默认 / 账户自定义覆盖共用）。
 *
 * 周期 = 数量 × 单位，单位档：周 / 月 / 季度 / 半年 / 年（默认每月）。
 *  - 快捷档：每周、每 2 周、每月、每季度、每半年、每年；
 *  - 「自定义」：单位 chips 决定步进粒度，数量步进像进制一样可进位升位：
 *    月族以月为最小粒度存储，+12 个月自动升位为「每年」再继续累加，
 *    换单位 chip 时若当前月数能被该单位整除则等价换算（如 6 个月 ⇄ 1 个半年）。
 *  - 账户自定义场景首位提供 [跟随全局]（选中即清除覆盖，值 = null）。
 * 选择即回调（即改即存），底部「完成」仅收尾关闭。
 */
@Composable
fun ReconcileCycleSheet(
    title: String,
    current: ReconcileCycle?,
    globalCycle: ReconcileCycle,
    allowFollowGlobal: Boolean,
    onSelect: (ReconcileCycle?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 快捷档
    val presets = listOf(
        ReconcileCycle(1, ReconcileCycleUnit.WEEK),
        ReconcileCycle(2, ReconcileCycleUnit.WEEK),
        ReconcileCycle(1, ReconcileCycleUnit.MONTH),
        ReconcileCycle(1, ReconcileCycleUnit.QUARTER),
        ReconcileCycle(1, ReconcileCycleUnit.HALF_YEAR),
        ReconcileCycle(1, ReconcileCycleUnit.YEAR)
    )

    val effective = current ?: globalCycle
    val isWeekMode = effective.unit == ReconcileCycleUnit.WEEK

    // ---- 自定义编辑态：月族归一为「总月数」，周族为「总周数」，chip 只决定步进与展示粒度 ----
    var customExpanded by remember {
        mutableStateOf(
            current != null && presets.none { it.count == current.count && it.unit == current.unit }
        )
    }
    var chipUnit by remember {
        mutableStateOf(
            if (isWeekMode) ReconcileCycleUnit.WEEK else largestMonthUnitOf(effective.totalMonths)
        )
    }
    var customMonths by remember { mutableStateOf(if (isWeekMode) 1 else effective.totalMonths.coerceAtLeast(1)) }
    var customWeeks by remember { mutableStateOf(if (isWeekMode) effective.count else 1) }

    /** 当前自定义态对应的周期值 */
    fun customCycle(): ReconcileCycle = if (chipUnit == ReconcileCycleUnit.WEEK) {
        ReconcileCycle(customWeeks.coerceAtLeast(1), ReconcileCycleUnit.WEEK)
    } else {
        ReconcileCycle(
            (customMonths / chipUnit.monthSpan).coerceAtLeast(1),
            chipUnit
        )
    }

    /** 步进：±1 个当前 chip 单位；月族 +12 个月自动进位为「年」再继续累加 */
    fun bump(delta: Int) {
        if (chipUnit == ReconcileCycleUnit.WEEK) {
            customWeeks = (customWeeks + delta).coerceIn(1, 520)
        } else {
            val span = chipUnit.monthSpan
            var m = (customMonths + delta * span).coerceIn(1, 1200)
            // 升位：达到 12 个月整（如 月11→+1、半年2→+1、季度4→+1）自动进位为年
            if (m % 12 == 0 && (delta > 0 || chipUnit == ReconcileCycleUnit.YEAR)) {
                chipUnit = ReconcileCycleUnit.YEAR
                m = m
            }
            customMonths = m
        }
        onSelect(customCycle())
    }

    /** 切换单位 chip：周/月族互切重置为 1；月族内部仅当前总月数可整除时允许（等价换算） */
    fun switchChip(unit: ReconcileCycleUnit) {
        if (unit == ReconcileCycleUnit.WEEK) {
            chipUnit = ReconcileCycleUnit.WEEK
            customWeeks = 1
            onSelect(ReconcileCycle(1, ReconcileCycleUnit.WEEK))
            return
        }
        if (chipUnit == ReconcileCycleUnit.WEEK) {
            chipUnit = unit
            customMonths = unit.monthSpan // 从周切到月族：起始为该单位 1 个
            onSelect(ReconcileCycle(1, unit))
            return
        }
        if (customMonths % unit.monthSpan == 0) {
            chipUnit = unit
            onSelect(customCycle())
        }
    }

    val customCanStepDown: Boolean = if (chipUnit == ReconcileCycleUnit.WEEK) {
        customWeeks > 1
    } else {
        customMonths > chipUnit.monthSpan
    }

    YuanmanModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp)
            )
            Text(
                text = "超过设定周期仍未对账的账户，会进入「待核对」提醒",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.outline
            )

            // 跟随全局（仅账户自定义场景）
            if (allowFollowGlobal) {
                FilterChip(
                    selected = current == null,
                    onClick = { onSelect(null) },
                    shape = RoundedCornerShape(10.dp),
                    label = { Text("跟随全局 · ${globalCycle.label}") }
                )
            }

            // 快捷档 chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { preset ->
                    FilterChip(
                        selected = !customExpanded &&
                            current != null && current.count == preset.count && current.unit == preset.unit,
                        onClick = {
                            customExpanded = false
                            onSelect(preset)
                        },
                        shape = RoundedCornerShape(10.dp),
                        label = { Text(preset.label) }
                    )
                }
                FilterChip(
                    selected = customExpanded,
                    onClick = {
                        customExpanded = !customExpanded
                        if (customExpanded) onSelect(customCycle())
                    },
                    shape = RoundedCornerShape(10.dp),
                    label = { Text("自定义") }
                )
            }

            // 自定义区：单位 chips + 数量步进
            if (customExpanded) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ReconcileCycleUnit.entries.forEach { unit ->
                            val enabled = unit == ReconcileCycleUnit.WEEK ||
                                chipUnit == ReconcileCycleUnit.WEEK ||
                                customMonths % unit.monthSpan == 0
                            FilterChip(
                                selected = chipUnit == unit,
                                enabled = enabled,
                                onClick = { switchChip(unit) },
                                shape = RoundedCornerShape(10.dp),
                                label = { Text(unit.label) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { bump(-1) },
                        enabled = customCanStepDown,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "减少",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = customCycle().label,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    IconButton(
                        onClick = { bump(1) },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "增加",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // 当前生效周期实时展示
            val activeLabel = if (allowFollowGlobal && current == null) {
                "跟随全局（${globalCycle.label}）"
            } else if (customExpanded) {
                customCycle().label
            } else {
                (current ?: globalCycle).label
            }
            Text(
                text = "当前：$activeLabel",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Button(
                onClick = { onDismiss() },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("完成", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

/** 月族总月数 → 最大可整除展示单位（年 > 半年 > 季度 > 月），用于进入自定义时的默认粒度 */
private fun largestMonthUnitOf(months: Int): ReconcileCycleUnit {
    val m = months.coerceAtLeast(1)
    if (m % 12 == 0) return ReconcileCycleUnit.YEAR
    if (m % 6 == 0) return ReconcileCycleUnit.HALF_YEAR
    if (m % 3 == 0) return ReconcileCycleUnit.QUARTER
    return ReconcileCycleUnit.MONTH
}
