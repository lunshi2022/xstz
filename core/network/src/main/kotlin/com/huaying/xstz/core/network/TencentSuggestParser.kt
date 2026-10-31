package com.huaying.xstz.core.network

import com.huaying.xstz.core.model.InstrumentKind

/**
 * 腾讯智能搜索（smartbox）响应解析器。纯函数，无网络依赖。
 *
 * 为什么需要它：这个应用支持 A股 / ETF / 债券 / 场外基金 四类标的，
 * 而**代码是记不住的**。用户想加"茅台"，让他去查 600519 是不合理的；
 * 想加"沪深300"，市面上有十几只跟踪它的 ETF，只能靠名称搜出来挑。
 *
 * 响应格式（`https://smartbox.gtimg.cn/s3/?q=茅台&t=all`）：
 *
 *   v_hint="sh~600519~\u8d35\u5dde\u8305\u53f0~gzmt~GP-A"
 *
 *   * 条目之间用 `^` 分隔
 *   * 每条 5 段：市场 / 代码 / 名称 / 拼音首字母 / 类型码
 *   * **名称是 Unicode 转义**（`\u8d35`），不是 UTF-8 原文——直接读会看到一串反斜杠
 *   * 无结果时整体是 `v_hint="N";`
 *
 * 一个接口同时覆盖场内与场外，类型码已经给出了准确分类，
 * 比"按代码段猜"可靠得多：
 *
 *   | 类型码 | 含义         | 本应用归类        |
 *   |--------|--------------|-------------------|
 *   | GP-A   | 沪深 A 股    | [InstrumentKind.STOCK] |
 *   | ETF    | 场内 ETF     | [InstrumentKind.ETF]   |
 *   | LOF    | 上市开放式基金 | [InstrumentKind.ETF] |
 *   | FJ     | 封闭式基金   | [InstrumentKind.ETF]   |
 *   | KJ     | 场外开放式基金 | [InstrumentKind.FUND] |
 *   | QDII   | 场外 QDII    | [InstrumentKind.FUND]  |
 *   | ZS     | 指数（不可持有） | 丢弃             |
 *
 * 港股（`hk`）与美股（`us`）一律丢弃：本应用不做境外持仓。
 *
 * 已知边界：**搜不到债券**。"22国债01""019547" 搜索均返回空或有歧义，
 * 交易所债券只能靠代码添加。这一限制在界面文案里明确告知，不假装能搜。
 */
object TencentSuggestParser {

    /** 一条搜索命中，尚未带行情 */
    data class Hit(
        val code: String,
        val name: String,
        val kind: InstrumentKind,
    )

    /** 搜索结果上限。20 条足够，再多用户也不会往下翻。 */
    const val MAX_HITS = 20

    /**
     * 解析响应。任何格式异常都返回空列表，不抛异常——
     * 搜索是"输入即触发"的高频路径，一次异常会让输入框卡住。
     */
    fun parse(raw: String): List<Hit> {
        val body = extractPayload(raw) ?: return emptyList()
        if (body == "N" || body.isBlank()) return emptyList()

        val out = LinkedHashMap<String, Hit>()
        for (entry in body.split('^')) {
            val hit = parseEntry(entry) ?: continue
            // 同一代码在 A股与基金段重复出现时只留第一条（接口按相关性排序）
            out.putIfAbsent("${hit.kind.name}:${hit.code}", hit)
            if (out.size >= MAX_HITS) break
        }
        return out.values.toList()
    }

    private fun parseEntry(entry: String): Hit? {
        val f = entry.split('~')
        if (f.size < 5) return null

        val market = f[0].trim().lowercase()
        val code = f[1].trim()
        val name = decodeUnicode(f[2].trim())
        val typeCode = f[4].trim().uppercase()

        if (code.isBlank() || name.isBlank()) return null
        // 只认沪深与场外基金市场；hk / us 直接丢
        if (market != "sh" && market != "sz" && market != "jj") return null
        // 代码必须是六位数字：搜索结果里偶有期权、外汇代码混进来
        if (code.length != 6 || code.any { !it.isDigit() }) return null

        val kind = kindOf(typeCode) ?: return null
        return Hit(code = code, name = name, kind = kind)
    }

    /**
     * 类型码 → 标的类型。返回 null 表示"这个结果我们不要"。
     *
     * `ZS`（指数）必须丢弃：用户看到"沪深300 000300"会以为能买，
     * 加进来却永远查不到行情——指数本身不可持有，只能通过 ETF 持有。
     */
    private fun kindOf(typeCode: String): InstrumentKind? = when {
        typeCode == "GP-A" -> InstrumentKind.STOCK
        typeCode == "ETF" || typeCode == "LOF" || typeCode == "FJ" -> InstrumentKind.ETF
        typeCode == "KJ" || typeCode == "QDII" -> InstrumentKind.FUND
        // GP（沪深以外股票）、ZS（指数）以及未知类型一律不要
        else -> null
    }

    /** 从 `v_hint="..."` 取出引号内容 */
    internal fun extractPayload(raw: String): String? {
        val start = raw.indexOf('"')
        if (start == -1) return null
        val end = raw.lastIndexOf('"')
        if (end <= start) return null
        return raw.substring(start + 1, end).trim().ifBlank { null }
    }

    /**
     * 解码 `\u8d35\u5dde` 这类转义。
     *
     * 不引 JSON 库：这里只需要处理 `\uXXXX` 一种转义（接口从不返回其它转义序列），
     * 手写 20 行比多一个依赖划算，而且能精确控制"遇到畸形转义原样保留"。
     */
    internal fun decodeUnicode(s: String): String {
        if (!s.contains("\\u")) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 5 < s.length && (s[i + 1] == 'u' || s[i + 1] == 'U')) {
                val hex = s.substring(i + 2, i + 6)
                val decoded = hex.toIntOrNull(16)
                if (decoded != null) {
                    sb.append(decoded.toChar())
                    i += 6
                    continue
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }
}
