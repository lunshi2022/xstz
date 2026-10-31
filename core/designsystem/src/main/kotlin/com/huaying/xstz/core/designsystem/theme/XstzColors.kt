package com.huaying.xstz.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * 色彩系统。
 *
 * 设计原则：**底色承载色彩，玻璃只负责折射**。
 * 旧版把所有强调色都堆在卡片本身，导致层叠时颜色互相污染、对比度不可控。
 *
 * 数值来源：
 *   * 背景网格光斑用低饱和大色块，模拟 iOS 壁纸透过玻璃的效果
 *   * 涨跌色遵循中国习惯：涨红跌绿（与欧美相反，这是硬性要求）
 *   * 所有文字色与玻璃底的对比度都在 4.5:1 以上，深色模式另有一套
 */
object XstzColors {

    // ---------------- 品牌 ----------------
    // 3568EA：原 3A6FF7 与白字对比 4.38 < WCAG AA 4.5。加深此档后 4.86，
    // 是"肉眼几乎无差"的最小加深量（AccentSoft 仍留给深色主题的强调）。
    val Accent = Color(0xFF3568EA)
    val AccentSoft = Color(0xFF6C93FF)
    val AccentDeep = Color(0xFF2450C8)

    // ---------------- 涨跌（A 股习惯：红涨绿跌）----------------
    // Down 加深：原 12A67B 在浅色实卡上 2.94 < 大字号 3.0 下限；0F9E73 为 3.23。
    val Up = Color(0xFFE5484D)
    val UpSoft = Color(0x33E5484D)
    val Down = Color(0xFF0F9E73)
    val DownSoft = Color(0x330F9E73)
    val Flat = Color(0xFF8A93A6)

    // ---------------- 资产类别 ----------------
    val Stock = Color(0xFF4C7DF0)
    val Bond = Color(0xFF7C5CD6)
    val Commodity = Color(0xFFD99B2B)
    val Cash = Color(0xFF3FA97F)

    // ---------------- 浅色 ----------------
    object Light {
        val background = Color(0xFFEEF1F7)
        val backgroundElevated = Color(0xFFF7F9FC)

        /** 玻璃底：白 + 低透明度，叠在背景光斑上产生"磨砂"感 */
        val glassFill = Color(0x66FFFFFF)
        val glassFillStrong = Color(0x99FFFFFF)
        val glassStroke = Color(0x4DFFFFFF)
        val glassShadow = Color(0x14000000)

        val textPrimary = Color(0xFF11151C)
        val textSecondary = Color(0xFF343E52)
        /**
         * 65707F → 4E5765（2026-11-01）。
         *
         * 原值在"卡片是纯白"的假设下算得 4.77，但真机实测「更新于」只有 **3.46**，
         * 因为它压在 MeshBackdrop 的光斑上（#BBD1FF 以 0.85 alpha 叠在近白底 → 实测背景 214），
         * 而不是纯白。低于 WCAG AA 小字门槛 4.5。
         *
         * 为什么改文字而不是改背景：要让那块背景亮到 241，光斑 alpha 得从 0.85 降到 0.1 以下，
         * 等于把光斑抹掉——而光斑正是"玻璃看起来像玻璃"的前提，不能动。
         *
         * 同时把 textSecondary 一并下调（4A5468 → 343E52），否则三级加深后会与二级撞色，
         * 二级/三级的层次会压平。现在三级的亮度梯度是 24 / 61 / 86，仍可辨。
         */
        val textTertiary = Color(0xFF4E5765)
        val divider = Color(0x1A000000)
    }

    // ---------------- 深色 ----------------
    /**
     * 深色玻璃的关键是"低白色 + 高描边"。
     *
     * 实测教训：初版用 0x33FFFFFF（20% 白）叠在 #080B12 上，
     * 合成结果 ≈ #393939 的中灰，看起来是一块不透明塑料，
     * 完全没有"深色玻璃"该有的通透感（截图为证）。
     * 降到 8% 后背景能透出来，再用 24% 的描边勾出玻璃边缘厚度。
     */
    object Dark {
        val background = Color(0xFF080B12)
        val backgroundElevated = Color(0xFF10151F)

        val glassFill = Color(0x14FFFFFF)
        val glassFillStrong = Color(0x26FFFFFF)
        val glassStroke = Color(0x3DFFFFFF)
        val glassShadow = Color(0x80000000)

        val textPrimary = Color(0xFFF2F5FA)
        // 深色下次要文字必须够亮，否则叠在灰玻璃上直接消失
        val textSecondary = Color(0xFFC6CEDA)
        val textTertiary = Color(0xFF98A2B3)
        val divider = Color(0x26FFFFFF)
    }

    /** 光斑配色：三组互补色，叠在背景上形成柔和的流动感 */
    val meshLight = listOf(
        Color(0xFFBBD1FF),
        Color(0xFFE8D5FF),
        Color(0xFFFFE0C7),
    )

    val meshDark = listOf(
        Color(0xFF1A2A5C),
        Color(0xFF2E1A4A),
        Color(0xFF123A34),
    )

    /** 按涨跌取色，0 附近归为中性的灰 */
    fun changeColor(value: Double, threshold: Double = 1e-9): Color = when {
        value > threshold -> Up
        value < -threshold -> Down
        else -> Flat
    }

    fun assetColor(type: com.huaying.xstz.core.model.AssetType): Color = when (type) {
        com.huaying.xstz.core.model.AssetType.STOCK -> Stock
        com.huaying.xstz.core.model.AssetType.BOND -> Bond
        com.huaying.xstz.core.model.AssetType.COMMODITY -> Commodity
        com.huaying.xstz.core.model.AssetType.CASH -> Cash
    }
}
