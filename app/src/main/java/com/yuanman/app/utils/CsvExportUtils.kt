package com.yuanman.app.utils

import android.content.Context
import android.content.Intent
import com.yuanman.app.data.local.entity.RecordWithCategory
import com.yuanman.app.data.model.RecordType
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExportUtils {

    /**
     * 将账单列表转换为标准的 CSV 字符串格式
     */
    fun generateCsvString(records: List<RecordWithCategory>): String {
        val sb = StringBuilder()
        // 添加 BOM 头，防止 Excel 打开中文乱码
        sb.append("\uFEFF")
        sb.append("账单ID,记账时间,收支类型,分类名称,金额(元),支付方式,备注说明\n")

        records.forEach { item ->
            val record = item.record
            val isExpense = record.type == RecordType.EXPENSE.name
            val typeStr = if (isExpense) "支出" else "收入"
            val categoryName = item.category?.name ?: "未分类"
            val amountStr = MoneyUtils.centsToYuanString(record.amount, withGrouping = false)
            val timeStr = DateTimeUtils.formatDateTime(record.recordTime)
            val paymentMethod = record.paymentMethod.replace(",", "，")
            val remark = record.remark.replace(",", "，").replace("\n", " ")

            sb.append("${record.id},$timeStr,$typeStr,$categoryName,$amountStr,$paymentMethod,$remark\n")
        }

        return sb.toString()
    }

    /**
     * 调用系统分享面板分享导出的 CSV 内容
     */
    fun shareCsvContent(context: Context, records: List<RecordWithCategory>) {
        val csvContent = generateCsvString(records)
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, csvContent)
            putExtra(Intent.EXTRA_TITLE, "沅满记账_账单明细导出_${DateTimeUtils.formatDate(System.currentTimeMillis())}.csv")
            type = "text/csv"
        }
        val shareIntent = Intent.createChooser(sendIntent, "导出并分享账单数据")
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(shareIntent)
    }
}
