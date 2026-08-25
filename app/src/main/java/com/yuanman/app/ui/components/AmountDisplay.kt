package com.yuanman.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.yuanman.app.data.model.RecordType
import com.yuanman.app.ui.theme.ExpenseColorDark
import com.yuanman.app.ui.theme.ExpenseColorLight
import com.yuanman.app.ui.theme.IncomeColorDark
import com.yuanman.app.ui.theme.IncomeColorLight
import com.yuanman.app.utils.MoneyUtils

@Composable
fun AmountDisplay(
    amountInCents: Long,
    type: RecordType? = null,
    showSign: Boolean = true,
    fontSize: TextUnit = 18.sp,
    modifier: Modifier = Modifier,
    customColor: Color? = null,
    fontWeight: FontWeight = FontWeight.SemiBold,
    isPrivacyMode: Boolean = false
) {
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f

    val color = customColor ?: when (type) {
        RecordType.EXPENSE -> if (isDark) ExpenseColorDark else ExpenseColorLight
        RecordType.INCOME -> if (isDark) IncomeColorDark else IncomeColorLight
        null -> MaterialTheme.colorScheme.onSurface
    }

    val displayStr = if (isPrivacyMode) {
        when {
            showSign && type == RecordType.EXPENSE -> "-¥ ****"
            showSign && type == RecordType.INCOME -> "+¥ ****"
            else -> "¥ ****"
        }
    } else {
        MoneyUtils.formatCurrency(
            cents = amountInCents,
            showSign = showSign && type != null,
            isExpense = type == RecordType.EXPENSE,
            withGrouping = true
        )
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = displayStr,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = color
        )
    }
}
