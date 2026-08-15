package com.moneyhistory.app.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.moneyhistory.app.MainViewModel
import com.moneyhistory.app.MoneyUtils
import com.moneyhistory.app.R
import com.moneyhistory.app.Transaction
import java.io.File

/** 导出：复制 JSON 备份 + 生成 CSV，通过 FileProvider 分享。 */
internal fun exportBackup(
    context: Context,
    viewModel: MainViewModel,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    try {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val jsonFile = File(dir, "moneyhistory_backup.json")
        viewModel.exportTo(jsonFile)

        val csvFile = File(dir, "moneyhistory_backup.csv")
        csvFile.writeText(buildCsv(viewModel.allTransactions()), Charsets.UTF_8)

        val authority = "${context.packageName}.fileprovider"
        val jsonUri = FileProvider.getUriForFile(context, authority, jsonFile)
        val csvUri = FileProvider.getUriForFile(context, authority, csvFile)

        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(
                Intent.EXTRA_STREAM,
                arrayListOf(jsonUri, csvUri)
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(
                intent,
                context.getString(R.string.export_title)
            )
        )
        onSuccess()
    } catch (e: Exception) {
        onError(context.getString(R.string.export_failed, e.message))
    }
}

/** 生成 CSV 文本（带 BOM，方便 Excel 正确识别中文；备注中的换行替换为空格）。 */
private fun buildCsv(list: List<Transaction>): String {
    val sb = StringBuilder("﻿id,type,amount,category,note,timestamp\n")
    list.forEach { t ->
        val note = t.note
            .replace("\r\n", " ")
            .replace("\n", " ")
            .replace("\r", " ")
            .replace("\"", "\"\"")
        sb.append(t.id).append(',')
            .append(t.type.json).append(',')
            .append(MoneyUtils.formatCentsPlain(t.amountCents)).append(',')
            .append(t.category).append(',')
            .append('\"').append(note).append('\"').append(',')
            .append(t.timestamp).append('\n')
    }
    return sb.toString()
}
