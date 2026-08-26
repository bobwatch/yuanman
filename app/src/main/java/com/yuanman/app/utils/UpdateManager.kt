package com.yuanman.app.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.yuanman.app.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class UpdateInfo(
    val versionName: String,
    val tagName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val apkUrl: String,
    val sizeBytes: Long
)

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class Available(val info: UpdateInfo) : UpdateState()
    data class Downloading(val info: UpdateInfo, val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : UpdateState()
    data class ReadyToInstall(val info: UpdateInfo, val apkFile: File) : UpdateState()
    object UpToDate : UpdateState()
    data class Error(val message: String) : UpdateState()
}

class UpdateManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    val currentVersionName: String
        get() = try {
            BuildConfig.VERSION_NAME
        } catch (e: Exception) {
            "0.0.1"
        }

    fun checkForUpdates(isManual: Boolean = true) {
        if (_updateState.value is UpdateState.Checking || _updateState.value is UpdateState.Downloading) {
            return
        }

        _updateState.value = UpdateState.Checking
        scope.launch(Dispatchers.IO) {
            try {
                val url = URL(LATEST_RELEASE_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("User-Agent", "yuanman-android")

                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val info = parseRelease(response)
                    if (info != null && isNewer(info.versionName, currentVersionName)) {
                        // 检查本地是否已经下载过该版本的 APK
                        val cachedApk = File(context.cacheDir, cacheFileName(info.versionName))
                        if (cachedApk.exists() && cachedApk.length() > 0 && (info.sizeBytes == 0L || cachedApk.length() == info.sizeBytes)) {
                            _updateState.value = UpdateState.ReadyToInstall(info, cachedApk)
                        } else {
                            _updateState.value = UpdateState.Available(info)
                        }
                    } else {
                        _updateState.value = UpdateState.UpToDate
                    }
                } else if (conn.responseCode == 404) {
                    _updateState.value = UpdateState.UpToDate
                } else {
                    _updateState.value = UpdateState.Error("检查失败 (HTTP ${conn.responseCode})")
                }
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error(e.message ?: "网络连接异常")
            }
        }
    }

    fun startDownload(info: UpdateInfo) {
        if (_updateState.value is UpdateState.Downloading) return

        _updateState.value = UpdateState.Downloading(info, 0f, 0L, info.sizeBytes)
        scope.launch(Dispatchers.IO) {
            try {
                val destFile = File(context.cacheDir, cacheFileName(info.versionName))
                val partialFile = File(context.cacheDir, "${cacheFileName(info.versionName)}.part")

                val conn = URL(info.apkUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.setRequestProperty("User-Agent", "yuanman-android")

                // 支持 GitHub Release 302 重定向
                conn.instanceFollowRedirects = true
                var realConn = conn
                if (conn.responseCode == HttpURLConnection.HTTP_MOVED_TEMP || conn.responseCode == HttpURLConnection.HTTP_MOVED_PERM || conn.responseCode == 307 || conn.responseCode == 308) {
                    val redirectUrl = conn.getHeaderField("Location")
                    if (!redirectUrl.isNullOrBlank()) {
                        realConn = URL(redirectUrl).openConnection() as HttpURLConnection
                        realConn.connectTimeout = 15_000
                        realConn.readTimeout = 30_000
                        realConn.setRequestProperty("User-Agent", "yuanman-android")
                    }
                }

                if (realConn.responseCode != HttpURLConnection.HTTP_OK) {
                    _updateState.value = UpdateState.Error("下载失败 (HTTP ${realConn.responseCode})")
                    return@launch
                }

                val totalLength = if (realConn.contentLengthLong > 0) realConn.contentLengthLong else info.sizeBytes
                var downloaded = 0L

                partialFile.delete()
                realConn.inputStream.use { input ->
                    partialFile.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            val progress = if (totalLength > 0) (downloaded.toFloat() / totalLength.toFloat()).coerceIn(0f, 1f) else 0f
                            _updateState.value = UpdateState.Downloading(info, progress, downloaded, totalLength)
                        }
                    }
                }

                destFile.delete()
                if (partialFile.renameTo(destFile)) {
                    _updateState.value = UpdateState.ReadyToInstall(info, destFile)
                } else {
                    _updateState.value = UpdateState.Error("重命名安装包失败")
                }
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error("下载异常: ${e.message}")
            }
        }
    }

    fun installApk(apkFile: File) {
        try {
            if (!apkFile.exists()) {
                _updateState.value = UpdateState.Error("安装包文件不存在，请重新下载")
                return
            }

            // 升级安装前执行紧急安全快照备份
            com.yuanman.app.data.local.DatabaseBackupManager.autoBackup(context)

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            _updateState.value = UpdateState.Error("启动安装器失败: ${e.message}")
        }
    }

    private fun parseRelease(jsonString: String): UpdateInfo? {
        return try {
            val json = JSONObject(jsonString)
            val tagName = json.optString("tag_name", "")
            val versionName = tagName.removePrefix("v").trim()
            if (versionName.isBlank()) return null

            val assets = json.optJSONArray("assets") ?: return null
            var apkUrl: String? = null
            var sizeBytes = 0L

            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url")
                    sizeBytes = asset.optLong("size", 0L)
                    break
                }
            }

            if (apkUrl.isNullOrBlank()) return null

            UpdateInfo(
                versionName = versionName,
                tagName = tagName,
                releaseTitle = json.optString("name", "v$versionName"),
                releaseNotes = json.optString("body", "").trim(),
                apkUrl = apkUrl,
                sizeBytes = sizeBytes
            )
        } catch (e: Exception) {
            null
        }
    }

    fun isNewer(latest: String, current: String): Boolean {
        val a = parseVersionParts(latest) ?: return false
        val b = parseVersionParts(current) ?: return false
        val maxLen = maxOf(a.size, b.size)
        for (i in 0 until maxLen) {
            val v1 = a.getOrElse(i) { 0 }
            val v2 = b.getOrElse(i) { 0 }
            if (v1 != v2) return v1 > v2
        }
        return false
    }

    private fun parseVersionParts(version: String): List<Int>? {
        return try {
            val clean = version.trim().removePrefix("v").substringBefore('-').substringBefore('+')
            clean.split('.').map { it.toInt() }
        } catch (e: Exception) {
            null
        }
    }

    private fun cacheFileName(versionName: String): String {
        val safe = versionName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        return "yuanman-update-$safe.apk"
    }

    companion object {
        private const val GITHUB_REPO = "bobwatch/yuanman"
        private const val LATEST_RELEASE_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
    }
}
