package com.moneyhistory.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.with
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.moneyhistory.app.MoneyUtils
import com.moneyhistory.app.R
import com.moneyhistory.app.RecurringExpense
import com.moneyhistory.app.YearMonth
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** 月份切换条（‹ 2026年8月 ›），月份文字带滑动动画。 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MonthSelector(
    month: YearMonth,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.month_prev)
            )
        }
        AnimatedContent(
            targetState = month,
            transitionSpec = {
                val forward =
                    targetState.year * 12 + targetState.month >
                        initialState.year * 12 + initialState.month
                if (forward) {
                    slideInHorizontally { it } + fadeIn() with
                        slideOutHorizontally { -it } + fadeOut()
                } else {
                    slideInHorizontally { -it } + fadeIn() with
                        slideOutHorizontally { it } + fadeOut()
                }
            },
            label = "month"
        ) { m ->
            Text(
                text = stringResource(R.string.month_format, m.year, m.month),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        IconButton(onClick = onNext) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.month_next)
            )
        }
    }
}

/** 日期选择按钮：点击弹出 M3 DatePicker，选择后保留原时分。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerButton(
    label: String,
    millis: Long,
    onDateSelected: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var show by remember { mutableStateOf(false) }
    val datePattern = stringResource(R.string.date_pattern)

    OutlinedButton(onClick = { show = true }, modifier = modifier) {
        Icon(Icons.Filled.DateRange, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("$label：${formatSheetDate(millis, datePattern)}")
    }

    if (show) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = utcMidnightOf(millis)
        )
        DatePickerDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { sel ->
                        onDateSelected(applyUtcDate(sel, millis))
                    }
                    show = false
                }) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { show = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/** 月度预算输入对话框；[onSave] 传入 0 表示清除预算。 */
@Composable
fun BudgetDialog(
    currentCents: Long,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit
) {
    var text by remember {
        mutableStateOf(
            if (currentCents > 0) MoneyUtils.formatCentsPlain(currentCents) else ""
        )
    }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.budget_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    error = false
                },
                label = { Text(stringResource(R.string.budget_amount_hint)) },
                isError = error,
                supportingText = {
                    if (error) Text(stringResource(R.string.sheet_amount_error))
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val cents = MoneyUtils.parseToCents(text)
                if (cents == null) {
                    error = true
                } else {
                    onSave(cents)
                    onDismiss()
                }
            }) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            Row {
                if (currentCents > 0) {
                    TextButton(onClick = {
                        onSave(0)
                        onDismiss()
                    }) {
                        Text(stringResource(R.string.budget_clear))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        }
    )
}

/** 周期文案（每周/每月/每年）。 */
@Composable
fun cycleLabel(cycle: RecurringExpense.Cycle): String = stringResource(
    when (cycle) {
        RecurringExpense.Cycle.WEEKLY -> R.string.cycle_weekly
        RecurringExpense.Cycle.MONTHLY -> R.string.cycle_monthly
        RecurringExpense.Cycle.YEARLY -> R.string.cycle_yearly
    }
)

internal fun formatSheetDate(millis: Long, pattern: String): String =
    SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))

/** 本地日期对应的 UTC 零点毫秒（DatePicker 的语义）。 */
internal fun utcMidnightOf(millis: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = millis }
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    utc.clear()
    utc.set(
        local.get(Calendar.YEAR),
        local.get(Calendar.MONTH),
        local.get(Calendar.DAY_OF_MONTH)
    )
    return utc.timeInMillis
}

/** 把 DatePicker 返回的 UTC 零点换算回本地年月日，保留 [baseMillis] 的时分。 */
internal fun applyUtcDate(utcMillis: Long, baseMillis: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        .apply { timeInMillis = utcMillis }
    val cal = Calendar.getInstance().apply { timeInMillis = baseMillis }
    cal.set(Calendar.YEAR, utc.get(Calendar.YEAR))
    cal.set(Calendar.MONTH, utc.get(Calendar.MONTH))
    cal.set(Calendar.DAY_OF_MONTH, utc.get(Calendar.DAY_OF_MONTH))
    return cal.timeInMillis
}
