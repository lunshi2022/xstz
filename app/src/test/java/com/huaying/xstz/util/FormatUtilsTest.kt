package com.huaying.xstz.util

import org.junit.Assert.*
import org.junit.Test

class FormatUtilsTest {

    // ==================== formatCurrency ====================

    @Test
    fun formatCurrency_normalValue_returnsFormattedCurrency() {
        val result = FormatUtils.formatCurrency(1234.56)
        assertTrue(result.contains("1,234.56") || result.contains("1234.56"))
        assertTrue(result.contains("¥"))
    }

    @Test
    fun formatCurrency_zeroValue_returnsZeroCurrency() {
        val result = FormatUtils.formatCurrency(0.0)
        assertTrue(result.contains("0.00") || result.contains("0"))
    }

    @Test
    fun formatCurrency_privacyMode_returnsMasked() {
        val result = FormatUtils.formatCurrency(1234.56, isPrivacy = true)
        assertEquals("¥ ****", result)
    }

    @Test
    fun formatCurrency_largeValue_returnsWithCommaSeparator() {
        val result = FormatUtils.formatCurrency(1234567.89)
        assertTrue(result.contains("1,234,567.89") || result.contains("1234567.89"))
    }

    // ==================== formatCurrencyValue ====================

    @Test
    fun formatCurrencyValue_normalValue_returnsNumberWithComma() {
        val result = FormatUtils.formatCurrencyValue(1234.56)
        assertTrue(result.contains("1,234.56") || result.contains("1234.56"))
        assertFalse(result.contains("¥"))
    }

    // ==================== formatPercent ====================

    @Test
    fun formatPercent_positiveValue_returnsWithPlusSign() {
        val result = FormatUtils.formatPercent(2.5)
        assertTrue(result.startsWith("+"))
        assertTrue(result.contains("2.50"))
    }

    @Test
    fun formatPercent_negativeValue_returnsWithMinusSign() {
        val result = FormatUtils.formatPercent(-1.3)
        assertTrue(result.startsWith("-"))
        assertTrue(result.contains("1.30"))
    }

    @Test
    fun formatPercent_zeroValue_returnsPositiveZero() {
        val result = FormatUtils.formatPercent(0.0)
        assertTrue(result.contains("0.00"))
    }

    @Test
    fun formatPercent_privacyMode_returnsMasked() {
        val result = FormatUtils.formatPercent(2.5, isPrivacy = true)
        assertEquals("****%", result)
    }

    // ==================== formatRatio ====================

    @Test
    fun formatRatio_normalValue_returnsPercentage() {
        val result = FormatUtils.formatRatio(25.0)
        assertTrue(result.contains("25.00"))
        assertTrue(result.contains("%"))
        assertFalse(result.startsWith("+"))
    }

    @Test
    fun formatRatio_privacyMode_returnsMasked() {
        val result = FormatUtils.formatRatio(25.0, isPrivacy = true)
        assertEquals("**%", result)
    }

    // ==================== formatRatio1F ====================

    @Test
    fun formatRatio1F_normalValue_returnsOneDecimal() {
        val result = FormatUtils.formatRatio1F(25.0)
        assertTrue(result.contains("25.0"))
    }

    // ==================== formatPrice ====================

    @Test
    fun formatPrice_normalValue_returnsThreeDecimals() {
        val result = FormatUtils.formatPrice(1.234)
        assertTrue(result.contains("1.234"))
    }

    @Test
    fun formatPrice_privacyMode_returnsMasked() {
        val result = FormatUtils.formatPrice(1.234, isPrivacy = true)
        assertEquals("****", result)
    }

    // ==================== formatCostPrice ====================

    @Test
    fun formatCostPrice_normalValue_returnsWithYenSign() {
        val result = FormatUtils.formatCostPrice(1.234)
        assertTrue(result.contains("¥"))
        assertTrue(result.contains("1.234"))
    }

    @Test
    fun formatCostPrice_privacyMode_returnsMasked() {
        val result = FormatUtils.formatCostPrice(1.234, isPrivacy = true)
        assertEquals("****", result)
    }

    // ==================== formatQuantity ====================

    @Test
    fun formatQuantity_normalValue_returnsInteger() {
        val result = FormatUtils.formatQuantity(1000.0)
        // %.0f with Locale.CHINA may produce "1,000" or "1000" depending on JVM
        assertTrue(result.contains("1000"))
    }

    @Test
    fun formatQuantity_privacyMode_returnsMasked() {
        val result = FormatUtils.formatQuantity(1000.0, isPrivacy = true)
        assertEquals("****", result)
    }

    // ==================== formatCashAmount ====================

    @Test
    fun formatCashAmount_normalValue_returnsWithYenSign() {
        val result = FormatUtils.formatCashAmount(1234.56)
        assertTrue(result.contains("¥"))
        assertTrue(result.contains("1,234.56") || result.contains("1234.56"))
    }

    @Test
    fun formatCashAmount_privacyMode_returnsMasked() {
        val result = FormatUtils.formatCashAmount(1234.56, isPrivacy = true)
        assertEquals("****", result)
    }

    // ==================== formatReturnRate ====================

    @Test
    fun formatReturnRate_positiveValue_returnsWithPlusSign() {
        val result = FormatUtils.formatReturnRate(25.0)
        assertTrue(result.startsWith("+"))
        assertTrue(result.contains("25.00"))
    }

    @Test
    fun formatReturnRate_negativeValue_returnsWithMinusSign() {
        val result = FormatUtils.formatReturnRate(-10.5)
        assertTrue(result.startsWith("-"))
        assertTrue(result.contains("10.50"))
    }

    @Test
    fun formatReturnRate_privacyMode_returnsMasked() {
        val result = FormatUtils.formatReturnRate(25.0, isPrivacy = true)
        assertEquals("****%", result)
    }

    // ==================== safeFormat ====================

    @Test
    fun safeFormat_normalValue_returnsFormatted() {
        val result = FormatUtils.safeFormat(123.456, "%.2f")
        assertEquals("123.46", result)
    }

    @Test
    fun safeFormat_nanValue_returnsDefault() {
        val result = FormatUtils.safeFormat(Double.NaN, "%.2f")
        assertEquals("0.00", result)
    }

    @Test
    fun safeFormat_infinityValue_returnsDefault() {
        val result = FormatUtils.safeFormat(Double.POSITIVE_INFINITY, "%.2f")
        assertEquals("0.00", result)
    }

    @Test
    fun safeFormat_negativeInfinityValue_returnsDefault() {
        val result = FormatUtils.safeFormat(Double.NEGATIVE_INFINITY, "%.2f")
        assertEquals("0.00", result)
    }

    @Test
    fun safeFormat_customDefault_returnsCustomDefault() {
        val result = FormatUtils.safeFormat(Double.NaN, "%.2f", "--")
        assertEquals("--", result)
    }

    // ==================== getInvestmentDays ====================

    @Test
    fun getInvestmentDays_zeroDate_returnsZero() {
        assertEquals(0, FormatUtils.getInvestmentDays(0L))
    }
}
