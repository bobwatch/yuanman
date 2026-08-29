package com.yuanman.app.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.yuanman.app.data.local.entity.RecordWithCategory
import com.yuanman.app.data.model.RecordType
import java.io.File
import java.io.FileOutputStream

object CsvExportUtils {

    /** 每次导出都覆盖同一个文件，避免缓存目录产生大量重复文件。 */
    private const val EXPORT_FILE_NAME = "yuanman_records.csv"

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
            sb.append(
                listOf(
                    record.id.toString(),
                    timeStr,
                    typeStr,
                    categoryName,
                    amountStr,
                    record.paymentMethod,
                    record.remark
                ).joinToString(",", transform = ::escapeCsvField)
            ).append('\n')
        }

        return sb.toString()
    }

    private fun escapeCsvField(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.any { it == ',' || it == '\n' || it == '\r' || it == '"' }) {
            "\"$escaped\""
        } else {
            escaped
        }
    }

    /**
     * 将 CSV 写入固定文件后调用系统分享面板。
     * 使用 FileProvider 传递 URI，避免部分分享目标无法接收 EXTRA_TEXT 的完整账单数据。
     */
    fun shareCsvContent(context: Context, records: List<RecordWithCategory>) {
        val csvContent = generateCsvString(records)
        val exportDir = File(context.cacheDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()
        val exportFile = File(exportDir, EXPORT_FILE_NAME)
        FileOutputStream(exportFile, false).use { output ->
            output.write(csvContent.toByteArray(Charsets.UTF_8))
        }
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            exportFile
        )
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_TITLE, EXPORT_FILE_NAME)
            putExtra(Intent.EXTRA_SUBJECT, EXPORT_FILE_NAME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            type = "text/csv"
        }
        val shareIntent = Intent.createChooser(sendIntent, "导出并分享账单数据")
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(shareIntent)
    }
}
