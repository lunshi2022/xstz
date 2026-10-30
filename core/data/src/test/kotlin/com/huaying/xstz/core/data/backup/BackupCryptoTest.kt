package com.huaying.xstz.core.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCryptoTest {

    private val sample = """{"version":1,"holdings":[{"code":"110022","shares":12345}]}"""
        .toByteArray(Charsets.UTF_8)

    @Test
    fun `加密后可用同口令解密`() {
        val pass = "correct-horse-battery".toCharArray()
        val file = BackupCrypto.encrypt(sample, pass)
        assertArrayEquals(sample, BackupCrypto.decrypt(file, pass))
    }

    @Test
    fun `密文不含明文片段`() {
        val pass = "correct-horse-battery".toCharArray()
        val file = BackupCrypto.encrypt(sample, pass)
        val asText = String(file, Charsets.ISO_8859_1)
        assertFalse(asText.contains("110022"))
        assertFalse(asText.contains("holdings"))
    }

    @Test
    fun `错误口令解密失败`() {
        val file = BackupCrypto.encrypt(sample, "right-pass-123".toCharArray())
        val failed = runCatching { BackupCrypto.decrypt(file, "wrong-pass-123".toCharArray()) }
        assertTrue(failed.isFailure)
        assertTrue(failed.exceptionOrNull() is BackupCrypto.DecryptionException)
    }

    @Test
    fun `同一明文两次加密产生不同密文`() {
        val pass = "same-pass-1234".toCharArray()
        val a = BackupCrypto.encrypt(sample, pass)
        val b = BackupCrypto.encrypt(sample, pass)
        // salt 与 nonce 每次随机，密文必须不同，否则会重用密钥流
        assertNotEquals(a.toList(), b.toList())
        // 但两者都能解开
        assertArrayEquals(sample, BackupCrypto.decrypt(a, pass))
        assertArrayEquals(sample, BackupCrypto.decrypt(b, pass))
    }

    @Test
    fun `篡改密文导致解密失败`() {
        val pass = "tamper-test-123".toCharArray()
        val file = BackupCrypto.encrypt(sample, pass)
        // 翻转最后一个字节（属于 GCM tag 区域）
        file[file.size - 1] = (file[file.size - 1].toInt() xor 0xFF).toByte()
        val failed = runCatching { BackupCrypto.decrypt(file, pass) }
        assertTrue(failed.isFailure)
    }

    @Test
    fun `篡改头部版本号导致解密失败`() {
        val pass = "header-test-123".toCharArray()
        val file = BackupCrypto.encrypt(sample, pass)
        file[4] = 99 // version
        val failed = runCatching { BackupCrypto.decrypt(file, pass) }
        assertTrue(failed.isFailure)
        assertTrue(failed.exceptionOrNull()!!.message!!.contains("版本"))
    }

    @Test
    fun `篡改 kdfId 参与认证并失败`() {
        val pass = "kdf-test-1234".toCharArray()
        val file = BackupCrypto.encrypt(sample, pass)
        file[5] = 2 // 声称是 PBKDF2
        // 头部作为 AAD 参与认证，或密钥本身不同 —— 两条路径都会失败
        val failed = runCatching { BackupCrypto.decrypt(file, pass) }
        assertTrue(failed.isFailure)
    }

    @Test
    fun `非本应用文件被拒绝`() {
        val junk = ByteArray(200) { 0x41 }
        val failed = runCatching { BackupCrypto.decrypt(junk, "any-pass-1234".toCharArray()) }
        assertTrue(failed.isFailure)
        assertTrue(failed.exceptionOrNull()!!.message!!.contains("不是本应用"))
    }

    @Test
    fun `过短文件被拒绝`() {
        val failed = runCatching { BackupCrypto.decrypt(ByteArray(10), "x".toCharArray()) }
        assertTrue(failed.isFailure)
    }

    @Test
    fun `空明文可加解密`() {
        val pass = "empty-test-1234".toCharArray()
        val file = BackupCrypto.encrypt(ByteArray(0), pass)
        assertArrayEquals(ByteArray(0), BackupCrypto.decrypt(file, pass))
    }

    @Test
    fun `中文内容可正确往返`() {
        val text = """{"名称":"示例·沪深300指数","备注":"测试中文"}"""
        val bytes = text.toByteArray(Charsets.UTF_8)
        val pass = "chinese-pass-123".toCharArray()
        assertArrayEquals(bytes, BackupCrypto.decrypt(BackupCrypto.encrypt(bytes, pass), pass))
    }

    @Test
    fun `口令提示只拦过短的`() {
        assertNotNull(BackupCrypto.passphraseHint("abc"))
        assertNotNull(BackupCrypto.passphraseHint("12345678"))
        assertNull(BackupCrypto.passphraseHint("abcd1234"))
    }

    @Test
    fun `文件头部魔数正确`() {
        val file = BackupCrypto.encrypt(sample, "magic-test-1234".toCharArray())
        assertEquals("XSTB", String(file.copyOfRange(0, 4), Charsets.US_ASCII))
        assertEquals(1, file[4].toInt())
    }

    @Test
    fun `默认必须使用 Argon2id 而不是静默降级`() {
        // 回归防护：曾因取错字段（resultAsBytes 是 PHC 字符串而非原始摘要）
        // 导致 Argon2 永远"失败"并静默回退 PBKDF2，文件头写着 kdfId=2 却无人察觉。
        // kdfId: 1=Argon2id 2=PBKDF2。若未来在 Android 运行时上真的不可用，
        // 这条测试会失败——那时应该显式调整文档与 ADR，而不是让它悄悄降级。
        val file = BackupCrypto.encrypt(sample, "argon2-check-1234".toCharArray())
        assertEquals("kdfId 应为 1(Argon2id)，实际为 ${file[5]}", 1, file[5].toInt())
    }
}
