package com.huaying.xstz.data.repository

import android.os.SystemClock
import com.huaying.xstz.util.SntpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.threeten.bp.LocalDate
import org.threeten.bp.ZoneId

object TimeRepository {
    // 存储 "真实时间 - SystemClock.elapsedRealtime()" 的差值
    // 初始值设为 "System.currentTimeMillis() - SystemClock.elapsedRealtime()"，即假设本地时间是准的
    private var timeOffset: Long = System.currentTimeMillis() - SystemClock.elapsedRealtime()
    
    private val client = OkHttpClient()
    @Volatile private var isSynced = false
    private val CHINA_ZONE = ZoneId.of("GMT+8")

    fun getCurrentTimeMillis(): Long {
        // 使用 elapsedRealtime 避免本地时间被篡改
        return SystemClock.elapsedRealtime() + timeOffset
    }
    
    fun getCurrentLocalDate(): LocalDate {
        return org.threeten.bp.Instant.ofEpochMilli(getCurrentTimeMillis())
            .atZone(CHINA_ZONE)
            .toLocalDate()
    }
    
    fun isTimeSynced(): Boolean = isSynced

    suspend fun syncTime() = withContext(Dispatchers.IO) {
        // 策略1：优先尝试 NTP (阿里云)
        if (syncNtp("ntp.aliyun.com")) return@withContext
        
        // 策略2：备用 NTP (pool.ntp.org)
        if (syncNtp("pool.ntp.org")) return@withContext
        
        // 策略3：降级到 HTTP 接口 (淘宝)
        syncHttp()
    }
    
    private fun syncNtp(host: String): Boolean {
        val sntpClient = SntpClient()
        // 5秒超时
        if (sntpClient.requestTime(host, 5000)) {
            val ntpTime = sntpClient.getNtpTime()
            val ntpTimeReference = sntpClient.getNtpTimeReference()
            
            // 计算 offset = 真实时间 - elapsedRealtime
            timeOffset = ntpTime - ntpTimeReference
            isSynced = true
            return true
        }
        return false
    }

    private fun syncHttp() {
        try {
            val request = Request.Builder()
                .url("http://api.m.taobao.com/rest/api3.do?api=mtop.common.getTimestamp")
                .build()

            val startTicks = SystemClock.elapsedRealtime()
            val response = client.newCall(request).execute()
            val endTicks = SystemClock.elapsedRealtime()
            
            if (response.isSuccessful && response.body != null) {
                val jsonStr = response.body!!.string()
                val json = JSONObject(jsonStr)
                val serverTime = json.getJSONObject("data").getString("t").toLong()
                
                if (serverTime > 0) {
                    val networkDelay = (endTicks - startTicks) / 2
                    val estimatedServerTimeNow = serverTime + networkDelay
                    
                    // offset = 估算的服务器当前时间 - 当前的 elapsedRealtime
                    timeOffset = estimatedServerTimeNow - endTicks
                    isSynced = true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // 失败时保持原有 offset (即继续使用之前的同步结果或本地时间)
        }
    }
}
