package com.huaying.xstz.core.network

import com.huaying.xstz.core.model.DataSource
import com.huaying.xstz.core.model.InstrumentKind
import com.huaying.xstz.core.model.NavPoint
import com.huaying.xstz.core.model.QuoteCandidate
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

    /**
     * 查单一标的的行情。
     *
     * @param kind 已知标的类型时传入，直接走对应接口，不做猜测。
     *   持仓刷新必须传它——代码段重叠（000001 既是平安银行也是华夏成长），
     *   不传就只能按规则猜，猜错会让持仓净值变成另一只标的的价格。
     */
    suspend fun quote(code: String, kind: InstrumentKind? = null): Sourced<TencentQuoteParser.Estimate?>

    /**
     * 查询该代码可能对应的**全部**标的，用于"添加标的"时的候选列表。
     *
     * 默认实现把 [quote] 的单结果转成一个候选；真实实现会同时查场内与场外。
     * 返回空列表表示确实没有这个标的。
     */
    suspend fun candidates(code: String): List<QuoteCandidate> {
        val result = quote(code)
        if (result.source != DataSource.LIVE) return emptyList()
        val e = result.value ?: return emptyList()
        if (e.name.isBlank()) return emptyList()
        val kind = when {
            CodeClassifier.isStock(code) -> InstrumentKind.STOCK
            CodeClassifier.isExchangeTradedFund(code) -> InstrumentKind.ETF
            CodeClassifier.isBond(code) -> InstrumentKind.BOND
            else -> InstrumentKind.FUND
        }
        return listOf(
            QuoteCandidate(
                code = code,
                name = e.name,
                kind = kind,
                price = e.estimatedNav.toDouble(),
                prevPrice = e.nav.toDouble(),
                changePercent = e.estimatedChangePercent / 100.0,
                dateText = e.navDate.ifBlank { e.estimateTime },
                assetType = QuoteCandidate.assetTypeOf(kind, code, e.name),
            )
        )
    }

    /**
     * 按**名称或代码**搜索标的，用于添加页的联想输入。
     *
     * 与 [candidates] 的区别：那个是"这个代码是什么"，这个是"叫什么的东西有哪些"。
     * 代码是记不住的，让用户为了加茅台去查 600519 不合理；
     * 想买"沪深300"时市面上有十几只 ETF，也只能靠名称搜出来挑。
     *
     * 默认实现返回空（离线样例不能凭空编造搜索结果）。返回空列表时
     * 调用方应回落到"按代码精确查"，而不是告诉用户"搜不到"。
     */
    suspend fun search(keyword: String): List<QuoteCandidate> = emptyList()

    /**
     * 拉取历史净值/价格序列，用于组合快照的历史回填。
     *
     * @param code 标的代码
     * @param kind 标的类型，决定走哪个历史接口
     * @param fromDay 起始日（epochDay，含）
     * @return 按日期升序的净值点。失败返回空列表——**不降级到样例**：
     *   历史数据编不出来，编出来的曲线会让用户以为自己赚过钱。
     */
    suspend fun history(code: String, kind: InstrumentKind, fromDay: Long): List<NavPoint> = emptyList()
}

/**
 * 腾讯 / 天天基金 公开接口实现。
 *
 * 两个地址：
 *   * 场外基金估值  https://fundgz.1234567.com.cn/js/{code}.js   （天天基金，JSONP）
 *   * 场内 A股/ETF/债券  https://qt.gtimg.cn/q={exchange}{code}   （腾讯财经，~ 分隔）
 *
 * 均为公开只读接口，无需鉴权。不保证 SLA，所以必须容忍失败。
 */
class RemoteQuoteSource(
    private val client: OkHttpClient = defaultClient(),
) : QuoteSource {

    override suspend fun quote(code: String, kind: InstrumentKind?): Sourced<TencentQuoteParser.Estimate?> =
        withContext(Dispatchers.IO) {
            when (kind) {
                // 已知是场外基金：只查场外。去场内碰运气反而可能命中同段位的股票，
                // 把基金净值污染成股价（000001 就是这个坑）
                InstrumentKind.FUND -> fetchFundQuote(code) ?: Sourced(null, DataSource.SAMPLE)

                // 已知是场内标的：按交易所前缀查行情
                InstrumentKind.STOCK, InstrumentKind.ETF, InstrumentKind.BOND ->
                    CodeClassifier.tencentQuoteSymbol(code)
                        ?.let { symbol -> fetchQuote(code, symbol) }
                        ?: Sourced(null, DataSource.SAMPLE)

                InstrumentKind.CASH -> Sourced(null, DataSource.SAMPLE)

                // 未知类型：按代码段猜（仅为兼容未显式传 kind 的旧调用点）
                null -> guessByCode(code)
            }
        }

    /**
     * 同时查场外与场内，返回全部候选。
     *
     * 这是添加页唯一可靠的做法：任何静态规则都会在某个代码上猜错，
     * 两个接口都返回结果时，只有用户知道他要的是哪一个。
     */
    override suspend fun candidates(code: String): List<QuoteCandidate> = withContext(Dispatchers.IO) {
        val out = LinkedHashMap<String, QuoteCandidate>()

        // 场外基金：六位数字都试一次，成本是一次 GET
        if (code.length == 6 && code.all { it.isDigit() }) {
            fetchFundQuote(code)?.value?.takeIf { it.name.isNotBlank() }?.let { e ->
                out["FUND"] = e.toCandidate(code, InstrumentKind.FUND)
            }
        }

        // 场内：股票 / ETF / 债券
        CodeClassifier.tencentQuoteSymbol(code)?.let { symbol ->
            fetchQuote(code, symbol)?.value?.takeIf { it.name.isNotBlank() }?.let { e ->
                val kind = kindOf(code)
                out[kind.name] = e.toCandidate(code, kind)
            }
        }

        out.values.toList()
    }

    private fun kindOf(code: String): InstrumentKind = when {
        CodeClassifier.isStock(code) -> InstrumentKind.STOCK
        CodeClassifier.isExchangeTradedFund(code) -> InstrumentKind.ETF
        CodeClassifier.isBond(code) -> InstrumentKind.BOND
        else -> InstrumentKind.FUND
    }

    /**
     * 名称 / 代码联想搜索。
     *
     * 只查一个接口就够：smartbox 同时返回场内（GP-A/ETF/LOF）与场外（KJ/QDII），
     * 类型码已经给出了准确分类，不需要像 [candidates] 那样分别去问两个行情接口。
     *
     * 搜索结果**不带价格**：一次搜索可能返回 20 条，逐条查行情就是 20 个请求，
     * 在手机网络下要等好几秒。价格留到用户选中之后再查——那时只有一个请求。
     * 界面上候选行显示代码与类型，不显示价格，这是刻意的取舍。
     */
    override suspend fun search(keyword: String): List<QuoteCandidate> = withContext(Dispatchers.IO) {
        val q = keyword.trim()
        if (q.length < 1) return@withContext emptyList()
        // OkHttp 的 url() 不接受非 ASCII 与空格，中文关键词必须先百分号编码，
        // 否则会抛 IllegalArgumentException，搜索框输入第一个汉字就崩
        val encoded = java.net.URLEncoder.encode(q, "UTF-8")
        val body = httpGet("$SUGGEST_BASE$encoded&t=all") ?: return@withContext emptyList()
        TencentSuggestParser.parse(body).map { hit ->
            QuoteCandidate(
                code = hit.code,
                name = hit.name,
                kind = hit.kind,
                price = 0.0,
                prevPrice = 0.0,
                changePercent = 0.0,
                dateText = "",
                assetType = QuoteCandidate.assetTypeOf(hit.kind, hit.code, hit.name),
            )
        }
    }

    /**
     * 历史序列拉取。
     *
     * 场外基金走天天基金 `lsjz`（分页，20 条/页，一年约 250 个交易日 ≈ 13 页）；
     * 场内走腾讯 `fqkline`（一次能返回足够长的区间，不必分页）。
     *
     * 场外分页有**硬上限**：最多 [MAX_FUND_PAGES] 页。理由是用户要的是"最近一年"，
     * 无限翻页只会在网络差时把添加流程拖死，而收益曲线多三年数据并不会变得更有用。
     */
    override suspend fun history(
        code: String,
        kind: InstrumentKind,
        fromDay: Long,
    ): List<NavPoint> = withContext(Dispatchers.IO) {
        runCatching {
            if (kind == InstrumentKind.FUND) fetchFundHistory(code, fromDay)
            else fetchKlineHistory(code, fromDay)
        }.getOrDefault(emptyList())
    }

    /** 场外基金历史净值：分页拉取，遇到早于 [fromDay] 的记录停止翻页 */
    private fun fetchFundHistory(code: String, fromDay: Long): List<NavPoint> {
        val out = ArrayList<NavPoint>()
        var page = 1
        while (page <= MAX_FUND_PAGES) {
            val url = "$FUND_HISTORY_BASE?fundCode=$code&pageIndex=$page&pageSize=20"
            val body = httpGet(url, "https://fundf10.eastmoney.com/") ?: break
            val points = NavHistoryParser.parseFundHistory(body)
            if (points.isEmpty()) break
            out += points
            // 本页最旧的一条已经早于起点，再翻也只会更旧
            if (points.minOf { it.epochDay } <= fromDay) break
            page++
        }
        return out.filter { it.epochDay >= fromDay }.distinctBy { it.epochDay }.sortedBy { it.epochDay }
    }

    /** 场内历史收盘价：一次请求拿足区间，不需要分页 */
    private fun fetchKlineHistory(code: String, fromDay: Long): List<NavPoint> {
        val symbol = CodeClassifier.tencentQuoteSymbol(code) ?: return emptyList()
        val url = "$KLINE_BASE?param=$symbol,day,,,$MAX_KLINE_DAYS,qfq"
        val body = httpGet(url) ?: return emptyList()
        return NavHistoryParser.parseKlineHistory(symbol, body)
            .filter { it.epochDay >= fromDay }
    }

    private fun guessByCode(code: String): Sourced<TencentQuoteParser.Estimate?> {
        if (CodeClassifier.isOpenEndFund(code)) {
            fetchFundQuote(code)?.let { return it }
        }
        CodeClassifier.tencentQuoteSymbol(code)?.let { symbol ->
            fetchQuote(code, symbol)?.let { return it }
        }
        return fetchFundQuote(code) ?: Sourced(null, DataSource.SAMPLE)
    }

    /**
     * 场外基金：腾讯 `jj` 前缀。
     *
     * 不再用天天基金的 fundgz 接口——2026-10 实测它已返回东财 404 页面
     * （带 `?rt=` 缓存参数也一样），基金类标的全部查不到。
     * 用同一个域名同时覆盖场内与场外，少一个外部依赖。
     */
    private fun fetchFundQuote(code: String): Sourced<TencentQuoteParser.Estimate?>? {
        val body = httpGet("$QUOTE_BASE" + "jj$code") ?: return null
        val parsed = TencentQuoteParser.parseFundQuote(code, body) ?: return null
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

    private fun httpGet(url: String, referer: String = "https://fund.eastmoney.com/"): String? = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Referer", referer)
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body ?: return null
            // qt.gtimg.cn 固定返回 GBK，且不总在响应头里声明 charset。
            // 交给 OkHttp 默认按 UTF-8 解会把"华夏成长混合"解成乱码。
            String(body.bytes(), java.nio.charset.Charset.forName("GBK"))
        }
    }.getOrNull()

    /** 场内接口不返回时间戳，用本机时间标注，UI 上说明的是"抓取时刻"而非"行情时刻" */
    private fun nowText(): String = java.time.LocalDateTime.now(MarketZone)
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

    companion object {
        private const val QUOTE_BASE = "https://qt.gtimg.cn/q="

        /**
         * 智能搜索。返回体是 `v_hint="sh~600519~\u8d35..."`，GBK 编码，名称是 Unicode 转义。
         * 必须带 `t=all`：只查某个子集会漏掉另一类标的（搜"华夏"既有股票也有 LOF）。
         */
        private const val SUGGEST_BASE = "https://smartbox.gtimg.cn/s3/?t=all&q="

        /**
         * 场外基金历史净值。**必须带 Referer**，否则东财返回空体。
         *
         * 注意 [httpGet] 里统一按 GBK 解码——这个接口其实返回 UTF-8。
         * 但它只含数字与 ASCII，GBK 解码 UTF-8 的纯 ASCII 字节结果相同，
         * 所以不需要为它单独开一条解码路径。
         */
        private const val FUND_HISTORY_BASE = "https://api.fund.eastmoney.com/f10/lsjz"

        /** 场内历史 K 线 */
        private const val KLINE_BASE = "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get"

        /** 场外分页上限：13 页 ≈ 一年交易日，再多没有意义 */
        private const val MAX_FUND_PAGES = 14

        /** 场内一次请求的天数：给足一年多一点，覆盖节假日稀疏 */
        private const val MAX_KLINE_DAYS = 400
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

/** 把接口返回的 Estimate 折算成候选。kind 与名称共同决定资产大类。 */
private fun TencentQuoteParser.Estimate.toCandidate(code: String, kind: InstrumentKind): QuoteCandidate =
    QuoteCandidate(
        code = code,
        name = name,
        kind = kind,
        price = estimatedNav.toDouble(),
        prevPrice = nav.toDouble(),
        changePercent = estimatedChangePercent / 100.0,
        dateText = navDate.ifBlank { estimateTime },
        assetType = QuoteCandidate.assetTypeOf(kind, code, name),
    )

/**
 * 离线样例数据源。作为降级链末端，永远有返回。
 */
class SampleQuoteSource : QuoteSource {
    override suspend fun quote(code: String, kind: InstrumentKind?): Sourced<TencentQuoteParser.Estimate?> =
        Sourced(SampleData.estimateOf(code), DataSource.SAMPLE, System.currentTimeMillis())

    /** 样例不参与"添加时查代码"：给任意 6 位数编个名字，等于告诉用户这个代码存在 */
    override suspend fun candidates(code: String): List<QuoteCandidate> = emptyList()
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

    override suspend fun quote(code: String, kind: InstrumentKind?): Sourced<TencentQuoteParser.Estimate?> {
        val live = runCatching { remote.quote(code, kind) }.getOrNull()
        if (live?.value != null && live.source == DataSource.LIVE) {
            cache[cacheKey(code, kind)] = live
            return live
        }
        cache[cacheKey(code, kind)]?.let { hit ->
            // 命中缓存但明确标记为 CACHE，UI 会显示"缓存"角标
            return hit.copy(source = DataSource.CACHE)
        }
        return fallback.quote(code, kind)
    }

    /** 候选列表不走缓存与样例：这是"查询是否存在"，缓存会让已下架的标的永远查得到 */
    override suspend fun candidates(code: String): List<QuoteCandidate> =
        runCatching { remote.candidates(code) }.getOrDefault(emptyList())

    /** 搜索同理：本轮输入的结果不缓存，避免"改了关键词还看到上一轮结果" */
    override suspend fun search(keyword: String): List<QuoteCandidate> =
        runCatching { remote.search(keyword) }.getOrDefault(emptyList())

    /**
     * 历史序列直接透传给远端，**不缓存也不降级**。
     *
     * 历史数据的缓存价值低（本地 nav_history 已经落库了，第二次直接从库里读），
     * 而降级的危害极高：编出来的历史会让收益曲线凭空长出一段不存在的行情。
     */
    override suspend fun history(code: String, kind: InstrumentKind, fromDay: Long): List<NavPoint> =
        runCatching { remote.history(code, kind, fromDay) }.getOrDefault(emptyList())

    private fun cacheKey(code: String, kind: InstrumentKind?) = "${kind?.name ?: "?"}:$code"
}
