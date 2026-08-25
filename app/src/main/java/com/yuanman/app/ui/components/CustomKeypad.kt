package com.yuanman.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import java.math.RoundingMode

object KeypadEngine {

    /**
     * 计算算式表达式的值（支持 + -，如 "12.5+8-2.3"）
     */
    fun evaluateExpression(expr: String): BigDecimal? {
        val trimmed = expr.trim()
        if (trimmed.isEmpty()) return null

        // 如果最后一位是操作符，先剔除再算
        val cleanExpr = if (trimmed.endsWith("+") || trimmed.endsWith("-")) {
            trimmed.dropLast(1)
        } else {
            trimmed
        }

        if (cleanExpr.isEmpty()) return null

        return try {
            val tokens = mutableListOf<String>()
            var currentNum = StringBuilder()

            for (char in cleanExpr) {
                if (char == '+' || char == '-') {
                    if (currentNum.isNotEmpty()) {
                        tokens.add(currentNum.toString())
                        currentNum = StringBuilder()
                    }
                    tokens.add(char.toString())
                } else {
                    currentNum.append(char)
                }
            }
            if (currentNum.isNotEmpty()) {
                tokens.add(currentNum.toString())
            }

            if (tokens.isEmpty()) return null

            var result = BigDecimal(tokens[0])
            var i = 1
            while (i < tokens.size) {
                val op = tokens[i]
                if (i + 1 < tokens.size) {
                    val nextNum = BigDecimal(tokens[i + 1])
                    result = if (op == "+") result.add(nextNum) else result.subtract(nextNum)
                    i += 2
                } else {
                    break
                }
            }
            result.setScale(2, RoundingMode.HALF_UP)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 格式化 BigDecimal 为简洁展示字符串（如 20.00 -> "20", 20.50 -> "20.5"）
     */
    fun formatDecimal(bd: BigDecimal): String {
        val stripped = bd.stripTrailingZeros()
        return if (stripped.scale() <= 0) {
            stripped.toPlainString()
        } else {
            bd.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        }
    }
}

/**
 * 沉浸式大厂级记账计算器键盘
 */
@Composable
fun CustomKeypad(
    expression: String,
    onExpressionChange: (String) -> Unit,
    onComplete: () -> Unit,
    onSaveAndContinue: (() -> Unit)? = null,
    isEditMode: Boolean = false,
    modifier: Modifier = Modifier,
    hapticEnabled: Boolean = true
) {
    val haptic = LocalHapticFeedback.current

    fun performHaptic() {
        if (hapticEnabled) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    fun handleKeyPress(key: String) {
        performHaptic()
        val current = expression

        when (key) {
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9" -> {
                // 找到当前最后一个数字段
                val lastOpIndex = maxOf(current.lastIndexOf('+'), current.lastIndexOf('-'))
                val currentSegment = if (lastOpIndex >= 0) current.substring(lastOpIndex + 1) else current

                // 限制单段最多两位小数
                if (currentSegment.contains(".")) {
                    val dotIndex = currentSegment.indexOf('.')
                    val decimals = currentSegment.substring(dotIndex + 1)
                    if (decimals.length >= 2) return
                }

                // 避免 "00" 前导 0
                if (currentSegment == "0") {
                    val prefix = if (lastOpIndex >= 0) current.substring(0, lastOpIndex + 1) else ""
                    onExpressionChange(prefix + key)
                } else {
                    if (currentSegment.length < 9) { // 限制单笔最高千万级
                        onExpressionChange(current + key)
                    }
                }
            }

            "." -> {
                val lastOpIndex = maxOf(current.lastIndexOf('+'), current.lastIndexOf('-'))
                val currentSegment = if (lastOpIndex >= 0) current.substring(lastOpIndex + 1) else current

                if (!currentSegment.contains(".")) {
                    if (currentSegment.isEmpty()) {
                        onExpressionChange(current + "0.")
                    } else {
                        onExpressionChange(current + ".")
                    }
                }
            }

            "+", "-" -> {
                if (current.isEmpty()) return
                if (current.endsWith("+") || current.endsWith("-") || current.endsWith(".")) {
                    onExpressionChange(current.dropLast(1) + key)
                } else {
                    // 如果已经包含一个运算符，先算出结果再追加运算符
                    if (current.contains("+") || current.contains("-")) {
                        val result = KeypadEngine.evaluateExpression(current)
                        if (result != null && result > BigDecimal.ZERO) {
                            onExpressionChange(KeypadEngine.formatDecimal(result) + key)
                            return
                        }
                    }
                    onExpressionChange(current + key)
                }
            }

            "DELETE" -> {
                if (current.isNotEmpty()) {
                    onExpressionChange(current.dropLast(1))
                }
            }

            "CLEAR" -> {
                onExpressionChange("")
            }

            "=" -> {
                val result = KeypadEngine.evaluateExpression(current)
                if (result != null && result > BigDecimal.ZERO) {
                    onExpressionChange(KeypadEngine.formatDecimal(result))
                }
            }
        }
    }

    val hasOperator = expression.contains("+") || expression.contains("-")

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Row 1: 7, 8, 9, +
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                KeypadButton("7", Modifier.weight(1f)) { handleKeyPress("7") }
                KeypadButton("8", Modifier.weight(1f)) { handleKeyPress("8") }
                KeypadButton("9", Modifier.weight(1f)) { handleKeyPress("9") }
                KeypadOpButton("+", Modifier.weight(1f)) { handleKeyPress("+") }
            }

            // Row 2: 4, 5, 6, -
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                KeypadButton("4", Modifier.weight(1f)) { handleKeyPress("4") }
                KeypadButton("5", Modifier.weight(1f)) { handleKeyPress("5") }
                KeypadButton("6", Modifier.weight(1f)) { handleKeyPress("6") }
                KeypadOpButton("-", Modifier.weight(1f)) { handleKeyPress("-") }
            }

            // Row 3: 1, 2, 3, Delete (长按清空)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                KeypadButton("1", Modifier.weight(1f)) { handleKeyPress("1") }
                KeypadButton("2", Modifier.weight(1f)) { handleKeyPress("2") }
                KeypadButton("3", Modifier.weight(1f)) { handleKeyPress("3") }
                KeypadDeleteButton(
                    modifier = Modifier.weight(1f),
                    onClick = { handleKeyPress("DELETE") },
                    onLongClick = { handleKeyPress("CLEAR") }
                )
            }

            // Row 4: ., 0, 再记一笔/00, 完成/=
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                KeypadButton(".", Modifier.weight(1f)) { handleKeyPress(".") }
                KeypadButton("0", Modifier.weight(1f)) { handleKeyPress("0") }

                if (onSaveAndContinue != null && !isEditMode) {
                    // 再记一笔 按钮
                    KeypadActionButton(
                        text = "再记一笔",
                        isPrimary = false,
                        modifier = Modifier.weight(1.1f),
                        onClick = {
                            performHaptic()
                            if (hasOperator) {
                                val result = KeypadEngine.evaluateExpression(expression)
                                if (result != null && result > BigDecimal.ZERO) {
                                    onExpressionChange(KeypadEngine.formatDecimal(result))
                                }
                            }
                            onSaveAndContinue()
                        }
                    )
                } else {
                    KeypadButton("00", Modifier.weight(1f)) {
                        if (expression.isNotEmpty() && !expression.endsWith("+") && !expression.endsWith("-")) {
                            handleKeyPress("0")
                            handleKeyPress("0")
                        }
                    }
                }

                // 主动作按键：如果有算式运算符，则为 "="；否则为 "完成"
                if (hasOperator) {
                    KeypadActionButton(
                        text = "=",
                        isPrimary = true,
                        modifier = Modifier.weight(1.1f),
                        onClick = { handleKeyPress("=") }
                    )
                } else {
                    KeypadActionButton(
                        text = if (isEditMode) "修改" else "完成",
                        isPrimary = true,
                        modifier = Modifier.weight(1.1f),
                        onClick = {
                            performHaptic()
                            onComplete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun KeypadButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun KeypadOpButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun KeypadDeleteButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Backspace,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun KeypadActionButton(
    text: String,
    isPrimary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = if (text.length > 2) 15.sp else 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (isPrimary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}
