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
    val sizeBytes: Long,
    /** 发布方提供的 SHA-256 旁文件地址（`app-release.apk.sha256`）；缺失时只校验包名与签名。 */
    val sha256Url: String? = null
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

    private val _showUpdatePrompt = MutableStateFlow(false)
    val showUpdatePrompt: StateFlow<Boolean> = _showUpdatePrompt.asStateFlow()

    /** 已通过校验的安装包摘要（按文件路径缓存），供打开安装器前复核使用。 */
    private val verifiedDigests = mutableMapOf<String, String>()

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
                        _hasUnseenUpdate.value = info.versionName != updatePreferences.getString(
                            LAST_SEEN_VERSION,
                            null
                        )
                        // 检查本地是否已经下载过该版本的 APK（命中也要重新校验，避免缓存被替换）
                        val cachedApk = File(context.cacheDir, cacheFileName(info.versionName))
                        val sizeMatches = cachedApk.exists() && cachedApk.length() > 0 &&
                            (info.sizeBytes == 0L || cachedApk.length() == info.sizeBytes)
                        if (sizeMatches && verificationFailure(info, cachedApk) == null) {
                            _updateState.value = UpdateState.ReadyToInstall(info, cachedApk)
                        } else {
                            _updateState.value = UpdateState.Available(info)
                        }

                        // 如果是手动检查，或者未在推迟期内，则弹出升级提示
                        if (isManual || !isUpdatePostponed(info.versionName)) {
                            _showUpdatePrompt.value = true
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

    fun requestUpdatePrompt() {
        if (_updateState.value is UpdateState.Available || _updateState.value is UpdateState.ReadyToInstall) {
            _showUpdatePrompt.value = true
        }
    }

    fun dismissUpdatePrompt(postpone: Boolean = true) {
        _showUpdatePrompt.value = false
        if (postpone) {
            val currentInfo = when (val state = _updateState.value) {
                is UpdateState.Available -> state.info
                is UpdateState.ReadyToInstall -> state.info
                else -> null
            }
            postponeUpdate(currentInfo?.versionName)
        }
    }

    fun postponeUpdate(versionName: String? = null, durationMs: Long = 24 * 60 * 60 * 1000L) {
        updatePreferences.edit()
            .putLong(POSTPONE_UPDATE_UNTIL, System.currentTimeMillis() + durationMs)
            .putString(POSTPONED_VERSION, versionName ?: "")
            .apply()
    }

    fun isUpdatePostponed(newVersionName: String? = null): Boolean {
        val until = updatePreferences.getLong(POSTPONE_UPDATE_UNTIL, 0L)
        val now = System.currentTimeMillis()
        if (now >= until) return false
        // 若发现了比当时取消时更新的版本，则不被旧版本的推迟所限制
        if (newVersionName != null) {
            val postponedVer = updatePreferences.getString(POSTPONED_VERSION, "") ?: ""
            if (postponedVer.isNotBlank() && isNewer(newVersionName, postponedVer)) {
                return false
            }
        }
        return true
    }

    fun markUpdateSeen(versionName: String) {
        updatePreferences.edit()
            .putString(LAST_SEEN_VERSION, versionName)
            .apply()
        _hasUnseenUpdate.value = false
    }

    fun startDownload(info: UpdateInfo) {
        if (_updateState.value is UpdateState.Downloading) return

        _showUpdatePrompt.value = false
        _updateState.value = UpdateState.Downloading(info, 0f, 0L, info.sizeBytes)
        scope.launch(Dispatchers.IO) {
            try {
                val destFile = File(context.cacheDir, cacheFileName(info.versionName))
                val partialFile = File(context.cacheDir, "${cacheFileName(info.versionName)}.part")

                val conn = openConnection(info.apkUrl)

                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    _updateState.value = UpdateState.Error("下载失败 (HTTP ${conn.responseCode})")
                    return@launch
                }

                val totalLength = if (conn.contentLengthLong > 0) conn.contentLengthLong else info.sizeBytes
                var downloaded = 0L

                partialFile.delete()
                conn.inputStream.use { input ->
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
                    val failure = verificationFailure(info, destFile)
                    if (failure == null) {
                        _updateState.value = UpdateState.ReadyToInstall(info, destFile)
                        _showUpdatePrompt.value = true
                    } else {
                        destFile.delete()
                        _updateState.value = UpdateState.Error(failure)
                    }
                } else {
                    _updateState.value = UpdateState.Error("重命名安装包失败")
                }
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error("下载异常: ${e.message}")
            }
        }
    }

    fun installApk(apkFile: File) {
        _showUpdatePrompt.value = false
        try {
            if (!apkFile.exists()) {
                _updateState.value = UpdateState.Error("安装包文件不存在，请重新下载")
                return
            }

            // 打开安装器前复核一次：摘要（若已取得）+ 包名 + 签名
            val failure = when (val result = ApkVerifier.verify(context, apkFile, verifiedDigests[apkFile.absolutePath])) {
                is ApkVerificationResult.Passed -> null
                is ApkVerificationResult.Failed -> result.message
            }
            if (failure != null) {
                _updateState.value = UpdateState.Error(failure)
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

    /**
     * 校验安装包，返回 null 表示通过、否则返回失败原因。
     * 发布方提供 `.sha256` 时必须取到并比对，取不到就阻止安装——宁可让用户手动下载，
     * 也不能在无法校验的情况下把包交给系统安装器。
     */
    private fun verificationFailure(info: UpdateInfo, apkFile: File): String? {
        val expectedDigest = try {
            fetchExpectedDigest(info)
        } catch (e: Exception) {
            return "无法获取官方校验摘要（${e.message ?: "网络异常"}），已阻止安装"
        }
        return when (val result = ApkVerifier.verify(context, apkFile, expectedDigest)) {
            is ApkVerificationResult.Passed -> {
                expectedDigest?.let { verifiedDigests[apkFile.absolutePath] = it }
                null
            }
            is ApkVerificationResult.Failed -> result.message
        }
    }

    /** 拉取发布方提供的 SHA-256 摘要；未提供摘要文件时返回 null。 */
    private fun fetchExpectedDigest(info: UpdateInfo): String? {
        val sha256Url = info.sha256Url ?: return null
        val conn = openConnection(sha256Url)
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("HTTP ${conn.responseCode}")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            return ApkVerifier.parseDigestFile(body)
                ?: throw IllegalStateException("摘要文件格式无法解析")
        } finally {
            conn.disconnect()
        }
    }

    /** 打开连接并跟随 GitHub Release 的 302/307/308 重定向。 */
    private fun openConnection(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("User-Agent", "yuanman-android")
        conn.instanceFollowRedirects = true
        if (conn.responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
            conn.responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
            conn.responseCode == 307 ||
            conn.responseCode == 308
        ) {
            val redirectUrl = conn.getHeaderField("Location")
            if (!redirectUrl.isNullOrBlank()) {
                conn.disconnect()
                val redirected = URL(redirectUrl).openConnection() as HttpURLConnection
                redirected.connectTimeout = 15_000
                redirected.readTimeout = 30_000
                redirected.setRequestProperty("User-Agent", "yuanman-android")
                return redirected
            }
        }
        return conn
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
            var sha256Url: String? = null

            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                val url = asset.optString("browser_download_url")
                if (apkUrl == null && name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = url
                    sizeBytes = asset.optLong("size", 0L)
                } else if (sha256Url == null && name.endsWith(".sha256", ignoreCase = true)) {
                    sha256Url = url
                }
            }

            if (apkUrl.isNullOrBlank()) return null

            UpdateInfo(
                versionName = versionName,
                tagName = tagName,
                releaseTitle = json.optString("name", "v$versionName"),
                releaseNotes = json.optString("body", "").trim(),
                apkUrl = apkUrl,
                sizeBytes = sizeBytes,
                sha256Url = sha256Url
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
        private const val UPDATE_PREFERENCES = "yuanman_update_preferences"
        private const val LAST_SEEN_VERSION = "last_seen_version"
        private const val POSTPONE_UPDATE_UNTIL = "postpone_update_until"
        private const val POSTPONED_VERSION = "postponed_version"
    }
}
