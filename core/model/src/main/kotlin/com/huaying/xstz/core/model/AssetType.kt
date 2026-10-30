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
    }
}
