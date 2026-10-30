package com.huaying.xstz.core.designsystem.shape

import androidx.compose.foundation.shape.Shape
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * 连续曲率圆角（squircle）。
 *
 * 为什么必须换掉 RoundedCornerShape：
 * 规范圆弧在"直线→圆弧"接缝处曲率从 0 突变为 1/r，视觉上是一个折点；
 * iOS 的连续圆角在该处曲率是渐变的，所以同样的半径看起来更"软"。
 * 这正是"像不像 iOS"最主要的几何差异，实现成本却极低。
 *
 * 实现：每角用超椭圆 |x/a|^n + |y/b|^n = 1 的四分之一弧采样。
 *   n = 2.0  → 退化为正圆（与 RoundedCornerShape 等价）
 *   n = 2.8  → 接近 iOS 连续圆角（默认）
 *   n = 4.0  → 明显偏方，适合图标底板
 *
 * 性能：Path 按量化后的 (宽, 高, 半径, 指数) 做 LRU 缓存，
 * 滚动/动画期间不会逐帧重建。
 */
class SquircleShape(
    private val cornerRadius: Dp = 20.dp,
    /** 超椭圆指数：2.0 圆弧，2.8 iOS 观感，4.0 偏方 */
    private val smoothing: Float = 2.8f,
    /** 每角采样段数：16 已平滑，20 用于大面板 */
    private val segmentsPerCorner: Int = 20
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val radiusPx = with(density) { cornerRadius.toPx() }
            .coerceIn(0f, minOf(size.width, size.height) / 2f)

        if (radiusPx < 0.5f) {
            return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
        }

        return Outline.Generic(
            SquirclePathCache.getOrBuild(
                width = size.width,
                height = size.height,
                radius = radiusPx,
                exponent = smoothing,
                segments = segmentsPerCorner
            )
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SquircleShape) return false
        return cornerRadius == other.cornerRadius &&
            smoothing == other.smoothing &&
            segmentsPerCorner == other.segmentsPerCorner
    }

    override fun hashCode(): Int {
        var result = cornerRadius.hashCode()
        result = 31 * result + smoothing.hashCode()
        result = 31 * result + segmentsPerCorner
        return result
    }
}

/** 胶囊（底部 Tab、按钮）：半径给足即自动收敛为半高，复用同一超椭圆 */
fun squircleCapsule(smoothing: Float = 2.6f): Shape =
    SquircleShape(cornerRadius = 9999.dp, smoothing = smoothing)

internal object SquirclePathCache {

    private const val MAX_ENTRIES = 32

    private data class Key(val w: Int, val h: Int, val r: Int, val n: Int, val s: Int)

    private val cache = object : LinkedHashMap<Key, Path>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Path>?): Boolean =
            size > MAX_ENTRIES
    }

    @Synchronized
    fun getOrBuild(
        width: Float,
        height: Float,
        radius: Float,
        exponent: Float,
        segments: Int
    ): Path {
        // 量化到 0.1px，避免尺寸动画中每帧生成新 key 导致缓存击穿
        val key = Key(
            w = (width * 10).toInt(),
            h = (height * 10).toInt(),
            r = (radius * 10).toInt(),
            n = (exponent * 100).toInt(),
            s = segments
        )
        cache[key]?.let { return it }
        return buildSquirclePath(width, height, radius, exponent, segments).also { cache[key] = it }
    }

    /**
     * 顺时针构造：左上 → 右上 → 右下 → 左下。
     * 超椭圆参数方程（Compose 坐标系 y 轴向下）：
     *   x = cx + r * sgn(cos t) * |cos t|^(2/n)
     *   y = cy + r * sgn(sin t) * |sin t|^(2/n)
     */
    private fun buildSquirclePath(
        w: Float,
        h: Float,
        r: Float,
        n: Float,
        segments: Int
    ): Path {
        val path = Path()
        val p = 2f / n
        var started = false

        fun emit(x: Float, y: Float) {
            if (!started) {
                path.moveTo(x, y)
                started = true
            } else {
                path.lineTo(x, y)
            }
        }

        // 单角：以 (cx, cy) 为圆心，t 从 startDeg 扫到 endDeg（数学角，y 向下已适配）
        fun corner(cx: Float, cy: Float, startDeg: Double, endDeg: Double) {
            for (i in 0..segments) {
                val t = Math.toRadians(startDeg + (endDeg - startDeg) * i.toDouble() / segments)
                val ct = cos(t)
                val st = sin(t)
                val x = cx + r * (if (ct >= 0) 1f else -1f) * abs(ct).toFloat().pow(p)
                val y = cy + r * (if (st >= 0) 1f else -1f) * abs(st).toFloat().pow(p)
                emit(x, y)
            }
        }

        // 左上角：中心 (r, r)，从 (0, r) 即 180° 扫到 (r, 0) 即 270°
        corner(r, r, 180.0, 270.0)
        // 上边线
        emit(w - r, 0f)
        // 右上角：中心 (w-r, r)，270° → 360°
        corner(w - r, r, 270.0, 360.0)
        // 右边线
        emit(w, h - r)
        // 右下角：中心 (w-r, h-r)，0° → 90°
        corner(w - r, h - r, 0.0, 90.0)
        // 下边线
        emit(r, h)
        // 左下角：中心 (r, h-r)，90° → 180°
        corner(r, h - r, 90.0, 180.0)

        path.close()
        return path
    }
}
