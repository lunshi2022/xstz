package com.huaying.xstz.core.domain

import kotlinx.coroutines.flow.StateFlow

/**
 * 应用前后台状态。
 *
 * 自动刷新需要区分"用户在看"（前台，按 3s/5s 刷）和"应用退到后台"
 * （降频到 30s）。这个状态来自 [androidx.lifecycle.ProcessLifecycleOwner]，
 * 放在 domain 层是为了让调度器在单元测试里用假实现替换掉，
 * 不依赖 Android 生命周期。
 */
interface AppForegroundProvider {
    val isForeground: StateFlow<Boolean>
}
