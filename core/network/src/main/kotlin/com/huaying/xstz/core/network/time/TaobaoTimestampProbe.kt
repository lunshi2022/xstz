package com.huaying.xstz.core.network.time

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 淘宝时间戳接口探针（HTTP 降级链末端）。
 *
 * 阿里系这个公开接口只回一个服务器毫秒时间戳，不需要鉴权，
 * 也不需要像 SNTP 那样自己组包解包——作为 SNTP 两级都失败后的兜底。
 *
 * 解析容错：任何非 `data.t` 的数字字段、空体、超时都返回 `null`，
 * 让上一级继续兜底，而不是直接抛异常。
 */
class TaobaoTimestampProbe(
    private val client: OkHttpClient = defaultClient(),
    private val url: String = "https://api.m.taobao.com/rest/api3.do?api=mtop.common.getTimestamp",
) : TimeProbe {

    override suspend fun probe(): Long? = withContext(Dispatchers.IO) {
        runCatching {
            val resp = client.newCall(Request.Builder().url(url).build()).execute()
            if (!resp.isSuccessful) return@runCatching null
            val text = resp.body?.string() ?: return@runCatching null
            val tree = Json.parseToJsonElement(text).jsonObject
            val t = tree["data"]
                ?.jsonObject
                ?.get("t")
                ?.jsonPrimitive
                ?.content
                ?: return@runCatching null
            t.toLongOrNull()
        }.getOrNull()
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .callTimeout(6, TimeUnit.SECONDS)
            .build()
    }
}
