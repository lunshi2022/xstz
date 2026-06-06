package com.huaying.xstz.ui.assetoverview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.huaying.xstz.ui.theme.BrandBlue
import java.text.DecimalFormat

@Composable
fun TrendChartSection(
    viewModel: AssetOverviewViewModel,
    isDarkMode: Boolean
) {
    val selectedTimeRange by viewModel.selectedTimeRange.collectAsState()
    val selectedDataType by viewModel.selectedChartDataType.collectAsState()
    val trendChartData by viewModel.trendChartData.collectAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "累计${selectedDataType.displayName}趋势",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            TimeRangeSelector(
                selectedRange = selectedTimeRange,
                onRangeSelected = { viewModel.selectTimeRange(it) },
                isDarkMode = isDarkMode
            )

            ChartTypeSelector(
                selectedType = selectedDataType,
                onTypeSelected = { viewModel.selectChartDataType(it) },
                isDarkMode = isDarkMode
            )

            Spacer(modifier = Modifier.height(8.dp))

            TrendLineChart(
                chartData = trendChartData,
                dataType = selectedDataType,
                isDarkMode = isDarkMode
            )
        }
    }
}

@Composable
fun TimeRangeSelector(
    selectedRange: TimeRange,
    onRangeSelected: (TimeRange) -> Unit,
    isDarkMode: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        TimeRange.values().forEach { range ->
            val isSelected = range == selectedRange
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isSelected) BrandBlue else Color.Transparent
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onRangeSelected(range) }
                    )
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = range.displayName,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun ChartTypeSelector(
    selectedType: ChartDataType,
    onTypeSelected: (ChartDataType) -> Unit,
    isDarkMode: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ChartDataType.values().forEach { type ->
            val isSelected = type == selectedType
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isSelected) BrandBlue else Color.Transparent
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onTypeSelected(type) }
                    )
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = type.displayName,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun TrendLineChart(
    chartData: TrendChartData?,
    dataType: ChartDataType,
    isDarkMode: Boolean
) {
    val lineColor = BrandBlue
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f).toArgb()

    AndroidView(
        factory = { context ->
            LineChart(context).apply {
                setupChart(this, lineColor, textColor, gridColor)
            }
        },
        update = { chart ->
            chartData?.let { data ->
                updateChartData(chart, data, lineColor)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
    )
}

private fun setupChart(chart: LineChart, lineColor: Color, textColor: Int, gridColor: Int) {
    chart.apply {
        setTouchEnabled(true)
        isDragEnabled = true
        setScaleEnabled(true)
        setPinchZoom(true)
        setDrawGridBackground(false)

        description.isEnabled = false
        legend.isEnabled = false

        xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            this.textColor = textColor
            textSize = 11f
            granularity = 1f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return ""
                }
            }
        }

        axisLeft.apply {
            setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART)
            setDrawGridLines(true)
            this.gridColor = gridColor
            this.textColor = textColor
            textSize = 10f
            valueFormatter = object : ValueFormatter() {
                private val decimalFormat = DecimalFormat("0.0%")
                override fun getFormattedValue(value: Float): String {
                    return "${decimalFormat.format(value / 100)}"
                }
            }
        }

        axisRight.isEnabled = false
    }
}

private fun updateChartData(
    chart: LineChart,
    data: TrendChartData,
    lineColor: Color
) {
    if (data.dataPoints.isEmpty()) {
        chart.data = null
        chart.invalidate()
        return
    }

    val entries = data.dataPoints.mapIndexed { index, point ->
        Entry(index.toFloat(), point.value.toFloat())
    }

    val dataSet = LineDataSet(entries, "").apply {
        color = lineColor.toArgb()
        setCircleColor(lineColor.toArgb())
        circleRadius = 3f
        circleHoleRadius = 1.5f
        lineWidth = 2f
        setDrawValues(false)
        mode = LineDataSet.Mode.CUBIC_BEZIER
        setDrawFilled(true)
        fillAlpha = 25
        setFillColor(lineColor.copy(alpha = 0.1f).toArgb())
    }

    val lineData = LineData(dataSet)
    chart.data = lineData

    chart.xAxis.valueFormatter = object : ValueFormatter() {
        override fun getFormattedValue(value: Float): String {
            val index = value.toInt()
            return if (index >= 0 && index < data.dataPoints.size) {
                data.dataPoints[index].label
            } else {
                ""
            }
        }
    }

    chart.notifyDataSetChanged()
    chart.invalidate()

    if (data.dataPoints.size > 1) {
        chart.setVisibleXRangeMaximum(data.dataPoints.size.toFloat())
        chart.moveViewToX(data.dataPoints.lastIndex.toFloat())
    }
}
