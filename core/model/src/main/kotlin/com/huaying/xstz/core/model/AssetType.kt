package com.huaying.xstz.core.model

/** 资产大类。沿用旧版四分类，避免用户既有心智模型断裂。 */
enum class AssetType {
    STOCK,
    BOND,
    COMMODITY,
    CASH;

    val displayName: String
        get() = when (this) {
            STOCK -> "股票"
            BOND -> "债券"
            COMMODITY -> "商品"
            CASH -> "现金"
        }

    /**
     * 分组标题用的名字，比 [displayName] 多一个"型"字。
     *
     * 为什么需要两个名字：持仓页同时存在两套维度——
     *   * [AssetType] 回答"在配置里算哪一类"（股票/债券/商品/现金）
     *   * [InstrumentKind] 回答"这是什么东西"（A股/ETF/债券/基金）
     *
     * 两者会交叉：一���"股票型基金"是 kind=FUND、type=STOCK。
     * 如果分组标题写"股票"而卡片徽章写"基金"，同一屏里两个词直接打架，
     * 用户会以为程序算错了。加上"型"字就自洽了——
     * "股票型"组里有 A股、ETF、股票型基金，三者徽章互不矛盾。
     */
    val groupName: String
        get() = when (this) {
            STOCK -> "股票型"
            BOND -> "债券型"
            COMMODITY -> "商品型"
            CASH -> "现金"
        }

    /** 现金不抓行情，净值恒为 1 */
    val isCashLike: Boolean get() = this == CASH

    companion object {
        fun fromName(name: String): AssetType = when (name) {
            "股票", "STOCK" -> STOCK
            "债券", "BOND" -> BOND
            "商品", "COMMODITY", "GOLD" -> COMMODITY
            "现金", "CASH" -> CASH
            else -> STOCK
        }

        /**
         * 从基金名称猜资产类别。
         *
         * 名称关键词远比代码段可靠：代码只能分出"场内/场外"，
         * 而"债券""黄金""货币"这些词直接写在基金全称里。
         *
         * 命中不了就落到 [STOCK]——绝大多数基金是权益类，
         * 猜错的代价比默认成"债券"小。用户在添加页可以随时改。
         */
        fun guess(code: String, name: String): AssetType = when {
            name.contains("货币") || name.contains("现金") ||
                name.contains("理财") || name.contains("活期") -> CASH

            name.contains("债") || name.contains("信用") || name.contains("短融") ||
                name.contains("利率") || name.contains("存单") -> BOND

            name.contains("黄金") || name.contains("白银") || name.contains("商品") ||
                name.contains("原油") || name.contains("豆粕") || name.contains("有色") ||
                name.contains("能源化工") || name.contains("贵金属") -> COMMODITY

            // 代码兜底：51/15/16/18 段以 ETF 为主，但其中债券 ETF 名字里一定有"债"，
            // 前面已拦掉，到这里仍按权益处理
            else -> STOCK
        }
    }
}
