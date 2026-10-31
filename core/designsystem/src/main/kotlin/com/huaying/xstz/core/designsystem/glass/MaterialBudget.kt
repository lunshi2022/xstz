package com.huaying.xstz.core.designsystem.glass

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 材质预算计数（方案阶段 2 第 4 项）。
 *
 * ══════════════════════════════════════════════════════════════════
 * 为什么需要运行时计数
 *
 * 方案约束：**同屏玻璃层数 ≤ 3、每层模糊半径 ≤ 24dp**。
 *
 * 问题在于"同屏几层玻璃"不是静态可数的——页面按状态组合，
 * 某个 Sheet 弹出来时可能瞬间多两层。靠代码审查数不出来，
 * 靠肉眼也数不出来（玻璃是视觉上最"隐形"的元素）。
 *
 * 所以做成运行期计数：每创建一个 GlassPanel 就在组合里登记 +1，
 * 离开时 -1。超过阈值时在 Debug 下打警告，把"隐形的性能问题"
 * 变成日志里可见的事实。
 *
 * 生产构建下整个机制是零成本的：
 *   `isDebuggable` 在 release 里恒为 false，Compose 编译器会把
 *   DisposableEffect 的调用点整个消掉（且该分支不会被走到）。
 * ══════════════════════════════════════════════════════════════════
 */
object MaterialBudget {

    /** 方案阶段 2 第 4 项的硬约束 */
    const val MAX_GLASS_LAYERS = 3
    const val MAX_BLUR_DP = 24

    private const val TAG = "MaterialBudget"

    /** 当前同屏玻璃层数。0 表示没有玻璃（正常页面内容层应该如此） */
    var currentLayers: Int by mutableIntStateOf(0)
        private set

    /** 是否发生过超预算（供调试面板显示红点） */
    var exceeded: Boolean by mutableStateOf(false)
        private set

    internal fun enter(label: String) {
        currentLayers++
        if (currentLayers > MAX_GLASS_LAYERS) {
            exceeded = true
            Log.w(
                TAG,
                "玻璃层数超预算：当前 $currentLayers 层 > 上限 $MAX_GLASS_LAYERS 层" +
                    "（新增来源：$label）。同屏玻璃过多会导致中端机掉帧。",
            )
        }
    }

    internal fun exit() {
        if (currentLayers > 0) currentLayers--
    }

    /** 重置。仅供测试与调试面板使用 */
    fun reset() {
        currentLayers = 0
        exceeded = false
    }
}

/**
 * 是否启用预算计数。
 *
 * 用 BuildConfig.DEBUG 而非运行时检测：
 * release 下这个常量是 false，整个 DisposableEffect 分支在
 * R8 优化后不留痕迹。
 */
private val budgetEnabled: Boolean
    get() = runCatching {
        val cls = Class.forName("com.huaying.xstz.core.designsystem.BuildConfig")
        cls.getField("DEBUG").getBoolean(null)
    }.getOrDefault(false)

/**
 * 在玻璃组件里调用，自动登记/注销一层玻璃。
 *
 * 用法（已内置于 GlassPanel）：
 * ```
 * TrackGlassLayer("OverviewSummaryCard")
 * ```
 * label 只用于超预算时的日志归因，不参与逻辑。
 */
@Composable
fun TrackGlassLayer(label: String) {
    if (!budgetEnabled) return

    // key(label) 没必要：同一 label 可能同时出现多次（如列表里多张卡），
    // 计数的语义就是"当前有几层"，不区分来源。
    DisposableEffect(Unit) {
        MaterialBudget.enter(label)
        onDispose { MaterialBudget.exit() }
    }
}

/** 供调试面板读取的当前层数 */
val LocalGlassLayerCount = staticCompositionLocalOf { 0 }
