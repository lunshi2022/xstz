package com.huaying.xstz.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/**
 * 基准配置文件生成器。
 *
 * 在真机（或模拟器）上运行 `./gradlew :benchmark:connectedBenchmarkAndroidTest`
 * 会产出一份优化过的 `baseline-prof.txt`，再手动拷回 `app/src/main/baseline-prof.txt`。
 * 无设备时这一份跑不起来——但它定义了"冷启动该预热哪些类"的脚本，
 * 是生成最优 profile 的唯一正确入口（手写的种子文件只是占位）。
 *
 * 流程：启动应用 → 等首屏可见 → 滑动几下 → 切 Tab，覆盖主要冷启动路径。
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineRule = BaselineProfileRule()

    @Test
    fun startup() = baselineRule.collect(
        packageName = "com.huaying.xstz",
    ) {
        // 冷启动：等待首屏出现。未走完引导时是引导页标题，已配置过则是「概览」标题。
        // BySelector 没有 .or()，用正则匹配二选一文本。
        device.wait(
            Until.hasObject(
                By.text(Pattern.compile("概览|看懂你的每一笔投资")),
            ),
            10_000,
        )

        // 触发一次自动刷新 + 滚动，预热列表与玻璃渲染路径
        device.waitForIdle()
        repeat(3) {
            device.swipe(
                device.displayWidth / 2,
                device.displayHeight * 3 / 4,
                device.displayWidth / 2,
                device.displayHeight / 4,
                10,
            )
        }
        device.waitForIdle()

        // 切换 Tab，覆盖各页的合成路径。
        // 文案必须与 Shell.kt 里 NavTab 的 title 完全一致，否则静默跳过、profile 缺路径。
        listOf("持仓", "走势", "再平衡", "设置").forEach { label ->
            if (device.wait(Until.hasObject(By.text(label)), 5_000)) {
                device.findObject(By.text(label))?.click()
                device.waitForIdle()
            }
        }
    }
}
