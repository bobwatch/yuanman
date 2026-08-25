package com.yuanman.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuanman.app.utils.DateTimeUtils
import com.yuanman.app.utils.MoneyUtils

@Composable
fun DateGroupHeader(
    timestamp: Long,
    totalExpense: Long,
    totalIncome: Long,
    modifier: Modifier = Modifier
) {
    val dateWithWeek = DateTimeUtils.formatMonthDayWithWeek(timestamp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = dateWithWeek,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (totalExpense > 0L) {
                Text(
                    text = "支 ${MoneyUtils.centsToYuanString(totalExpense)}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                )
            }
            if (totalIncome > 0L) {
                Text(
                    text = "收 ${MoneyUtils.centsToYuanString(totalIncome)}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                )
            }
        }
    }
}
