package com.huaying.xstz.core.network

import com.huaying.xstz.core.model.AssetType
import com.huaying.xstz.core.model.Nav

/**
 * 离线样例数据。
 *
 * 为什么内置：用户要求「真实数据源 + 离线兜底」。
 * 接口被限流、被墙、被改字段都是常态，开源项目最难堪的体验就是开屏一片空白。
 * 有了样例，任何网络环境下 App 都能完整演示，且 UI 会明确标注「离线样例」，
 * 不会让人把演示数据当成自己的资产。
 *
 * 数据构造原则：
 *   * 覆盖四种资产类别，能体现再平衡的差异
 *   * 净值曲线带真实感的波动，不是单调直线（否则图表看起来像假的）
 *   * 全部为虚构基金名，避免误认
 */
object SampleData {

    data class SampleFund(
        val code: String,
        val name: String,
        val type: AssetType,
        val nav: Double,
        val prevNav: Double,
        val targetRatio: Double,
        val shares: Double,
        val costPerShare: Double,
        /** 过去 180 天的净值序列，用于图表 */
        val history: List<Double>,
    )

    /** 用确定性伪随机生成净值曲线：同一 code 每次得到的曲线一致，截图与测试可复现 */
    private fun curve(seed: Int, base: Double, days: Int = 180): List<Double> {
        var x = seed.toLong() * 6364136223846793005L + 1442695040888963407L
        val out = ArrayList<Double>(days)
        var v = base * 0.86
        repeat(days) { i ->
            x = x * 6364136223846793005L + 1442695040888963407L
            // 取高位做 [-1,1] 的抖动，再用正弦叠一层趋势，避免纯噪声看起来像心电图
            val noise = ((x ushr 40) % 2000 - 1000) / 1000.0 * 0.006
            val trend = kotlin.math.sin(i / 26.0) * 0.004 + 0.0012
            v *= (1.0 + noise + trend)
            out.add(v)
        }
        // 让末值落在 base 附近，避免图表与当前净值对不上
        val scale = base / out.last()
        return out.map { it * scale }
    }

    val funds: List<SampleFund> = listOf(
        SampleFund(
            code = "900001",
            name = "示例·沪深300指数",
            type = AssetType.STOCK,
            nav = 3.8421,
            prevNav = 3.8106,
            targetRatio = 0.40,
            shares = 12_500.0,
            costPerShare = 3.4,
            history = curve(11, 3.8421),
        ),
        SampleFund(
            code = "900002",
            name = "示例·中证500增强",
            type = AssetType.STOCK,
            nav = 2.1573,
            prevNav = 2.1804,
            targetRatio = 0.20,
            shares = 8_000.0,
            costPerShare = 2.31,
            history = curve(23, 2.1573),
        ),
        SampleFund(
            code = "900003",
            name = "示例·国债ETF",
            type = AssetType.BOND,
            nav = 1.0864,
            prevNav = 1.0859,
            targetRatio = 0.25,
            shares = 22_000.0,
            costPerShare = 1.05,
            history = curve(37, 1.0864),
        ),
        SampleFund(
            code = "900004",
            name = "示例·黄金ETF",
            type = AssetType.COMMODITY,
            nav = 5.6210,
            prevNav = 5.5320,
            targetRatio = 0.10,
            shares = 1_800.0,
            costPerShare = 5.1,
            history = curve(53, 5.6210),
        ),
        SampleFund(
            code = "900005",
            name = "示例·货币基金",
            type = AssetType.CASH,
            nav = 1.0,
            prevNav = 1.0,
            targetRatio = 0.05,
            shares = 28_000.0,
            costPerShare = 1.0,
            history = List(180) { 1.0 },
        ),
    )

    /** 按代码查样例；找不到就造一个稳定的假数据，保证 UI 永远不会空 */
    fun forCode(code: String, name: String = "", type: AssetType = AssetType.STOCK): SampleFund {
        funds.firstOrNull { it.code == code }?.let { return it }
        val base = 1.0 + (code.hashCode() and 0x7FFFFFFF) % 400 / 100.0
        return SampleFund(
            code = code,
            name = name.ifBlank { "示例·$code" },
            type = type,
            nav = base,
            prevNav = base * 0.995,
            targetRatio = 0.0,
            shares = 0.0,
            costPerShare = base * 0.95,
            history = curve(code.hashCode(), base),
        )
    }

    fun estimateOf(code: String): TencentQuoteParser.Estimate {
        val s = forCode(code)
        return TencentQuoteParser.Estimate(
            code = s.code,
            name = s.name,
            nav = Nav.fromDouble(s.prevNav),
            estimatedNav = Nav.fromDouble(s.nav),
            estimatedChangePercent = (s.nav - s.prevNav) / s.prevNav * 100.0,
            navDate = "1970-01-01",
            estimateTime = "离线样例",
        )
    }
}
