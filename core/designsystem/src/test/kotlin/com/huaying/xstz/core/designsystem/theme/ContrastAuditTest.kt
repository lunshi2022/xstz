package com.huaying.xstz.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG 2.1 对比度审计（方案阶段 4 验收项：对比度报告全绿）。
 *
 * 审计模型：文字永远叠在「玻璃合成色」上——玻璃是不透明白/黑以 alpha 叠在
 * 背景色上的结果。所以底色 = glassFill 以其 alpha 合成到 background 上。
 * 这是保守估计：真实模糊采样只会让底色更接近背景，不会更极端。
 *
 * WCAG AA 要求：
 *   * 正文文字（< 18pt）≥ 4.5:1
 *   * 大字号（≥ 18pt 或 14pt 加粗）≥ 3:1
 *
 * 涨跌色（Up/Down）多用于大号数字（numericL/numericXL 属大字号），按 3:1 审计；
 * 但 A 股"红涨绿跌"是语义色，无法随主题换色，这里只审计其在实色卡上的下限。
 */
class ContrastAuditTest {

    // ---------------- WCAG 公式（直接实现规范，避免引入依赖） ----------------

    /** sRGB 通道线性化：c <= 0.03928 ? c/12.92 : ((c+0.055)/1.055)^2.4 */
    private fun linear(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(c: Color): Double =
        0.2126 * linear(c.red) + 0.7152 * linear(c.green) + 0.0722 * linear(c.blue)

    /** WCAG 对比度：(L1 + 0.05) / (L2 + 0.05)，L1 取亮者 */
    private fun contrast(fg: Color, bg: Color): Double {
        val l1 = maxOf(luminance(fg), luminance(bg))
        val l2 = minOf(luminance(fg), luminance(bg))
        return (l1 + 0.05) / (l2 + 0.05)
    }

    /** 把半透明玻璃色合成到背景上，得到文字实际所处的底色 */
    private fun glassSurface(glass: Color, background: Color): Color =
        glass.compositeOver(background)

    private fun lightSurfaces(): List<String> = listOf(
        "glassFill-on-bg",
        "glassFillStrong-on-bg",
        "backgroundElevated",
    )

    private fun darkSurfaces(): List<String> = lightSurfaces()

    // ---------------- 浅色主题 ----------------

    @Test
    fun `light - primary and secondary text meet 4_5 on all surfaces`() {
        val bg = XstzColors.Light.background
        val surfaces = listOf(
            glassSurface(XstzColors.Light.glassFill, bg),
            glassSurface(XstzColors.Light.glassFillStrong, bg),
            XstzColors.Light.backgroundElevated,
        )
        val texts = mapOf(
            "textPrimary" to XstzColors.Light.textPrimary,
            "textSecondary" to XstzColors.Light.textSecondary,
        )
        surfaces.forEachIndexed { i, surface ->
            texts.forEach { (name, color) ->
                val ratio = contrast(color, surface)
                assertTrue(
                    "light/$name vs ${lightSurfaces()[i]}: $ratio < 4.5",
                    ratio >= 4.5,
                )
            }
        }
    }

    @Test
    fun `light - tertiary text meets 4_5 on all surfaces`() {
        val bg = XstzColors.Light.background
        val surfaces = listOf(
            glassSurface(XstzColors.Light.glassFill, bg),
            glassSurface(XstzColors.Light.glassFillStrong, bg),
            XstzColors.Light.backgroundElevated,
        )
        surfaces.forEachIndexed { i, surface ->
            val ratio = contrast(XstzColors.Light.textTertiary, surface)
            assertTrue(
                "light/textTertiary vs ${lightSurfaces()[i]}: $ratio < 4.5",
                ratio >= 4.5,
            )
        }
    }

    // ---------------- 深色主题 ----------------

    @Test
    fun `dark - primary and secondary text meet 4_5 on all surfaces`() {
        val bg = XstzColors.Dark.background
        val surfaces = listOf(
            glassSurface(XstzColors.Dark.glassFill, bg),
            glassSurface(XstzColors.Dark.glassFillStrong, bg),
            XstzColors.Dark.backgroundElevated,
        )
        val texts = mapOf(
            "textPrimary" to XstzColors.Dark.textPrimary,
            "textSecondary" to XstzColors.Dark.textSecondary,
        )
        surfaces.forEachIndexed { i, surface ->
            texts.forEach { (name, color) ->
                val ratio = contrast(color, surface)
                assertTrue(
                    "dark/$name vs ${darkSurfaces()[i]}: $ratio < 4.5",
                    ratio >= 4.5,
                )
            }
        }
    }

    @Test
    fun `dark - tertiary text meets 4_5 on all surfaces`() {
        val bg = XstzColors.Dark.background
        val surfaces = listOf(
            glassSurface(XstzColors.Dark.glassFill, bg),
            glassSurface(XstzColors.Dark.glassFillStrong, bg),
            XstzColors.Dark.backgroundElevated,
        )
        surfaces.forEachIndexed { i, surface ->
            val ratio = contrast(XstzColors.Dark.textTertiary, surface)
            assertTrue(
                "dark/textTertiary vs ${darkSurfaces()[i]}: $ratio < 4.5",
                ratio >= 4.5,
            )
        }
    }

    // ---------------- 涨跌语义色（大字号数字，AA 大字 3:1） ----------------

    @Test
    fun `change colors meet 3_1 for large text on solid cards`() {
        // 涨跌色只出现在实色卡（SolidCard）上，按大字号 3:1 审计。
        // 注意 palette.flat 实际映射为各主题的 textTertiary（见 LightPalette/DarkPalette），
        // 所以这里审计真实使用的值，而非 XstzColors.Flat 原值。
        val light = XstzColors.Light.backgroundElevated to XstzColors.Light.textTertiary
        val dark = XstzColors.Dark.backgroundElevated to XstzColors.Dark.textTertiary
        for ((surface, flat) in listOf(light, dark)) {
            for (color in listOf(XstzColors.Up, XstzColors.Down, flat)) {
                val ratio = contrast(color, surface)
                assertTrue(
                    "change color (large text) on backgroundElevated: $ratio < 3.0",
                    ratio >= 3.0,
                )
            }
        }
    }

    // ---------------- 品牌色（按钮文字用白，需 4.5:1） ----------------

    @Test
    fun `accent color as background supports white text at 4_5`() {
        // GlassButton 的 accent 变体用白色文字，底是 Accent
        val ratio = contrast(Color.White, XstzColors.Accent)
        assertTrue(
            "white on Accent: $ratio < 4.5",
            ratio >= 4.5,
        )
    }

    // ---------------- 高对比度变体（开启系统「高对比度文字」时） ----------------

    @Test
    fun `high-contrast palette variant still meets 4_5 on all surfaces`() {
        // 开启系统高对比度后，tertiary/flat 会被替换成更极端的 secondary（浅色）或
        // primary（深色）。这条用例锁定：替换后最弱一档文字仍满足 AA，不会反向劣化。
        for (palette in listOf(LightPalette, DarkPalette)) {
            val hc = palette.withHighContrast()
            val bg = if (palette.isDark) XstzColors.Dark.background else XstzColors.Light.background
            val surfaces = listOf(
                glassSurface(if (palette.isDark) XstzColors.Dark.glassFill else XstzColors.Light.glassFill, bg),
                glassSurface(if (palette.isDark) XstzColors.Dark.glassFillStrong else XstzColors.Light.glassFillStrong, bg),
                if (palette.isDark) XstzColors.Dark.backgroundElevated else XstzColors.Light.backgroundElevated,
            )
            surfaces.forEach { surface ->
                val ratio = contrast(hc.textTertiary, surface)
                assertTrue(
                    "${if (palette.isDark) "dark" else "light"} high-contrast/tertiary vs surface: $ratio < 4.5",
                    ratio >= 4.5,
                )
            }
        }
    }
}
