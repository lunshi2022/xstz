package com.huaying.xstz.ui.charts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import com.huaying.xstz.R
import com.huaying.xstz.data.model.DailyAssetData
import java.time.format.DateTimeFormatter

class CustomMarkerView(context: Context, layoutResource: Int) : MarkerView(context, layoutResource) {

    private val tvContent: TextView = findViewById(R.id.tvContent)
    private var dataList: List<DailyAssetData>? = null
    private var type: String = "value"
    private val handler = Handler(Looper.getMainLooper())
    private var hideRunnable: Runnable? = null

    // 使用 View 的生命周期来清理 Handler 回调，避免内存泄漏
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        hideRunnable?.let { handler.removeCallbacks(it) }
        hideRunnable = null
    }

    fun setData(data: List<DailyAssetData>) {
        this.dataList = data
    }

    fun setChartType(type: String) {
        this.type = type
    }

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        if (e == null) return

        try {
            val index = e.x.toInt()
            if (dataList != null && index >= 0 && index < dataList!!.size) {
                val item = dataList!![index]
                val dateStr = item.date.format(DATE_FORMATTER)

                val valueText = when (type) {
                    "return" -> "收益率: %.2f%%".format(e.y)
                    "percent" -> {
                        val total = item.totalAsset
                        if (total > 0) {
                            val s = item.stockValue / total * 100
                            val b = item.bondValue / total * 100
                            val g = item.goldValue / total * 100
                            val c = item.cashValue / total * 100
                            buildString {
                                append("股票: %.1f%%".format(s))
                                append("\n债券: %.1f%%".format(b))
                                append("\n商品: %.1f%%".format(g))
                                append("\n现金: %.1f%%".format(c))
                            }
                        } else {
                            "无资产"
                        }
                    }
                    "comparison" -> {
                        val total = item.totalAsset
                        val principal = item.principal
                        val profit = total - principal
                        val profitRate = if (principal > 0) profit / principal * 100 else 0.0

                        buildString {
                            append("总资产: %.2f".format(total))
                            if (principal > 0) {
                                append("\n投入本金: %.2f".format(principal))
                                append("\n浮动盈亏: %+.2f (%+.2f%%)".format(profit, profitRate))
                            }
                        }
                    }
                    else -> "数值: %.2f".format(e.y)
                }

                tvContent.text = "$dateStr\n$valueText"
            } else {
                tvContent.text = "%.2f".format(e.y)
            }
        } catch (ex: Exception) {
            tvContent.text = "%.2f".format(e.y)
        }

        scheduleAutoHide()

        super.refreshContent(e, highlight)
    }

    // 重置自动隐藏计时器
    fun resetAutoHideTimer() {
        scheduleAutoHide()
    }

    private fun scheduleAutoHide() {
        hideRunnable?.let { handler.removeCallbacks(it) }

        hideRunnable = Runnable {
            if (chartView != null) {
                chartView.highlightValue(null)
            }
        }
        handler.postDelayed(hideRunnable!!, AUTO_HIDE_DELAY_MS)
    }

    override fun getOffset(): MPPointF {
        return MPPointF(-(width / 2f), -height.toFloat() - 10f)
    }

    companion object {
        private const val AUTO_HIDE_DELAY_MS = 2000L
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }
}
