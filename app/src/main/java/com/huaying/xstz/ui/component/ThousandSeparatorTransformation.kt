package com.huaying.xstz.ui.component

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * 千分符视觉变换
 * 在整数部分每3位插入逗号，小数部分保持不变
 * 正确处理光标位置映射，避免输入时光标跳动
 */
class ThousandSeparatorTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val originalText = text.text
        val formattedText = formatWithSeparators(originalText)
        return TransformedText(
            AnnotatedString(formattedText),
            SeparatorOffsetMapping(originalText, formattedText)
        )
    }

    private fun formatWithSeparators(text: String): String {
        if (text.isEmpty()) return ""
        val dotIndex = text.indexOf('.')
        val intPart = if (dotIndex >= 0) text.substring(0, dotIndex) else text
        val decimalPart = if (dotIndex >= 0) text.substring(dotIndex) else ""
        val formattedIntPart = if (intPart.isNotEmpty()) {
            intPart.reversed().chunked(3).joinToString(",").reversed()
        } else {
            ""
        }
        return formattedIntPart + decimalPart
    }

    private class SeparatorOffsetMapping(
        private val original: String,
        private val transformed: String
    ) : OffsetMapping {
        override fun originalToTransformed(offset: Int): Int {
            if (original.isEmpty()) return 0
            var originalCharsSeen = 0
            var i = 0
            while (i < transformed.length) {
                if (originalCharsSeen == offset) {
                    while (i < transformed.length && transformed[i] == ',') i++
                    return i
                }
                if (transformed[i] != ',') originalCharsSeen++
                i++
            }
            return transformed.length
        }

        override fun transformedToOriginal(offset: Int): Int {
            if (transformed.isEmpty()) return 0
            var originalCharsSeen = 0
            for (i in 0 until minOf(offset, transformed.length)) {
                if (transformed[i] != ',') originalCharsSeen++
            }
            return originalCharsSeen
        }
    }
}
