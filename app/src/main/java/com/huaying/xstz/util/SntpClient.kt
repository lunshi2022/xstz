package com.huaying.xstz.util

import android.os.SystemClock
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.math.abs

/**
 * Simple SNTP client class for retrieving network time.
 * Based on Android's internal SntpClient.
 */
class SntpClient {
    companion object {
        private const val ORIGINATE_TIME_OFFSET = 24
        private const val RECEIVE_TIME_OFFSET = 32
        private const val TRANSMIT_TIME_OFFSET = 40
        private const val NTP_PACKET_SIZE = 48

        private const val NTP_PORT = 123
        private const val NTP_MODE_CLIENT = 3
        private const val NTP_VERSION = 3

        // Number of seconds between Jan 1, 1900 and Jan 1, 1970
        // 70 years plus 17 leap days
        private const val OFFSET_1900_TO_1970 = ((365L * 70L) + 17L) * 24L * 60L * 60L
    }

    private var _ntpTime: Long = 0
    private var _ntpTimeReference: Long = 0
    private var _roundTripTime: Long = 0

    /**
     * Returns the time computed from the NTP transaction.
     *
     * @return time value computed from NTP server response.
     */
    fun getNtpTime(): Long {
        return _ntpTime
    }

    /**
     * Returns the reference clock value (value of SystemClock.elapsedRealtime())
     * corresponding to the NTP time.
     *
     * @return reference clock corresponding to the NTP time.
     */
    fun getNtpTimeReference(): Long {
        return _ntpTimeReference
    }

    /**
     * Returns the round trip time of the NTP transaction
     *
     * @return round trip time in milliseconds.
     */
    fun getRoundTripTime(): Long {
        return _roundTripTime
    }

    /**
     * Sends an SNTP request to the given host and processes the response.
     *
     * @param host host name of the server.
     * @param timeout network timeout in milliseconds.
     * @return true if the transaction was successful.
     */
    fun requestTime(host: String, timeout: Int): Boolean {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            socket.soTimeout = timeout
            val address = InetAddress.getByName(host)
            val buffer = ByteArray(NTP_PACKET_SIZE)
            val request = DatagramPacket(buffer, buffer.size, address, NTP_PORT)

            // set mode = 3 (client) and version = 3
            // mode is in low 3 bits of first byte
            // version is in bits 3-5 of first byte
            buffer[0] = (NTP_MODE_CLIENT or (NTP_VERSION shl 3)).toByte()

            // get current time and write it to the request packet
            val requestTime = System.currentTimeMillis()
            val requestTicks = SystemClock.elapsedRealtime()
            writeTimeStamp(buffer, TRANSMIT_TIME_OFFSET, requestTime)

            socket.send(request)

            // read the response
            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)
            val responseTicks = SystemClock.elapsedRealtime()
            val responseTime = requestTime + (responseTicks - requestTicks)

            // extract the results
            val originateTime = readTimeStamp(buffer, ORIGINATE_TIME_OFFSET)
            val receiveTime = readTimeStamp(buffer, RECEIVE_TIME_OFFSET)
            val transmitTime = readTimeStamp(buffer, TRANSMIT_TIME_OFFSET)
            
            // Check for invalid zero timestamp in response
            if (originateTime == 0L && receiveTime == 0L && transmitTime == 0L) {
                 return false
            }

            val roundTripTime = responseTicks - requestTicks
            // receiveTime = originateTime + transit + skew
            // responseTime = transmitTime + transit - skew
            // clockOffset = ((receiveTime - originateTime) + (transmitTime - responseTime))/2
            //             = ((receiveTime - originateTime) + (transmitTime - (originateTime + roundTripTime)))/2
            val clockOffset = ((receiveTime - originateTime) + (transmitTime - responseTime)) / 2

            // save our results - use the times on this side of the network latency
            // (response rather than request time)
            _ntpTime = responseTime + clockOffset
            _ntpTimeReference = responseTicks
            _roundTripTime = roundTripTime
        } catch (e: Exception) {
            return false
        } finally {
            socket?.close()
        }

        return true
    }

    /**
     * Reads an unsigned 32 bit big endian number from the given offset in the buffer.
     */
    private fun read32(buffer: ByteArray, offset: Int): Long {
        val b0 = buffer[offset].toInt()
        val b1 = buffer[offset + 1].toInt()
        val b2 = buffer[offset + 2].toInt()
        val b3 = buffer[offset + 3].toInt()

        // convert signed bytes to unsigned values
        val i0 = if ((b0 and 0x80) == 0x80) (b0 and 0x7F) + 0x80 else b0
        val i1 = if ((b1 and 0x80) == 0x80) (b1 and 0x7F) + 0x80 else b1
        val i2 = if ((b2 and 0x80) == 0x80) (b2 and 0x7F) + 0x80 else b2
        val i3 = if ((b3 and 0x80) == 0x80) (b3 and 0x7F) + 0x80 else b3

        return ((i0.toLong() shl 24) + (i1.toLong() shl 16) + (i2.toLong() shl 8) + i3.toLong())
    }

    /**
     * Reads the NTP time stamp at the given offset in the buffer and returns
     * it as a system time (milliseconds since January 1, 1970).
     */
    private fun readTimeStamp(buffer: ByteArray, offset: Int): Long {
        val seconds = read32(buffer, offset)
        val fraction = read32(buffer, offset + 4)
        // Special case: zero means zero.
        if (seconds == 0L && fraction == 0L) {
            return 0
        }
        return ((seconds - OFFSET_1900_TO_1970) * 1000) + ((fraction * 1000L) / 0x100000000L)
    }

    /**
     * Writes system time (milliseconds since January 1, 1970) as an NTP time stamp
     * at the given offset in the buffer.
     */
    private fun writeTimeStamp(buffer: ByteArray, offset: Int, time: Long) {
        // Special case: zero means zero.
        if (time == 0L) {
            buffer[offset] = 0
            buffer[offset + 1] = 0
            buffer[offset + 2] = 0
            buffer[offset + 3] = 0
            buffer[offset + 4] = 0
            buffer[offset + 5] = 0
            buffer[offset + 6] = 0
            buffer[offset + 7] = 0
            return
        }

        val seconds = time / 1000L
        val milliseconds = time - seconds * 1000L
        val secondsStart = seconds + OFFSET_1900_TO_1970
        val fraction = milliseconds * 0x100000000L / 1000L

        write32(buffer, offset, secondsStart)
        write32(buffer, offset + 4, fraction)
    }

    /**
     * Writes an unsigned 32 bit big endian number to the given offset in the buffer.
     */
    private fun write32(buffer: ByteArray, offset: Int, value: Long) {
        buffer[offset] = (value shr 24).toByte()
        buffer[offset + 1] = (value shr 16).toByte()
        buffer[offset + 2] = (value shr 8).toByte()
        buffer[offset + 3] = (value shr 0).toByte()
    }
}