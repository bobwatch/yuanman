package com.yuanman.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Calendar
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YuanmanDatePickerSheet(
    initialDateMillis: Long,
    maxDateMillis: Long = System.currentTimeMillis(),
    onDateSelected: (year: Int, month: Int, day: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val maxDate = remember(maxDateMillis) { maxDateMillis }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDateMillis.coerceAtMost(maxDate),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= maxDate
        }
    )

    YuanmanModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            DatePicker(
                state = state,
                showModeToggle = false,
                colors = DatePickerDefaults.colors()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Button(
                    onClick = {
                        val selected = state.selectedDateMillis ?: return@Button
                        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                            timeInMillis = selected
                        }
                        onDateSelected(
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH) + 1,
                            calendar.get(Calendar.DAY_OF_MONTH)
                        )
                        onDismiss()
                    },
                    enabled = state.selectedDateMillis != null
                ) { Text("确定") }
            }
        }
    }
}
