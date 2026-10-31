package com.huaying.xstz.core.data

import androidx.lifecycle.ProcessLifecycleOwner
import com.huaying.xstz.core.domain.AppForegroundProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 基于 [ProcessLifecycleOwner] 的前后台判定。
 *
 * 用 WHOLE_APP 级别而非单个 Activity：行情刷新是全局行为，
 * 用户从一个 Tab 切到另一个 Tab 不该触发降频——只有整个应用
 * 退到后台（HOME / 切到别的 App）才算"后台"。
 */
@Singleton
class AppForegroundProviderImpl @Inject constructor() : AppForegroundProvider {

    private val _isForeground = MutableStateFlow(true)
    override val isForeground: StateFlow<Boolean> get() = _isForeground

    init {
        val owner = ProcessLifecycleOwner.get()
        owner.lifecycle.addObserver(
            object : androidx.lifecycle.DefaultLifecycleObserver {
                override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
                    _isForeground.value = true
                }

                override fun onStop(owner: androidx.lifecycle.LifecycleOwner) {
                    _isForeground.value = false
                }
            },
        )
    }
}
