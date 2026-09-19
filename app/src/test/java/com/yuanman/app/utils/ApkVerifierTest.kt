package com.yuanman.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * 更新包校验的纯逻辑部分：摘要解析错一位就会把正常安装包判为非法（或反过来放行），
 * 因此用公开测试向量锁定行为。
 */
class ApkVerifierTest {

    // NIST 标准测试向量
    private val sha256OfAbc = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

    @Test
    fun `字节摘要使用公开测试向量`() {
        assertEquals(sha256OfAbc, ApkVerifier.sha256Hex("abc".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `文件摘要与字节摘要一致`() {
        val file = File.createTempFile("yuanman-verify", ".tmp")
        try {
            file.writeBytes("abc".toByteArray(Charsets.UTF_8))
            assertEquals(sha256OfAbc, ApkVerifier.sha256Hex(file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `解析 sha256sum 风格旁文件`() {
        assertEquals(sha256OfAbc, ApkVerifier.parseDigestFile("$sha256OfAbc  app-release.apk\n"))
    }

    @Test
    fun `解析纯摘要与带前缀摘要并统一小写`() {
        assertEquals(sha256OfAbc, ApkVerifier.parseDigestFile(sha256OfAbc))
        assertEquals(sha256OfAbc, ApkVerifier.parseDigestFile("SHA256 (app-release.apk) = ${sha256OfAbc.uppercase()}"))
    }

    @Test
    fun `无法解析的内容返回空`() {
        assertNull(ApkVerifier.parseDigestFile(""))
        assertNull(ApkVerifier.parseDigestFile("checksum unavailable"))
        assertNull(ApkVerifier.parseDigestFile("abc123"))
    }
}
