package com.yuanman.app.utils

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

/** 安装包校验结果；失败时 message 直接用于界面提示。 */
sealed class ApkVerificationResult {
    object Passed : ApkVerificationResult()
    data class Failed(val message: String) : ApkVerificationResult()
}

/**
 * 更新包安装前校验：摘要（发布提供 `.sha256` 时）、包名与签名必须与本机安装的沅满一致，
 * 防止被替换或篡改的安装包进入系统安装器。三类校验缺一不可，任一不通过即阻止安装。
 */
object ApkVerifier {

    fun sha256Hex(bytes: ByteArray): String = FileDigest.sha256Hex(bytes)

    fun sha256Hex(file: File): String = FileDigest.sha256Hex(file)

    /** 解析 `.sha256` 旁文件：兼容 `sha256sum` 的 `<摘要>  <文件名>`、纯摘要与 `sha256:` 前缀三种写法。 */
    fun parseDigestFile(text: String): String? =
        Regex("[0-9a-fA-F]{64}").find(text.trim())?.value?.lowercase()

    fun verify(context: Context, apkFile: File, expectedSha256: String?): ApkVerificationResult {
        if (!apkFile.isFile || apkFile.length() == 0L) {
            return ApkVerificationResult.Failed("安装包文件不完整，请重新下载")
        }

        if (!expectedSha256.isNullOrBlank() && !FileDigest.matches(expectedSha256, sha256Hex(apkFile))) {
            return ApkVerificationResult.Failed("安装包摘要校验失败（SHA-256 不匹配），已阻止安装")
        }

        val packageManager = context.packageManager
        val archive = archiveInfo(packageManager, apkFile.absolutePath)
            ?: return ApkVerificationResult.Failed("无法读取安装包信息，已阻止安装")
        if (archive.packageName != context.packageName) {
            return ApkVerificationResult.Failed("安装包包名不匹配（${archive.packageName}），已阻止安装")
        }

        val archiveSigners = signerDigests(archive)
        val installedSigners = installedInfo(packageManager, context.packageName)?.let { signerDigests(it) }.orEmpty()
        if (archiveSigners.isEmpty() || installedSigners.isEmpty()) {
            return ApkVerificationResult.Failed("无法读取签名信息，已阻止安装")
        }
        // 允许本机签名集合包含安装包签名（覆盖密钥轮换后仍持历史签名的包），但不允许出现本机之外的签名
        if (!archiveSigners.all { it in installedSigners }) {
            return ApkVerificationResult.Failed("安装包签名与本机不一致，已阻止安装")
        }

        return ApkVerificationResult.Passed
    }

    private fun archiveInfo(packageManager: PackageManager, path: String): PackageInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageManager.getPackageArchiveInfo(path, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageArchiveInfo(path, PackageManager.GET_SIGNATURES)
        }

    private fun installedInfo(packageManager: PackageManager, packageName: String): PackageInfo? =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            }
        } catch (e: Exception) {
            null
        }

    private fun signerDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        }
        if (signatures.isNullOrEmpty()) return emptySet()
        return signatures.mapTo(mutableSetOf()) { sha256Hex(it.toByteArray()) }
    }
}
