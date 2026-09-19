package com.yuanman.app.utils

import java.io.File
import java.security.MessageDigest

/** SHA-256 摘要计算：更新包校验与备份完整性清单共用。 */
object FileDigest {

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun sha256Hex(file: File): String {
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

    /** 常数时间比较，避免摘要比对本身泄露信息。 */
    fun matches(expectedHex: String, actualHex: String): Boolean =
        MessageDigest.isEqual(expectedHex.lowercase().toByteArray(), actualHex.lowercase().toByteArray())
}
