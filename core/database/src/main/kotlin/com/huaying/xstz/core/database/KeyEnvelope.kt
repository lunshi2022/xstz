package com.huaying.xstz.core.database

/**
 * 数据库口令封包的**纯字节层**逻辑。
 *
 * 从 [DatabaseKeyProvider] 拆出来只有一个理由：让格式校验可以被 JVM 测试覆盖。
 * Keystore 那部分（`AndroidKeyStore`、`KeyGenParameterSpec`）在 Robolectric 里
 * 无法真实运行，若把校验逻辑和它混在一起，就只能靠设备测试来验证——
 * 而"损坏文件要不要拒绝"这类边界恰恰是最需要快速反复验证的。
 *
 * 格式：
 *   offset  size  field
 *   0       1     version
 *   1       12    iv
 *   13      ...   ciphertext + 16 字节 GCM tag
 */
internal object KeyEnvelope {

    const val VERSION: Byte = 1
    const val IV_LEN = 12
    private const val TAG_LEN = 16

    /** 最小合法长度：版本 + IV + tag（密文体可以为 0 字节，因为 DEK 固定 32 字节，实际不会） */
    const val MIN_LEN = 1 + IV_LEN + TAG_LEN

    class MalformedException(message: String) : Exception(message)

    fun pack(iv: ByteArray, sealed: ByteArray): ByteArray {
        require(iv.size == IV_LEN) { "IV 必须是 $IV_LEN 字节，实际 ${iv.size}" }
        val out = ByteArray(1 + IV_LEN + sealed.size)
        out[0] = VERSION
        iv.copyInto(out, 1)
        sealed.copyInto(out, 1 + IV_LEN)
        return out
    }

    /**
     * 校验并拆出 (iv, 密文)。
     *
     * 校验顺序是刻意的：先长度、再版本，最后才拆字段。
     * 反过来会先 `copyOfRange` 出一个越界的半成品，异常信息指向数组下标
     * 而不是真正的原因（文件损坏），排查时会被误导。
     */
    fun unpack(payload: ByteArray): Pair<ByteArray, ByteArray> {
        if (payload.size < MIN_LEN) {
            throw MalformedException(
                "数据库密钥文件长度异常（${payload.size} < $MIN_LEN 字节），拒绝启动以免误清数据"
            )
        }
        if (payload[0] != VERSION) {
            throw MalformedException(
                "数据库密钥文件版本 ${payload[0]} 不受支持（当前支持 $VERSION）"
            )
        }
        val iv = payload.copyOfRange(1, 1 + IV_LEN)
        val body = payload.copyOfRange(1 + IV_LEN, payload.size)
        return iv to body
    }
}
