package com.yuanman.app.utils

import android.content.Context
import android.net.Uri
import com.yuanman.app.data.local.entity.CategoryEntity
import com.yuanman.app.data.local.entity.RecordEntity
import com.yuanman.app.data.model.RecordType
import com.yuanman.app.data.repository.CategoryRepository
import com.yuanman.app.data.repository.RecordRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.math.BigDecimal
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Locale

data class ImportResult(
    val successCount: Int,
    val skipCount: Int,
    val totalCount: Int,
    val message: String
)

object CsvImportUtils {

    private val DATE_FORMATS = listOf(
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA),
        SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.CHINA),
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA),
        SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.CHINA),
        SimpleDateFormat("yyyy-MM-dd", Locale.CHINA),
        SimpleDateFormat("yyyy/MM/dd", Locale.CHINA),
        SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.CHINA),
        SimpleDateFormat("yyyy.MM.dd", Locale.CHINA)
    )

    /**
     * 从 Uri 中解析 CSV 文件并保存到数据库
     */
    suspend fun importFromCsvUri(
        context: Context,
        uri: Uri,
        categoryRepository: CategoryRepository,
        recordRepository: RecordRepository
    ): ImportResult = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@withContext ImportResult(0, 0, 0, "无法读取所选文件")

        // 探测字符集编码 (GBK vs UTF-8)
        val text = detectAndDecode(bytes)
        if (text.isBlank()) {
            return@withContext ImportResult(0, 0, 0, "文件内容为空")
        }

        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) {
            return@withContext ImportResult(0, 0, 0, "文件没有有效数据行")
        }

        // 寻找表头行
        var headerIndex = -1
        var colMap = ColumnMap()

        for (i in 0 until minOf(lines.size, 20)) {
            val cols = parseCsvLine(lines[i])
            val map = matchHeaders(cols)
            if (map.isValid()) {
                headerIndex = i
                colMap = map
                break
            }
        }

        if (headerIndex == -1 || !colMap.isValid()) {
            return@withContext ImportResult(0, 0, 0, "未能识别表格表头，请确保包含“时间”、“金额”、“分类”等列")
        }

        val currentCategories = categoryRepository.getAllCategoriesDirect().toMutableList()
        val defaultExpenseCat = currentCategories.firstOrNull { it.type == RecordType.EXPENSE.name }
        val defaultIncomeCat = currentCategories.firstOrNull { it.type == RecordType.INCOME.name }

        val newRecords = mutableListOf<RecordEntity>()
        var successCount = 0
        var skipCount = 0

        for (i in (headerIndex + 1) until lines.size) {
            val line = lines[i]
            if (line.isBlank() || line.startsWith("#") || line.startsWith("----")) continue

            val cols = parseCsvLine(line)
            if (cols.size <= maxOf(colMap.amountCol, colMap.timeCol)) {
                skipCount++
                continue
            }

            try {
                // 1. 金额解析
                val rawAmountStr = cols.getOrNull(colMap.amountCol).orEmpty()
                val amountCents = parseAmountToCents(rawAmountStr)
                if (amountCents <= 0L) {
                    skipCount++
                    continue
                }

                // 2. 类型解析
                val rawTypeStr = if (colMap.typeCol >= 0) cols.getOrNull(colMap.typeCol).orEmpty() else ""
                val isIncome = rawTypeStr.contains("收入") || rawTypeStr.contains("INCOME") || rawTypeStr.contains("入账")
                val recordType = if (isIncome) RecordType.INCOME.name else RecordType.EXPENSE.name

                // 3. 分类解析与匹配
                val rawCategoryName = if (colMap.categoryCol >= 0) cols.getOrNull(colMap.categoryCol).orEmpty().trim() else ""
                var matchedCategory = currentCategories.firstOrNull {
                    it.type == recordType && (it.name.equals(rawCategoryName, ignoreCase = true) || it.name.contains(rawCategoryName) || rawCategoryName.contains(it.name))
                }

                if (matchedCategory == null && rawCategoryName.isNotBlank()) {
                    // 自动新建分类
                    val newCat = CategoryEntity(
                        name = rawCategoryName.take(8),
                        type = recordType,
                        iconName = if (isIncome) "savings" else "category",
                        colorHex = if (isIncome) 0xFF4CAF50 else 0xFFFF7043,
                        sortOrder = currentCategories.size + 1
                    )
                    val newId = categoryRepository.insertCategory(newCat)
                    val insertedCat = newCat.copy(id = newId)
                    currentCategories.add(insertedCat)
                    matchedCategory = insertedCat
                }

                val finalCategory = matchedCategory
                    ?: (if (isIncome) defaultIncomeCat else defaultExpenseCat)
                    ?: currentCategories.firstOrNull()

                if (finalCategory == null) {
                    skipCount++
                    continue
                }

                // 4. 时间解析
                val rawTimeStr = cols.getOrNull(colMap.timeCol).orEmpty().trim()
                val recordTime = parseTime(rawTimeStr)

                // 5. 支付方式与备注
                val paymentMethod = if (colMap.paymentMethodCol >= 0) cols.getOrNull(colMap.paymentMethodCol).orEmpty().trim() else "其他"
                val remark = if (colMap.remarkCol >= 0) cols.getOrNull(colMap.remarkCol).orEmpty().trim() else ""

                newRecords.add(
                    RecordEntity(
                        type = recordType,
                        amount = amountCents,
                        categoryId = finalCategory.id,
                        recordTime = recordTime,
                        remark = remark,
                        paymentMethod = paymentMethod.ifEmpty { "其他" }
                    )
                )
                successCount++
            } catch (e: Exception) {
                skipCount++
            }
        }

        if (newRecords.isNotEmpty()) {
            recordRepository.insertRecords(newRecords)
        }

        return@withContext ImportResult(
            successCount = successCount,
            skipCount = skipCount,
            totalCount = successCount + skipCount,
            message = "成功导入 $successCount 笔账单" + (if (skipCount > 0) "，跳过 $skipCount 笔无效数据" else "")
        )
    }

    private fun detectAndDecode(bytes: ByteArray): String {
        // 检查 UTF-8 BOM
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }

        // 尝试以 UTF-8 解码
        try {
            val utf8Decoder = Charsets.UTF_8.newDecoder()
            val charBuffer = utf8Decoder.decode(java.nio.ByteBuffer.wrap(bytes))
            return charBuffer.toString()
        } catch (_: Exception) {
        }

        // 尝试以 GBK 解码（支付宝/部分第三方导出常用）
        try {
            val gbk = Charset.forName("GBK")
            return String(bytes, gbk)
        } catch (_: Exception) {
        }

        return String(bytes, Charset.defaultCharset())
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = java.lang.StringBuilder()
        var inQuotes = false

        for (ch in line) {
            when (ch) {
                '"' -> inQuotes = !inQuotes
                ',' -> {
                    if (inQuotes) {
                        sb.append(ch)
                    } else {
                        result.add(sb.toString().trim())
                        sb.setLength(0)
                    }
                }
                else -> sb.append(ch)
            }
        }
        result.add(sb.toString().trim())
        return result
    }

    private data class ColumnMap(
        var timeCol: Int = -1,
        var typeCol: Int = -1,
        var categoryCol: Int = -1,
        var amountCol: Int = -1,
        var paymentMethodCol: Int = -1,
        var remarkCol: Int = -1
    ) {
        fun isValid() = timeCol >= 0 && amountCol >= 0
    }

    private fun matchHeaders(headers: List<String>): ColumnMap {
        val map = ColumnMap()
        headers.forEachIndexed { index, header ->
            val h = header.trim().replace("\uFEFF", "").lowercase()
            when {
                h.contains("时间") || h.contains("日期") || h == "date" || h == "time" -> {
                    if (map.timeCol == -1) map.timeCol = index
                }
                h.contains("类型") || h.contains("收/支") || h == "type" -> {
                    if (map.typeCol == -1) map.typeCol = index
                }
                h.contains("分类") || h == "category" -> {
                    if (map.categoryCol == -1) map.categoryCol = index
                }
                h.contains("金额") || h.contains("amount") || h.contains("money") -> {
                    if (map.amountCol == -1) map.amountCol = index
                }
                h.contains("支付方式") || h.contains("收/付款方式") || h.contains("方式") || h.contains("账户") || h == "method" -> {
                    if (map.paymentMethodCol == -1) map.paymentMethodCol = index
                }
                h.contains("备注") || h.contains("商品") || h.contains("说明") || h.contains("对方") || h == "remark" || h == "note" -> {
                    if (map.remarkCol == -1) map.remarkCol = index
                }
            }
        }
        return map
    }

    private fun parseAmountToCents(str: String): Long {
        val clean = str.replace("¥", "")
            .replace("￥", "")
            .replace(",", "")
            .replace("元", "")
            .replace("+", "")
            .replace("-", "")
            .trim()
        if (clean.isBlank()) return 0L
        val yuan = BigDecimal(clean)
        return (yuan.multiply(BigDecimal(100))).toLong()
    }

    private fun parseTime(str: String): Long {
        if (str.isBlank()) return System.currentTimeMillis()
        val clean = str.trim()
        for (format in DATE_FORMATS) {
            try {
                val date = format.parse(clean)
                if (date != null) return date.time
            } catch (_: Exception) {
            }
        }
        return System.currentTimeMillis()
    }
}
