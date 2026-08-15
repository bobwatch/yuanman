package com.moneyhistory.app.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneyhistory.app.R

// 按键圆角：与主题控件圆角（small 12dp / medium 16dp）取中，配合全局 8dp 间距
private val KeyShape = RoundedCornerShape(14.dp)

/**
 * 计算器式九宫格数字键盘（自绘，不弹系统软键盘）。
 * 按键：1-9、0、小数点、退格 ⌫（长按清空整笔金额）。
 * 顶部：传入 [onCollapse] 时显示细收起条（点击收起键盘，由调用方控制显隐）。
 * 底部一行：左侧「＋ 连加」（当前段为空时禁用，避免点了没反应），右侧可放 [footer]。
 * 所有按键 48dp 高，满足触摸目标下限。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NumPad(
    onKey: (String) -> Unit,
    modifier: Modifier = Modifier,
    onCollapse: (() -> Unit)? = null,
    plusEnabled: Boolean = true,
    onClearAll: (() -> Unit)? = null,
    footer: @Composable RowScope.() -> Unit = {}
) {
    // 按键轻震动：数字输入是高频操作，触感确认比纯视觉反馈更跟手
    val view = LocalView.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (onCollapse != null) {
            // 键盘顶部细条：点中间箭头收起键盘（整条 48dp 高，满足触达下限）
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable(onClick = onCollapse),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.numpad_collapse),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf(".", "0", "⌫")
        )
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { key ->
                    NumKey(
                        label = key,
                        // 退格键是符号文本，读屏按语义读「删除」而不是读「⌫」；
                        // 长按清空是计算器式快捷操作，同键上给出人话标签
                        description = if (key == "⌫") {
                            stringResource(R.string.numpad_backspace)
                        } else {
                            null
                        },
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onKey(key)
                        },
                        onLongPress = if (key == "⌫") onClearAll else null,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        // 底部一行：「＋ 连加」与「保存」并排
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = KeyShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(
                    alpha = if (plusEnabled) 1f else 0.4f
                ),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(
                    alpha = if (plusEnabled) 1f else 0.4f
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    // clip 必须在 clickable 之前：Surface 的圆角只裁背景，
                    // 不裁修饰符水波纹，不先裁剪的话按下去是方形涟漪
                    .clip(KeyShape)
                    .clickable(enabled = plusEnabled) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onKey("+")
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.numpad_plus),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            footer()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun NumKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    onLongPress: (() -> Unit)? = null
) {
    // 长按（清空）是低频快捷操作，用 LONG_PRESS 触感与普通点按区分
    val view = LocalView.current
    Surface(
        shape = KeyShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        // 符号键（⌫）清掉内部文本语义、换成人话标签，其余键照常
        modifier = modifier
            .height(48.dp)
            .clip(KeyShape)
            .then(
                if (description != null) {
                    Modifier.clearAndSetSemantics {
                        this.contentDescription = description
                    }
                } else {
                    Modifier
                }
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress?.let { lp ->
                    {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        lp()
                    }
                },
                onClickLabel = description,
                onLongClickLabel = if (onLongPress != null && description != null) {
                    stringResource(R.string.numpad_clear_all)
                } else {
                    null
                }
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                fontSize = 20.sp
            )
        }
    }
}
