package com.yuanman.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 备份清单决定「恢复时是否拒绝写入」：分组识别错会让校验形同虚设，
 * 解析过于宽松则会把损坏清单当成有效清单放过。
 */
class BackupManifestTest {

    private val digestA = "a".repeat(64)
    private val digestB = "b".repeat(64)

    @Test
    fun `编码后可解码出同样的摘要`() {
        val encoded = BackupManifest.encode(
            createdAt = 1_700_000_000_000L,
            digests = mapOf(
                BackupManifest.GROUP_DATABASE to digestA,
                BackupManifest.GROUP_ACCOUNTS to digestB
            )
        )
        val decoded = BackupManifest.decode(encoded)
        assertEquals(1_700_000_000_000L, decoded?.createdAt)
        assertEquals(digestA, decoded?.digests?.get(BackupManifest.GROUP_DATABASE))
        assertEquals(digestB, decoded?.digests?.get(BackupManifest.GROUP_ACCOUNTS))
    }

    @Test
    fun `解码把大写摘要统一为小写`() {
        val encoded = BackupManifest.encode(1L, mapOf(BackupManifest.GROUP_DATABASE to digestA.uppercase()))
        assertEquals(digestA, BackupManifest.decode(encoded)?.digests?.get(BackupManifest.GROUP_DATABASE))
    }

    @Test
    fun `损坏的清单按无清单处理`() {
        assertNull(BackupManifest.decode(""))
        assertNull(BackupManifest.decode("not json"))
        assertNull(BackupManifest.decode("""{"format":99,"files":{"database":"$digestA"}}"""))
        assertNull(BackupManifest.decode("""{"format":1,"files":{"database":"too-short"}}"""))
        assertNull(BackupManifest.decode("""{"format":1}"""))
    }

    @Test
    fun `文件名分组忽略系统追加的后缀`() {
        assertEquals(BackupManifest.GROUP_DATABASE, BackupManifest.groupOf("yuanman_database_backup.db"))
        assertEquals(BackupManifest.GROUP_DATABASE, BackupManifest.groupOf("yuanman_database_backup (1).db"))
        assertEquals(BackupManifest.GROUP_PREFERENCES, BackupManifest.groupOf("yuanman_preferences (1).preferences_pb"))
        assertEquals(BackupManifest.GROUP_ACCOUNTS, BackupManifest.groupOf("yuanman_accounts_data.json"))
        assertNull(BackupManifest.groupOf("yuanman_backup_manifest.json"))
        assertNull(BackupManifest.groupOf("random.txt"))
    }

    @Test
    fun `清单本身不被当成快照分组`() {
        // 清单文件名以 yuanman_backup 开头，不能与数据库快照前缀混淆
        assertTrue(BackupManifest.FILE_NAME.startsWith("yuanman_backup"))
        assertNull(BackupManifest.groupOf(BackupManifest.FILE_NAME))
    }
}
