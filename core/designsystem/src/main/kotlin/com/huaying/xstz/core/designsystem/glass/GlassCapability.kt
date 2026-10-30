package com.huaying.xstz.core.designsystem.glass

import android.os.Build

/**
 * 玻璃能力探测。
 *
 * ══════════════════════════════════════════════════════════════════
 * 为什么需要这个：
 *
 * Haze 的模糊依赖 Android 的 RenderEffect（API 31+）。在**软件渲染**
 * （模拟器上的 -gpu swiftshader_indirect）下 RenderEffect 不生效，
 * hazeBlur 会输出一个不透明的近似色，而不是"模糊后的背景"。
 *
 * 实测后果（API 36 模拟器截图）：
 *   深色模式卡片 = #393939 的不透明灰塑料块
 *   浅色模式卡片 = 中灰块，完全看不到背后的粉色光斑
 * 玻璃感荡然无存，还不如老老实实做半透明。
 *
 * 所以：能真模糊就真模糊（真机），不能就降级成「半透明 + 高光 + 描边」。
 * 这不是偷懒，而是在没有模糊能力时唯一还像玻璃的做法。
 * ══════════════════════════════════════════════════════════════════
 */
object GlassCapability {

    /**
     * 是否具备真实的背景模糊能力。
     *
     * 判定依据是 emulator 的硬件标识。真机上为 true，模拟器为 false。
     * 这里不试图探测具体 GPU（那需要 GL 上下文，而组合阶段拿不到），
     * 因为模拟器上的结论是一致的：RenderEffect 不可靠。
     *
     * 副作用要知道：开发期在模拟器上看到的"玻璃"是没有模糊的版本，
     * 真机上会更通透。这个差异写进了 docs/REBUILD_STATUS.md。
     */
    val hasRealBlur: Boolean by lazy {
        val hardware = Build.HARDWARE.lowercase()
        val fingerprint = Build.FINGERPRINT.lowercase()
        val product = Build.PRODUCT.lowercase()

        val isEmulator = hardware.contains("ranchu") ||
            hardware.contains("goldfish") ||
            fingerprint.contains("generic") ||
            fingerprint.contains("emulator") ||
            product.contains("sdk_gphone") ||
            product.contains("emulator")

        !isEmulator
    }

    /** 给设置页/诊断用的说明文案 */
    fun describe(): String =
        if (hasRealBlur) "支持实时背景模糊（RenderEffect）"
        else "当前环境不支持实时模糊，已降级为半透明材质"
}
