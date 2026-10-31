package com.huaying.xstz.core.network.time

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * SNTP（RFC 2030 简化版）探针，走 UDP 123 端口。
 *
 * 只取服务器在响应里填的 **Transmit Timestamp（T3，偏移 40 字节）**，
 * 不实现完整的 NTP 滤波——移动端对"几十毫秒精度"的需求，
 * 远低于"能拿到一个不被系统时间篡改影响的时间"的需求。
 *
 * NTP 纪元比 Unix 早 2208988800 秒，这是 RFC 规定的固定偏移。
 */
class SntpProbe(
    private val host: String,
    private val port: Int = 123,
    private val timeoutMs: Int = 4000,
) : TimeProbe {

    override suspend fun probe(): Long? = withContext(Dispatchers.IO) {
        runCatching {
            val socket = DatagramSocket()
            socket.soTimeout = timeoutMs
            try {
                val request = ByteArray(48)
                // LI=0, VN=3, Mode=3(client) → 0x1B
                request[0] = 0x1B.toByte()
                val address = InetAddress.getByName(host)
                socket.send(DatagramPacket(request, request.size, address, port))

                val response = ByteArray(48)
                socket.receive(DatagramPacket(response, response.size))

                val seconds = readUint32(response, 40)
                val fraction = readUint32(response, 44)
                val ntpMillis = seconds * 1_000L + (fraction * 1_000L shr 32)
                ntpMillis - NTP_EPOCH_OFFSET_MS
            } finally {
                socket.close()
            }
        }.getOrNull()
    }

    private fun readUint32(buf: ByteArray, offset: Int): Long {
        var v = 0L
        for (i in 0..3) {
            v = (v shl 8) or (buf[offset + i].toLong() and 0xFF)
        }
        return v
    }

    companion object {
        /** NTP 纪元（1900-01-01）到 Unix 纪元（1970-01-01）的秒数 × 1000 */
        private const val NTP_EPOCH_OFFSET_MS = 2_208_988_800_000L
    }
}
