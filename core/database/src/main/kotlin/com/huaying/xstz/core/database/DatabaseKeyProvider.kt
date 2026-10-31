package com.huaying.xstz.core.database

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 数据库口令（SQLCipher passphrase）的来源。
 *
 * ══════════════════════════════════════════════════════════════════
 * 方案依据
 *
 * REBUILD_PLAN 第 306 行给了两个可选实现：
 *   「密钥由 **Keystore 包裹的随机 DEK**，或用户口令派生」
 *
 * 这里选前者（随机 DEK + Keystore 包裹），理由：
 *   1. 本地库每次启动都要求用户输入口令，是把备份场景的交互成本错误地
 *      搬到了主流程上——用户只要打开 App 就得输密码，这是产品事故。
 *   2. 随机 256 位 DEK 的熵高于任何用户能记住的口令，不需要 KDF 拉伸。
 *   3. 与 ADR-008 不冲突：ADR-008 说的是**导出文件**必须口令派生
 *      （因为文件跨设备，Keystore 密钥出不了设备）；库文件不跨设备，
 *      两者场景不同，选型不同是正确的而非矛盾的。
 * ══════════════════════════════════════════════════════════════════
 *
 * 为什么不用 `EncryptedSharedPreferences`：
 *   Security-crypto 已进入维护模式（Google 官方声明），且它内部同样是
 *   Keystore + AES-GCM，多一层依赖不换来任何保证。这里直接用 Keystore，
 *   行为完全可见。
 *
 * 存储布局：`files/dbkey.bin` = [1 字节版本][12 字节 IV][密文 + 16 字节 tag]
 * 放在 files/ 而非 databases/：与 .db 分开，删库不连带删密钥。
 */
internal object DatabaseKeyProvider {

    private const val TAG = "DatabaseKeyProvider"

    /** Keystore 别名。改这个字符串等于让所有存量用户的库永久打不开，不要改。 */
    private const val KEYSTORE_ALIAS = "xstz_db_dek_wrapper_v1"

    private const val KEY_FILE = "dbkey.bin"
    private const val TAG_BITS = 128
    private const val DEK_BYTES = 32

    private val random = SecureRandom()

    /**
     * 取得数据库口令。
     *
     * 三种情形：
     *   1. 密钥文件存在且可解 → 返回原 DEK
     *   2. 文件不存在（首次启动 / 用户清了应用数据）→ 生成新 DEK 并落盘
     *   3. 文件存在但解不开（Keystore 被重置、文件损坏）→ **抛异常，不静默重建**
     *
     * 第 3 种情形必须抛异常。库里是用户的持仓与交易记录，静默删库重建
     * 等于无声的数据丢失——这与 XstzDatabase 拒绝
     * fallbackToDestructiveMigration 是同一条原则。
     * 让崩溃暴露问题，由上层提示用户从备份恢复。
     */
    fun passphrase(context: Context): ByteArray {
        val file = File(context.filesDir, KEY_FILE)
        return if (file.exists()) {
            unwrap(file.readBytes())
        } else {
            newDek().also { dek -> file.writeBytes(wrap(dek)) }
        }
    }

    internal fun newDek(): ByteArray = ByteArray(DEK_BYTES).also { random.nextBytes(it) }

    internal fun wrap(dek: ByteArray, key: SecretKey = wrapperKey()): ByteArray {
        val iv = ByteArray(KeyEnvelope.IV_LEN).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return KeyEnvelope.pack(iv, cipher.doFinal(dek))
    }

    internal fun unwrap(payload: ByteArray, key: SecretKey = wrapperKey()): ByteArray {
        val (iv, body) = KeyEnvelope.unpack(payload)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            cipher.doFinal(body)
        } catch (t: Throwable) {
            // 最常见原因：用户在系统设置里清了凭据，或恢复出厂后 Keystore 重建。
            // 此时包裹密钥已变，DEK 再也解不出来：库文件还在但打不开。
            throw IllegalStateException(
                "数据库密钥无法解开（Keystore 可能已被重置）。" +
                    "请从备份恢复数据，不要卸载重装——卸载会一并删除库文件。",
                t,
            )
        }
    }

    /**
     * 取得（或首次创建）Keystore 里的包裹密钥。
     *
     * 不设 `setUserAuthenticationRequired(true)`：要求每次访问都过生物识别，
     * 会把数据库访问变成需要用户在场的操作，后台刷新与 Widget 全部失效。
     * 这里以设备级加密（开机后 Keystore 才可用）作为保护边界。
     */
    private fun wrapperKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry)
            ?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(false)
                .build()
        )
        Log.i(TAG, "已在 AndroidKeyStore 中创建数据库包裹密钥 $KEYSTORE_ALIAS")
        return generator.generateKey()
    }
}
