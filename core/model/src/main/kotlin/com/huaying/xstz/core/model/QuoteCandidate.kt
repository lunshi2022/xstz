package com.huaying.xstz.core.model

/**
 * 一次查询命中的一个候选标的。
 *
 * 为什么是"候选列表"而不是"一个结果"：
 * A 股代码段与基金代码段重叠。`000001` 同时是**平安银行**（深市 A 股）
 * 和**华夏成长**（场外基金）；`110022` 既是可转债区间也可能命中场外基金。
 * 靠代码段猜类型必然猜错一方——任何"优先级"规则都是在替用户做决定。
 *
 * 正确做法是同时查场内与场外两个接口，把命中结果都摆出来，由用户点选。
 * 这也顺带解决了"查不到"的判定：一个候选都没有，才是真的没有。
 */
data class QuoteCandidate(
    val code: String,
    val name: String,
    val kind: InstrumentKind,
    /** 最新价 / 实时估值；场外基金为估值，场内为现价 */
    val price: Double,
    /** 昨收价 / 昨日单位净值 */
    val prevPrice: Double,
    /** 涨跌幅，小数（0.0045 表示 +0.45%） */
    val changePercent: Double,
    /** 行情时间或净值日期，可能为空 */
    val dateText: String,
    /** 在资产配置里的归类，由 kind 与名称共同决定 */
    val assetType: AssetType,
) {
    companion object {
        /**
         * 由类型与名称共同决定资产大类。
         *
         * ETF 不能一律算股票：债券 ETF、黄金 ETF 名称里带"债""黄金"，
         * 交给 [AssetType.guess] 按关键词判断，比按代码段猜准确得多。
         */
        fun assetTypeOf(kind: InstrumentKind, code: String, name: String): AssetType =
            when (kind) {
                InstrumentKind.CASH -> AssetType.CASH
                InstrumentKind.BOND -> AssetType.BOND
                InstrumentKind.STOCK -> AssetType.STOCK
                InstrumentKind.ETF, InstrumentKind.FUND -> AssetType.guess(code, name)
            }
    }
}
