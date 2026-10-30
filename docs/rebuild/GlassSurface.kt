// ⚠️ 本文件基于 Haze 1.x 的 API 书写，在实测的 Haze 2.0.1 下【不能直接编译】。
// 已知差异（2026-10-30 实测，见 VERIFICATION.md §3.4/3.5）：
//   1. 制品拆分：haze-materials 已不存在 → haze + haze-blur + haze-blur-materials
//   2. Modifier.hazeEffect(state, style) 已废弃 → Modifier.hazeBlur(input = HazeInput.Backdrop(state), style = ...)
//   3. 材质直接复用 CupertinoMaterials.ultraThin/thin/regular/thick —— 即 Apple 的四档材质，
//      不需要（也不应该）自行调参近似，本文件里自造的参数表应整体替换为 CupertinoMaterials。
// 保留本文件的价值：四档降级策略、AGSL 折射探针、同屏玻璃层数预算这三块设计仍然成立，
// 改造时只替换"渲染层"与"材质参数"两部分。
package com.huaying.xstz.core.designsystem.glass

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huaying.xstz.core.designsystem.shape.SquircleShape
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/**
 * ============================================================================
 * Liquid Glass 材质系统（Android 侧实现）
 * ============================================================================
 *
 * 设计约束（来自 Apple HIG 的硬规则，违反即"廉价玻璃拟态"）：
 *   1. 玻璃只用于**导航层**（顶栏 / 底栏 / Tab / 悬浮操作 / Sheet / Dialog），
 *      **不用于内容层**（列表项、表单、图表容器）。
 *   2. 玻璃不叠玻璃。玻璃上的按钮用实色填充或描边。
 *   3. 同屏主玻璃面 ≤ 3 个，超出即降级为半透明实色。
 *
 * ----------------------------------------------------------------------------
 * ⚠️ 落地前必读：Haze API 版本差异
 * ----------------------------------------------------------------------------
 * 本文件按 Haze 2.0.x 的 `hazeSource` / `hazeEffect` 双端 API 编写。
 * 若锁定版本后 IDE 报签名不匹配，按以下顺序处理（不要改架构，只改调用）：
 *   - 1.7.x 及更早：用 `Modifier.haze(state)` 标源、`Modifier.hazeChild(state, style)` 标玻璃
 *   - 2.0.x：用 `Modifier.hazeSource(state)` / `Modifier.hazeEffect(state) { ... }`
 *   - 若命名再次变更，以 IDE 自动补全为准，本例的 tier 判定、令牌、降级逻辑可原样复用
 *
 * ----------------------------------------------------------------------------
 * ⚠️ 落地前必读：AGSL 折射的两条红线
 * ----------------------------------------------------------------------------
 *   1. RuntimeShader 编译在部分 OEM ROM 上会失败（返回黑块 / 抛异常），
 *      必须走 [GlassCapability] 探针，失败即降 L2。
 *   2. 折射**只做视觉装饰**，绝不能承载任何信息（不能靠折射区分层级/状态），
 *      否则在 L1 设备上信息就丢了。
 */

// ============================================================================
// 1. 能力档位
// ============================================================================

/**
 * 四档降级。运行时由 [GlassCapability.resolve] 决定，不由业务代码硬编码。
 */
enum class GlassTier {
    /** API ≥33 且 shader 可用且非低内存设备：模糊 + 折射 + 动态高光 */
    FULL,

    /** API ≥32 或 shader 不可用：模糊 + 静态高光 + 染色 */
    BLUR,

    /** API 29~31：无模糊，半透明遮罩 + 染色 + 细描边 */
    TINT,

    /** Reduce Transparency / 低电量 / 探针失败：完全不透明 */
    OPAQUE
}

/**
 * 能力探针。必须在应用启动时执行一次（结果缓存），不要在每次重组里跑。
 *
 * 用法（Application.onCreate 或首帧前）：
 * ```
 * GlassCapability.probe(context)
 * ```
 */
object GlassCapability {

    @Volatile
    private var resolved: GlassTier? = null

    fun current(): GlassTier = resolved ?: GlassTier.TINT

    /**
     * @param lowRam 传 ActivityManager.isLowRamDevice，低内存设备直接降到 BLUR
     * @param reduceTransparency 系统无障碍"移除动画/降低透明度"开关（Android 无同名开关，
     *        需自行在设置页提供给用户，并读取 SharedPreferences）
     */
    fun probe(lowRam: Boolean, reduceTransparency: Boolean): GlassTier {
        if (resolved != null) return resolved!!

        val tier = when {
            reduceTransparency -> GlassTier.OPAQUE
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shaderCompiles() && !lowRam ->
                GlassTier.FULL
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2 && !lowRam -> GlassTier.BLUR
            else -> GlassTier.TINT
        }
        resolved = tier
        return tier
    }

    /** 用一段最小 AGSL 试编译，捕获 OEM 驱动异常 */
    private fun shaderCompiles(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return try {
            RuntimeShader(MINIMAL_AGSL)
            true
        } catch (t: Throwable) {
            false
        }
    }

    private const val MINIMAL_AGSL = """
        half4 main(float2 fragCoord) {
            return half4(1.0);
        }
    """
}

// ============================================================================
// 2. 设计令牌
// ============================================================================

@Immutable
data class GlassTokens(
    val blurRadius: Dp,
    val tint: Color,
    val borderColor: Color,
    val borderWidth: Dp,
    val refractionScale: Float,
    val specularAlpha: Float,
    val noiseFactor: Float,
    val shadowAmbient: Dp,
    val shadowSpot: Dp
)

/** 浅色主题：白玻璃，靠阴影与描边立起来 */
val LightGlassTokens = GlassTokens(
    blurRadius = 24.dp,
    tint = Color.White.copy(alpha = 0.55f),
    borderColor = Color.White.copy(alpha = 0.55f),
    borderWidth = 0.5.dp,
    refractionScale = 0.035f,
    specularAlpha = 0.22f,
    noiseFactor = 0.04f,
    shadowAmbient = 8.dp,
    shadowSpot = 16.dp
)

/** 深色主题：黑玻璃，靠染色与高光边缘立起来 */
val DarkGlassTokens = GlassTokens(
    blurRadius = 24.dp,
    tint = Color(0xFF15161A).copy(alpha = 0.62f),
    borderColor = Color.White.copy(alpha = 0.16f),
    borderWidth = 0.5.dp,
    refractionScale = 0.035f,
    specularAlpha = 0.14f,
    noiseFactor = 0.05f,
    shadowAmbient = 10.dp,
    shadowSpot = 20.dp
)

/** 按档位裁剪令牌：低档位不给模糊与折射 */
fun GlassTokens.forTier(tier: GlassTier): GlassTokens = when (tier) {
    GlassTier.FULL -> this
    GlassTier.BLUR -> copy(
        blurRadius = blurRadius * 0.75f,
        refractionScale = 0f,
        specularAlpha = specularAlpha * 0.45f
    )
    GlassTier.TINT -> copy(
        blurRadius = 0.dp,
        tint = tint.copy(alpha = (tint.alpha + 0.25f).coerceAtMost(0.9f)),
        refractionScale = 0f,
        specularAlpha = 0f
    )
    GlassTier.OPAQUE -> copy(
        blurRadius = 0.dp,
        tint = if (tint.luminanceApprox() > 0.5f) Color(0xFFF7F7FA) else Color(0xFF15161A),
        refractionScale = 0f,
        specularAlpha = 0f,
        shadowAmbient = 2.dp,
        shadowSpot = 4.dp
    )
}

private fun Color.luminanceApprox(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

val LocalGlassTokens = compositionLocalOf { LightGlassTokens }
val LocalGlassTier = compositionLocalOf { GlassTier.TINT }

// ============================================================================
// 3. 玻璃容器
// ============================================================================

/**
 * 玻璃面。
 *
 * @param hazeState 由**背景内容**通过 [Modifier.glassBackdrop] 标记，
 *        玻璃面从它采样。同一屏幕共用一个 HazeState。
 * @param shape 默认连续曲率圆角（见 SquircleShape），**不要传 RoundedCornerShape**
 * @param interactive 是否响应按压（按压时高光增强）
 */
@Composable
fun GlassSurface(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    shape: Shape = SquircleShape(20.dp),
    tier: GlassTier = LocalGlassTier.current,
    tokens: GlassTokens = LocalGlassTokens.current,
    interactive: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val t = remember(tokens, tier) { tokens.forTier(tier) }

    val base = modifier
        .clip(shape)
        .then(
            if (tier == GlassTier.FULL || tier == GlassTier.BLUR) {
                Modifier.hazeEffect(hazeState) {
                    // ⚠️ 若 Haze 版本 API 不同，改这里的属性名即可，其余逻辑不变
                    blurRadius = t.blurRadius
                    noiseFactor = t.noiseFactor
                }
            } else {
                Modifier
            }
        )
        // 染色层：这是"能看清文字"的保障，不是可选项
        .background(t.tint, shape)
        // 边缘高光线：iOS 玻璃的灵魂，0.5dp 白色描边
        .border(t.borderWidth, t.borderColor, shape)
        // 镜面高光：左上向的斜向渐变，模拟单一光源
        .glassSpecular(t.specularAlpha)

    Box(modifier = base) {
        content()
    }
}

/**
 * 镜面高光。iOS 的玻璃左上边缘更亮、右下更暗，模拟光从左上打来。
 * L1 档位下为 0（由 forTier 清空），不产生额外绘制成本。
 */
private fun Modifier.glassSpecular(alpha: Float): Modifier {
    if (alpha <= 0f) return this
    return this.drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0.0f to Color.White.copy(alpha = alpha),
                    0.35f to Color.White.copy(alpha = alpha * 0.25f),
                    1.0f to Color.Transparent
                ),
                start = Offset(0f, 0f),
                end = Offset(size.width * 0.7f, size.height * 0.7f)
            )
        )
    }
}

/**
 * 把**背景内容**标记为采样源。必须包住滚动列表/图表等会被玻璃覆盖的内容。
 *
 * 正确用法：
 * ```
 * val hazeState = rememberHazeState()
 * Box {
 *     LazyColumn(Modifier.glassBackdrop(hazeState)) { ... }   // 背景
 *     GlassTabBar(hazeState, Modifier.align(BottomCenter))    // 前景玻璃
 * }
 * ```
 *
 * 错误用法（会导致模糊区域错位或全屏重绘）：
 *   - 把 hazeSource 打在根 Box 上，同时玻璃也在根 Box 内（自我采样，模糊出黑块）
 *   - 每个列表项单独打（应打在 LazyColumn 容器上）
 */
fun Modifier.glassBackdrop(hazeState: HazeState): Modifier = this.hazeSource(hazeState)

/**
 * 全屏玻璃背景（用于 Dialog / Sheet 的遮罩淡入）。
 * 只做染色，不做模糊——模糊由上层 GlassSurface 负责，避免两层模糊叠加导致模糊半径翻倍。
 */
@Composable
fun GlassScrim(alpha: Float = 0.28f, color: Color = Color.Black) {
    Box(
        Modifier
            .fillMaxSize()
            .background(color.copy(alpha = alpha))
    )
}
