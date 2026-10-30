package com.huaying.xstz.core.network

import com.huaying.xstz.core.model.DataSource
import com.huaying.xstz.core.model.Sourced
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 行情数据源。
 *
 * 降级链是硬性要求（用户选的口径）：真实接口 → 缓存 → 离线样例。
 * 任何一环失败都静默降级并在返回值上标注 [DataSource]，绝不让界面空着。
 */
interface QuoteSource {
    suspend fun quote(code: String): Sourced<TencentQuoteParser.Estimate?>
}

/**
 * 腾讯公开行情接口实现。
 *
 * 两个地址：
 *   * 场外基金估值  https://fundgz.1234567.com.cn/js/{code}.js   （天天基金，JSONP）
 *   * 场内股票/ETF  https://qt.gtimg.cn/q={exchange}{code}       （腾讯财经，~ 分隔）
 *
 * 均为公开只读接口，无需鉴权。不保证 SLA，所以必须容忍失败。
 */
class RemoteQuoteSource(
    private val client: OkHttpClient = defaultClient(),
) : QuoteSource {

    override suspend fun quote(code: String): Sourced<TencentQuoteParser.Estimate?> =
        withContext(Dispatchers.IO) {
            // 场外基金优先（大多数用户持仓是场外）
            if (CodeClassifier.isOpenEndFund(code)) {
                fetchEstimate(code)?.let { return@withContext it }
            }

            // 场内品种：先拿行情，再折算成 Estimate，保持上层只有一种返回类型
            CodeClassifier.tencentQuoteSymbol(code)?.let { symbol ->
                fetchQuote(code, symbol)?.let { return@withContext it }
            }

            // 场外失败也再试一次估值接口，覆盖"代码分类判错"的情况
            fetchEstimate(code) ?: Sourced(null, DataSource.SAMPLE)
        }

    private fun fetchEstimate(code: String): Sourced<TencentQuoteParser.Estimate?>? {
        val url = "$FUND_BASE$code.js?rt=${System.currentTimeMillis()}"
        val body = httpGet(url) ?: return null
        val parsed = TencentQuoteParser.parseEstimate(code, body) ?: return null
        return Sourced(parsed, DataSource.LIVE, System.currentTimeMillis())
    }

    private fun fetchQuote(code: String, symbol: String): Sourced<TencentQuoteParser.Estimate?>? {
        val body = httpGet("$QUOTE_BASE$symbol") ?: return null
        val q = TencentQuoteParser.parseQuote(code, body) ?: return null
        return Sourced(
            TencentQuoteParser.Estimate(
                code = code,
                name = q.name,
                nav = q.prevNav,
                estimatedNav = q.nav,
                estimatedChangePercent = q.changeRate * 100.0,
                navDate = "",
                estimateTime = nowText(),
            ),
            DataSource.LIVE,
            System.currentTimeMillis(),
        )
    }

    private fun httpGet(url: String): String? = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Referer", "https://fund.eastmoney.com/")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            resp.body?.string()
        }
    }.getOrNull()

    /** 场内接口不返回时间戳，用本机时间标注，UI 上说明的是"抓取时刻"而非"行情时刻" */
    private fun nowText(): String = java.time.LocalDateTime.now(MarketZone)
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

    companion object {
        private const val FUND_BASE = "https://fundgz.1234567.com.cn/js/"
        private const val QUOTE_BASE = "https://qt.gtimg.cn/q="
        private const val UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
        private val MarketZone = java.time.ZoneId.of("Asia/Shanghai")

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            // 行情是"锦上添花"，宁可快速失败降级，也不要让用户对着转圈等 30 秒
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}

/**
 * 离线样例数据源。作为降级链末端，永远有返回。
 */
class SampleQuoteSource : QuoteSource {
    override suspend fun quote(code: String): Sourced<TencentQuoteParser.Estimate?> =
        Sourced(SampleData.estimateOf(code), DataSource.SAMPLE, System.currentTimeMillis())
}

/**
 * 带缓存的装饰器：真实接口失败时，先回落到上次成功的结果，再退到样例。
 *
 * 缓存放在内存里就够：进程重启后重新抓一次成本很低，
 * 而且行情本来就有时效性，落库反而会让用户看到过期数据却不自知。
 */
class CachedQuoteSource(
    private val remote: QuoteSource,
    private val fallback: QuoteSource,
) : QuoteSource {

    private val cache = java.util.concurrent.ConcurrentHashMap<String, Sourced<TencentQuoteParser.Estimate?>>()

    override suspend fun quote(code: String): Sourced<TencentQuoteParser.Estimate?> {
        val live = runCatching { remote.quote(code) }.getOrNull()
        if (live?.value != null && live.source == DataSource.LIVE) {
            cache[code] = live
            return live
        }
        cache[code]?.let { hit ->
            // 命中缓存但明确标记为 CACHE，UI 会显示"缓存"角标
            return hit.copy(source = DataSource.CACHE)
        }
        val sample = fallback.quote(code)
        return sample
    }
}
