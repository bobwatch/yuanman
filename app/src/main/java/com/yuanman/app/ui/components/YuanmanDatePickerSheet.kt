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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
    onDateSelected: ((year: Int, month: Int, day: Int) -> Unit)? = null,
    onDateTimeSelected: ((Long) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val maxDate = remember(maxDateMillis) { maxDateMillis }
    val initialCalendar = remember(initialDateMillis, maxDate) {
        Calendar.getInstance().apply {
            timeInMillis = initialDateMillis.coerceAtMost(maxDate)
        }
    }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDateMillis.coerceAtMost(maxDate),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= maxDate
        }
    )
    val timeState = rememberTimePickerState(
        initialHour = initialCalendar.get(Calendar.HOUR_OF_DAY),
        initialMinute = initialCalendar.get(Calendar.MINUTE),
        is24Hour = true
    )

    // DatePicker 的日期值按 UTC 返回，这里重新组合为本地时间，确保保存的时间与设备显示一致。
    val selectedDateTimeMillis = state.selectedDateMillis?.let { selected ->
        val selectedDate = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = selected
        }
        Calendar.getInstance().apply {
            clear()
            set(Calendar.YEAR, selectedDate.get(Calendar.YEAR))
            set(Calendar.MONTH, selectedDate.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, selectedDate.get(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, timeState.hour)
            set(Calendar.MINUTE, timeState.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val selectingDateTime = onDateTimeSelected != null
    val isFutureTime = selectingDateTime &&
        selectedDateTimeMillis != null && selectedDateTimeMillis > maxDate

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
            if (selectingDateTime) {
                Text(
                    text = "时间",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                TimeInput(
                    state = timeState,
                    modifier = Modifier.fillMaxWidth(),
                    colors = TimePickerDefaults.colors()
                )
                if (isFutureTime) {
                    Text(
                        text = "不能选择未来时间",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
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
                        if (onDateTimeSelected != null) {
                            selectedDateTimeMillis?.let(onDateTimeSelected)
                        } else {
                            onDateSelected?.invoke(
                                calendar.get(Calendar.YEAR),
                                calendar.get(Calendar.MONTH) + 1,
                                calendar.get(Calendar.DAY_OF_MONTH)
                            )
                        }
                        onDismiss()
                    },
                    enabled = state.selectedDateMillis != null && !isFutureTime
                ) { Text("确定") }
            }
        }
    }
}
