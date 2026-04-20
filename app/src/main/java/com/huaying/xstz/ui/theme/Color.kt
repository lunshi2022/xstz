package com.huaying.xstz.ui.theme

import androidx.compose.ui.graphics.Color
import com.huaying.xstz.data.entity.AssetType

// Asset Type Colors
val StockColor = Color(0xFF2196F3)
val BondColor = Color(0xFF4CAF50)
val GoldColor = Color(0xFFFF9800)
val CashColor = Color(0xFF9E9E9E)

// Brand Colors
val BrandBlue = Color(0xFF1890FF)
val DangerRed = Color(0xFFF5222D)
val SuccessGreen = Color(0xFF52C41A)

// Light Mode Colors
val LightBackground = Color(0xFFF5F5F7)  // 页面背景使用浅灰色，与卡片形成对比
val LightSurface = Color(0xFFFFFFFF)     // 卡片背景使用纯白色
val LightSurfaceSecondary = Color(0xFFFFFFFF)  // 次级卡片背景也使用白色
val LightTextPrimary = Color(0xFF1D1D1F)
val LightTextSecondary = Color(0xFF86868B)
val LightPriceBox = Color(0xFFF5F5F7)    // 价格卡片使用浅灰背景
val LightDangerBg = Color(0xFFFFF1F0)
val LightCodeBg = Color(0xFFE6F7FF)
val LightIconBg = Color(0xFFF5F5F7)

// Dark Mode Colors
val DarkBackground = Color(0xFF000000)      // 页面背景使用纯黑
val DarkSurface = Color(0xFF1C1C1E)         // 卡片背景使用深灰色
val DarkSurfaceSecondary = Color(0xFF1C1C1E)  // 次级卡片背景
val DarkTextPrimary = Color(0xFFFFFFFF)
val DarkTextSecondary = Color(0xFF8E8E93)
val DarkPriceBox = Color(0xFF2C2C2E)        // 价格卡片使用稍浅的灰色
val DarkDangerBg = Color(0xFF2A1215)
val DarkCodeBg = Color(0xFF15395B)
val DarkIconBg = Color(0xFF2C2C2E)

fun getColorForAssetType(type: AssetType): Color {
    return when (type) {
        AssetType.STOCK -> StockColor
        AssetType.BOND -> BondColor
        AssetType.COMMODITY -> GoldColor
        AssetType.CASH -> CashColor
    }
}
