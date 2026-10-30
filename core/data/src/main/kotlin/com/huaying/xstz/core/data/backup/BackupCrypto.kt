package com.huaying.xstz.core.data.backup

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 备份文件加密。
 *
 * ══════════════════════════════════════════════════════════════════
 * 这解决了旧版最致命的缺陷：
 *
 * 旧版用 AndroidKeystore 里的不可导出密钥加密导出文件。
 * Keystore 密钥**永远无法离开设备**，所以：
 *   * 换手机 → 备份文件永久解不开
 *   * 重装系统 → 同上
 *   * 卸载重装 → 同上
 * 换句话说，那个"备份"功能在跨设备场景下 100% 失效，
 * 而跨设备恢复恰恰是备份存在的唯一理由。
 *
 * 新版改为**口令派生密钥**：用户设一个备份口令，用 Argon2id 从口令派生 AES 密钥。
 * 密钥可以从口令随时重建，因此备份在任何设备、任何时间都能恢复。
 * ══════════════════════════════════════════════════════════════════
 *
 * 文件格式（版本化，向后兼容的基石）：
 *   offset  size  field
 *   0       4     magic "XSTB"
 *   4       1     version = 1
 *   5       1     kdfId   1=Argon2id  2=PBKDF2-SHA256(降级)
 *   6       16    salt
 *   22      12    nonce (GCM IV)
 *   34      ...   ciphertext + GCM tag
 *
 * 设计要点：
 *   * 每个文件独立随机 salt + nonce，杜绝重用到同一密钥流
 *   * 头部作为 AAD 参与认证，攻击者改 kdfId 或 version 会导致解密失败而非降级
 *   * GCM 自带完整性校验，密文被篡改必然抛 AEADBadTagException
 */
object BackupCrypto {

    private const val MAGIC = "XSTB"
    private const val VERSION: Byte = 1
    private const val KDF_ARGON2ID: Byte = 1
    private const val KDF_PBKDF2: Byte = 2

    private const val SALT_LEN = 16
    private const val NONCE_LEN = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256

    /** Argon2id 参数：内存 64MB、3 轮、单线程。手机端约 0.3~0.8 秒，可接受 */
    private const val ARGON2_MEMORY_KB = 64 * 1024
    private const val ARGON2_ITERATIONS = 3
    private const val ARGON2_PARALLELISM = 1

    /** PBKDF2 降级参数：OWASP 2023 建议的 SHA-256 迭代下限 */
    private const val PBKDF2_ITERATIONS = 600_000

    private val random = SecureRandom()

    class DecryptionException(message: String, cause: Throwable? = null) : Exception(message, cause)

    /**
     * 加密。
     *
     * @param plaintext 明文（通常是 JSON 序列化后的备份内容）
     * @param passphrase 用户口令
     * @return 完整文件字节（含头部）
     */
    fun encrypt(plaintext: ByteArray, passphrase: CharArray): ByteArray {
        val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
        val nonce = ByteArray(NONCE_LEN).also { random.nextBytes(it) }

        val (kdfId, key) = deriveKey(passphrase, salt)

        val header = ByteBuffer.allocate(6 + SALT_LEN + NONCE_LEN)
            .put(MAGIC.toByteArray(Charsets.US_ASCII))
            .put(VERSION)
            .put(kdfId)
            .put(salt)
            .put(nonce)
            .array()

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_BITS, nonce),
        )
        // 头部参与认证：任何对 version/kdfId 的篡改都会让解密失败
        cipher.updateAAD(header)

        val ciphertext = cipher.doFinal(plaintext)
        return header + ciphertext
    }

    /**
     * 解密。口令错误或文件被篡改统一抛 [DecryptionException]，
     * 不向调用方暴露"是口令错还是文件坏"——避免成为口令爆破的 oracle。
     */
    fun decrypt(file: ByteArray, passphrase: CharArray): ByteArray {
        if (file.size < 6 + SALT_LEN + NONCE_LEN + 16) {
            throw DecryptionException("文件长度不足，可能已损坏")
        }
        val buf = ByteBuffer.wrap(file)
        val magic = ByteArray(4).also { buf.get(it) }
        if (String(magic, Charsets.US_ASCII) != MAGIC) {
            throw DecryptionException("不是本应用的备份文件")
        }
        val version = buf.get()
        if (version.toInt() != VERSION.toInt()) {
            throw DecryptionException("备份文件版本 $version 不受支持，请升级应用")
        }
        val kdfId = buf.get()
        val salt = ByteArray(SALT_LEN).also { buf.get(it) }
        val nonce = ByteArray(NONCE_LEN).also { buf.get(it) }
        val headerLen = 6 + SALT_LEN + NONCE_LEN

        val key = when (kdfId) {
            KDF_ARGON2ID -> argon2(passphrase, salt) ?: pbkdf2(passphrase, salt)
            KDF_PBKDF2 -> pbkdf2(passphrase, salt)
            else -> throw DecryptionException("未知的密钥派生方式 $kdfId")
        }

        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(TAG_BITS, nonce),
            )
            cipher.updateAAD(file.copyOfRange(0, headerLen))
            cipher.doFinal(file, headerLen, file.size - headerLen)
        } catch (t: Throwable) {
            throw DecryptionException("解密失败：口令不正确或文件已损坏", t)
        }
    }

    /**
     * 派生密钥。
     * Argon2id 不可用时自动降级到 PBKDF2，并把实际用的算法写进头部，
     * 保证同一台设备上加密/解密始终用同一条路径。
     */
    private fun deriveKey(passphrase: CharArray, salt: ByteArray): Pair<Byte, ByteArray> {
        argon2(passphrase, salt)?.let { return KDF_ARGON2ID to it }
        return KDF_PBKDF2 to pbkdf2(passphrase, salt)
    }

    /**
     * Argon2id。用 password4j 的纯 Java 实现：
     *   * 不含 native 库 → 不存在 16KB 页对齐问题（Play 上架硬要求）
     *   * 不依赖 Android 平台 API → JVM 单测可覆盖，行为可复现
     * 若运行环境缺少该类（极少见），返回 null 交由 PBKDF2 兜底。
     *
     * password4j 1.8 的入口是 HashBuilder：Password.hash(plain).addSalt(salt).with(function)。
     * 取原始密钥必须用 **getBytes()**：
     *   * getBytes()        → 32 字节原始摘要（要的就是它）
     *   * getResult()       → "$argon2id$v=19$m=...$salt$hash" 的 PHC 字符串（97 字节）
     *   * getResultAsBytes()→ 上面那个字符串的字节，也是 97 字节
     * 早期版本误用了 resultAsBytes 并加了"必须 32 字节"的校验，
     * 结果永远校验失败、静默降级到 PBKDF2——文件头里写着 kdfId=2 却没人发现。
     * 这条注释是防止再次踩坑的唯一防线。
     */
    private fun argon2(passphrase: CharArray, salt: ByteArray): ByteArray? = runCatching {
        val function = com.password4j.Argon2Function.getInstance(
            ARGON2_MEMORY_KB,
            ARGON2_ITERATIONS,
            ARGON2_PARALLELISM,
            KEY_BITS / 8,
            com.password4j.types.Argon2.ID,
        )
        com.password4j.Password
            .hash(passphrase.concatToString())
            .addSalt(salt)
            .with(function)
            .bytes
            .takeIf { it.size == KEY_BITS / 8 }
    }.getOrNull()

    private fun pbkdf2(passphrase: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec)
            .encoded
    }


    /** 口令强度提示：只做长度与字符集建议，不做"必须含特殊字符"这类过时规则 */
    fun passphraseHint(passphrase: String): String? = when {
        passphrase.length < 8 -> "口令至少 8 位"
        passphrase.all { it.isDigit() } -> "纯数字口令太弱，建议加入字母"
        else -> null
    }
}
