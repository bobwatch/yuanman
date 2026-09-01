package com.yuanman.app.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.content.pm.PackageManager
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
import java.util.Locale

data class UpdateInfo(
    val versionName: String,
    val tagName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val apkUrl: String,
    val sizeBytes: Long,
    val checksumUrl: String? = null
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

    private val updatePreferences = context.getSharedPreferences(
        UPDATE_PREFERENCES,
        Context.MODE_PRIVATE
    )
    private val _hasUnseenUpdate = MutableStateFlow(false)
    val hasUnseenUpdate: StateFlow<Boolean> = _hasUnseenUpdate.asStateFlow()

    val currentVersionName: String
        get() = try {
            BuildConfig.VERSION_NAME
        } catch (e: Exception) {
            "0.0.1"
        }

    fun checkForUpdates(isManual: Boolean = true) {
        if (_updateState.value is UpdateState.Checking ||
            _updateState.value is UpdateState.Downloading ||
            (!isManual && _updateState.value is UpdateState.ReadyToInstall)
        ) {
            return
        }

        _updateState.value = UpdateState.Checking
        scope.launch(Dispatchers.IO) {
            try {
                val conn = openTrustedConnection(
                    urlString = LATEST_RELEASE_URL,
                    connectTimeout = 10_000,
                    readTimeout = 15_000,
                    accept = "application/vnd.github+json"
                )

                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val info = parseRelease(response)
                    if (info != null && isNewer(info.versionName, currentVersionName)) {
                        _hasUnseenUpdate.value = info.versionName != updatePreferences.getString(
                            LAST_SEEN_VERSION,
                            null
                        )
                        // 检查本地是否已经下载过该版本的 APK
                        val cachedApk = File(context.cacheDir, cacheFileName(info.versionName))
                        if (cachedApk.exists() && cachedApk.length() > 0 &&
                            (info.sizeBytes == 0L || cachedApk.length() == info.sizeBytes) &&
                            archiveMatchesInstalledSignature(cachedApk)
                        ) {
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

    fun markUpdateSeen(versionName: String) {
        updatePreferences.edit()
            .putString(LAST_SEEN_VERSION, versionName)
            .apply()
        _hasUnseenUpdate.value = false
    }

    fun startDownload(info: UpdateInfo) {
        if (_updateState.value is UpdateState.Downloading) return

        _updateState.value = UpdateState.Downloading(info, 0f, 0L, info.sizeBytes)
        scope.launch(Dispatchers.IO) {
            try {
                val destFile = File(context.cacheDir, cacheFileName(info.versionName))
                val partialFile = File(context.cacheDir, "${cacheFileName(info.versionName)}.part.apk")
                val realConn = openTrustedConnection(info.apkUrl, 15_000, 30_000)

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

                if (totalLength > 0L && downloaded != totalLength) {
                    partialFile.delete()
                    _updateState.value = UpdateState.Error("下载校验失败：文件大小不一致")
                    return@launch
                }
                val expectedChecksum = info.checksumUrl?.let(::downloadChecksum)
                if (expectedChecksum != null && fileSha256(partialFile) != expectedChecksum) {
                    partialFile.delete()
                    _updateState.value = UpdateState.Error("下载校验失败：SHA-256 不一致")
                    return@launch
                }
                if (!archiveMatchesInstalledSignature(partialFile)) {
                    partialFile.delete()
                    _updateState.value = UpdateState.Error("安装包签名与当前应用不一致，已拒绝安装")
                    return@launch
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
            if (!archiveMatchesInstalledSignature(apkFile)) {
                _updateState.value = UpdateState.Error("安装包签名校验失败，请重新下载")
                apkFile.delete()
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
            var apkName: String? = null
            var sizeBytes = 0L

            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                val lowerName = name.lowercase(Locale.ROOT)
                if (lowerName.endsWith(".apk") &&
                    "androidtest" !in lowerName && "android-test" !in lowerName && "debug" !in lowerName
                ) {
                    apkUrl = asset.optString("browser_download_url")
                    apkName = name
                    sizeBytes = asset.optLong("size", 0L)
                    break
                }
            }

            if (apkUrl.isNullOrBlank()) return null

            var checksumUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                if (name.equals("${apkName}.sha256", ignoreCase = true) || name.equals("SHA256SUMS", ignoreCase = true)) {
                    checksumUrl = asset.optString("browser_download_url").ifBlank { null }
                    break
                }
            }

            UpdateInfo(
                versionName = versionName,
                tagName = tagName,
                releaseTitle = json.optString("name", "v$versionName"),
                releaseNotes = json.optString("body", "").trim(),
                apkUrl = apkUrl,
                sizeBytes = sizeBytes,
                checksumUrl = checksumUrl
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

    private fun openTrustedConnection(
        urlString: String,
        connectTimeout: Int,
        readTimeout: Int,
        accept: String? = null
    ): HttpURLConnection {
        var current = URL(urlString)
        repeat(MAX_REDIRECTS + 1) {
            require(current.protocol.equals("https", ignoreCase = true)) { "更新地址必须使用 HTTPS" }
            require(isTrustedUpdateHost(current.host)) { "不受信任的更新域名：${current.host}" }
            val connection = current.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = connectTimeout
            connection.readTimeout = readTimeout
            connection.setRequestProperty("User-Agent", "yuanman-android")
            accept?.let { connection.setRequestProperty("Accept", it) }
            val code = connection.responseCode
            if (code in setOf(301, 302, 303, 307, 308)) {
                val location = connection.getHeaderField("Location") ?: error("更新重定向缺少目标地址")
                current = URL(current, location)
                connection.disconnect()
            } else {
                return connection
            }
        }
        error("更新重定向次数过多")
    }

    private fun isTrustedUpdateHost(host: String): Boolean {
        val normalized = host.lowercase()
        return normalized == "github.com" || normalized == "api.github.com" ||
            normalized.endsWith(".githubusercontent.com")
    }

    private fun downloadChecksum(url: String): String? {
        val conn = openTrustedConnection(url, 10_000, 15_000)
        if (conn.responseCode != HttpURLConnection.HTTP_OK) error("无法下载 SHA-256 校验文件")
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        return Regex("(?i)\\b[0-9a-f]{64}\\b").find(text)?.value?.lowercase()
            ?: error("SHA-256 校验文件格式错误")
    }

    private fun fileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Suppress("DEPRECATION")
    private fun archiveMatchesInstalledSignature(apkFile: File): Boolean {
        val packageManager = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else PackageManager.GET_SIGNATURES
        val archive = packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags) ?: return false
        if (archive.packageName != context.packageName) return false
        val installed = packageManager.getPackageInfo(context.packageName, flags)
        val archiveSignatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archive.signingInfo?.apkContentsSigners.orEmpty()
        } else archive.signatures.orEmpty()
        val installedSignatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            installed.signingInfo?.apkContentsSigners.orEmpty()
        } else installed.signatures.orEmpty()
        return archiveSignatures.isNotEmpty() && archiveSignatures.any { candidate ->
            installedSignatures.any { it.toByteArray().contentEquals(candidate.toByteArray()) }
        }
    }

    companion object {
        private const val GITHUB_REPO = "bobwatch/yuanman"
        private const val LATEST_RELEASE_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
        private const val UPDATE_PREFERENCES = "yuanman_update_preferences"
        private const val LAST_SEEN_VERSION = "last_seen_version"
        private const val MAX_REDIRECTS = 5
    }
}
