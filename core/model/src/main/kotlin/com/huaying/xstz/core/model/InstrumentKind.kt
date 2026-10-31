package com.huaying.xstz.core.model

/**
 * 标的类型。
 *
 * 与 [AssetType] 的分工：
 *   * **[InstrumentKind]** 回答"这是什么东西"——A股个股、场内 ETF、债券、场外基金。
 *     用于展示标签与选择查询接口。
 *   * **[AssetType]** 回答"它在资产配置里算哪一类"——股票、债券、商品、现金。
 *     用于占比统计与再平衡。
 *
 * 两者不是一一对应：宽基 ETF 的 kind 是 ETF，assetType 是 STOCK；
 * 债券 ETF 的 kind 是 ETF，assetType 是 BOND。合并成一个枚举会让
 * "按资产大类看配置"和"按标的类型看明细"两个需求互相打架。
 */
enum class InstrumentKind {
    STOCK,
    ETF,
    BOND,
    FUND,
    CASH;

    val displayName: String
        get() = when (this) {
            STOCK -> "A股"
            ETF -> "ETF"
            BOND -> "债券"
            FUND -> "基金"
            CASH -> "现金"
        }

    /** 查询该标的是否走场内行情接口（腾讯 qt.gtimg.cn） */
    val isExchangeTraded: Boolean
        get() = this == STOCK || this == ETF || this == BOND
}
