package com.moneyhistory.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** 从 GitHub 最新 Release 解析出的升级信息。 */
data class UpdateInfo(
    val versionName: String,
    val apkUrl: String,
    val notes: String,
    val sizeBytes: Long
)

/** 升级流程状态（驱动 MainActivity 的弹窗）。 */
sealed class UpdateState {
    object Idle : UpdateState()
    data class Available(val info: UpdateInfo) : UpdateState()
    object Downloading : UpdateState()
}

/**
 * 在线升级：从 GitHub Releases 拉取最新 APK 并安装。
 * 纯 HttpURLConnection + org.json，无第三方依赖；不引入 java.time。
 */
object UpdateChecker {

    private const val REPO = "bobwatch/yuanman"
    private const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"
    private const val TIMEOUT_MS = 15_000
    private const val DOWNLOAD_TIMEOUT_MS = 30_000

    /** 检查 GitHub 最新发布；网络/解析失败返回 null。 */
    fun checkLatest(): UpdateInfo? {
        return try {
            val conn = URL(LATEST_URL).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("User-Agent", "yuanman-android")
                if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
                parseRelease(conn.inputStream.bufferedReader().readText())
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseRelease(body: String): UpdateInfo? {
        return try {
            val json = JSONObject(body)
            val tag = json.optString("tag_name").removePrefix("v")
            if (tag.isBlank()) return null
            val assets = json.optJSONArray("assets") ?: return null
            var apkUrl: String? = null
            var sizeBytes = 0L
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name").endsWith(".apk")) {
                    apkUrl = asset.optString("browser_download_url")
                    sizeBytes = asset.optLong("size", 0L)
                    break
                }
            }
            val url = apkUrl ?: return null
            UpdateInfo(
                versionName = tag,
                apkUrl = url,
                notes = json.optString("body", "").trim(),
                sizeBytes = sizeBytes
            )
        } catch (e: Exception) {
            null
        }
    }

    /** 语义化版本比较："0.0.3" > "0.0.2"。 */
    fun isNewer(latest: String, current: String): Boolean {
        val a = latest.split('.').mapNotNull { it.toIntOrNull() }
        val b = current.split('.').mapNotNull { it.toIntOrNull() }
        val size = maxOf(a.size, b.size)
        for (i in 0 until size) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** 下载 APK 到缓存目录；失败返回 null。 */
    fun download(context: Context, url: String, destName: String = "yuanman-update.apk"): File? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = DOWNLOAD_TIMEOUT_MS
                conn.setRequestProperty("User-Agent", "yuanman-android")
                if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
                val file = File(context.cacheDir, destName)
                conn.inputStream.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                file
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }

    /** 打开系统安装器（FileProvider 授权）。 */
    fun install(context: Context, apk: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setData(uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
