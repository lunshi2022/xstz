package com.huaying.xstz.core.database

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 数据库口令封包的字节层测试。
 *
 * ══════════════════════════════════════════════════════════════════
 * 为什么"加密是否生效"不在这里测
 *
 * SQLCipher 的核心是 native 库 `libsqlcipher.so`。Robolectric 只在 JVM 上
 * 模拟 Android framework，**无法加载 ARM/x86 的 .so**，一旦触达 native
 * 就抛 `UnsatisfiedLinkError: nativeOpen`（已实测确认）。
 *
 * 所以测试分两层：
 *   * 本文件（JVM）：封包格式、边界校验、往返一致性——不需要 native 与 Keystore
 *   * `androidTest/.../SqlCipherAggregationTest`：真实加密落盘 + SUM() 聚合
 *
 * [DatabaseKeyProvider.wrap] / [DatabaseKeyProvider.unwrap] 接受注入的密钥，
 * 正是为了能用软件生成的 AES 密钥在 JVM 上跑通往返——否则这两个方法
 * 会因为绑死 AndroidKeyStore 而完全无法在 JVM 测试。
 * ══════════════════════════════════════════════════════════════════
 */
class KeyEnvelopeTest {

    @Test
    fun `打包与拆包往返一致`() {
        val iv = ByteArray(KeyEnvelope.IV_LEN) { it.toByte() }
        val sealed = "ciphertext-and-tag".toByteArray()

        val packed = KeyEnvelope.pack(iv, sealed)
        val (gotIv, gotBody) = KeyEnvelope.unpack(packed)

        assertArrayEquals(iv, gotIv)
        assertArrayEquals(sealed, gotBody)
    }

    /**
     * 长度不足必须在版本检查之前就被拦下。
     *
     * 若顺序反了，读 `payload[0]` 之前没做越界保护，短文件会抛
     * `ArrayIndexOutOfBoundsException`，排查时会误以为是代码 bug
     * 而不是"密钥文件损坏"。
     */
    @Test
    fun `长度不足时抛异常且不越界`() {
        val tooShort = ByteArray(KeyEnvelope.MIN_LEN - 1) { 1 }
        val e = assertThrows(KeyEnvelope.MalformedException::class.java) {
            KeyEnvelope.unpack(tooShort)
        }
        assertTrue("错误信息应指出长度问题", e.message!!.contains("长度异常"))
    }

    @Test
    fun `空数组不会崩溃`() {
        assertThrows(KeyEnvelope.MalformedException::class.java) {
            KeyEnvelope.unpack(ByteArray(0))
        }
    }

    /**
     * 版本号不匹配必须拒绝，不能"尽力解析"。
     *
     * 未来若把包裹算法从 AES-GCM 换成别的，旧文件仍要能读——
     * 那是通过新增版本分支实现，而不是忽略版本号硬解。
     * 忽略版本会导致"用错算法解出的垃圾字节被当成口令"，
     * 表现为库能打开但全是乱码，比直接报错难排查得多。
     */
    @Test
    fun `版本号不匹配时拒绝`() {
        val packed = KeyEnvelope.pack(ByteArray(KeyEnvelope.IV_LEN), ByteArray(16))
        packed[0] = 99
        val e = assertThrows(KeyEnvelope.MalformedException::class.java) {
            KeyEnvelope.unpack(packed)
        }
        assertTrue("错误信息应包含版本号", e.message!!.contains("99"))
    }

    /**
     * 用软件 AES 密钥跑通真实的加密-解密往返。
     *
     * 这一段**确实执行了 AES-GCM**，验证的是 wrap/unwrap 的接线正确，
     * 包括 IV 传递、AAD 无关、tag 校验。用错 IV 位置或把 tag 当密文
     * 都会在这里失败。
     */
    @Test
    fun `AES-GCM 往返还原出相同 DEK`() {
        val key = javax.crypto.KeyGenerator
            .getInstance("AES")
            .apply { init(256) }
            .generateKey()

        val dek = DatabaseKeyProvider.newDek()
        val packed = DatabaseKeyProvider.wrap(dek, key)
        val restored = DatabaseKeyProvider.unwrap(packed, key)

        assertArrayEquals("恢复出的 DEK 必须与原始一致", dek, restored)
        assertEquals(32, restored.size)
    }

    /**
     * 换一把密钥必须解不开。
     *
     * 这是"Keystore 被重置后库打不开"的行为预演：包裹密钥变了，
     * GCM tag 校验必然失败。此测试确认我们**没有**吞掉这个失败
     * 并返回垃圾字节。
     */
    @Test
    fun `密钥不匹配时抛异常而非返回垃圾`() {
        val kg = javax.crypto.KeyGenerator.getInstance("AES").apply { init(256) }
        val goodKey = kg.generateKey()
        val badKey = kg.generateKey()

        val packed = DatabaseKeyProvider.wrap(DatabaseKeyProvider.newDek(), goodKey)

        val e = assertThrows(IllegalStateException::class.java) {
            DatabaseKeyProvider.unwrap(packed, badKey)
        }
        assertTrue("应提示从备份恢复", e.message!!.contains("备份"))
    }

    /** 每次生成的 DEK 必须不同，否则"随机 DEK"退化为固定口令。 */
    @Test
    fun `每次生成的 DEK 互不相同`() {
        val a = DatabaseKeyProvider.newDek()
        val b = DatabaseKeyProvider.newDek()
        assertEquals(32, a.size)
        assertTrue("两次生成的 DEK 不应相同", !a.contentEquals(b))
    }
}
