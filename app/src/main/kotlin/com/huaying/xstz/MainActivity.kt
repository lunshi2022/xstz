package com.huaying.xstz

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.huaying.xstz.core.designsystem.tokens.Motion
import com.huaying.xstz.core.domain.repository.SettingsRepository
import com.huaying.xstz.feature.onboarding.OnboardingScreen
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 透明系统栏：玻璃要能延伸到状态栏下方，否则顶部会有一条硬边
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )
        super.onCreate(savedInstanceState)

        setContent {
            val vm: ShellViewModel = hiltViewModel()
            val settings by vm.state.collectAsStateWithLifecycle()

            // 引导只决定"显示哪个壳"，不改变主题装配——
            // 引导页本身也要正确的深浅色，否则第一印象就是错的
            val systemDark = isSystemInDarkTheme()
            val dark = when (settings.themeMode) {
                SettingsRepository.THEME_LIGHT -> false
                SettingsRepository.THEME_DARK -> true
                else -> systemDark
            }

            Crossfade(
                targetState = settings.needsOnboarding,
                animationSpec = tween(Motion.PAGE_MS),
                label = "shell-onboarding",
            ) { needsOnboarding ->
                if (needsOnboarding) {
                    OnboardingScreen(onDone = vm::completeOnboarding)
                } else {
                    XstzApp(
                        darkTheme = dark,
                        reduceMotion = settings.reduceMotion,
                        reduceTransparency = settings.reduceTransparency,
                        highContrast = settings.highContrast,
                    )
                }
            }
        }
    }
}

data class ShellState(
    val themeMode: Int = SettingsRepository.THEME_AUTO,
    val reduceTransparency: Boolean = false,
    val reduceMotion: Boolean = false,
    val highContrast: Boolean = false,
    /** 首次启动（或数据被清除）时为 true，显示引导页 */
    val needsOnboarding: Boolean = false,
)

@HiltViewModel
class ShellViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<ShellState> = combine(
        settings.observeThemeMode(),
        settings.observeReducedTransparency(),
        // 系统级「减弱动效」：对接 Android 的动画时长缩放开关
        // （Settings.Global / Secure 的 ANIMATOR_DURATION_SCALE == 0，即开发者选项
        // 「动画程序时长缩放」或无障碍「移除动画」），不再与透明度同源。
        settings.observeReducedMotion(),
        settings.observeHighContrast(),
        // 引导状态必须是响应式流：completeOnboarding() 是在 App 运行期间写它的，
        // 一次性 Flow 发完即结束，会导致点了「跳过」退不出引导页（真机已复现）。
        settings.observeOnboardingDone(),
    ) { theme, transparency, motion, highContrast, onboarded ->
        ShellState(
            themeMode = theme,
            reduceTransparency = transparency,
            reduceMotion = motion,
            highContrast = highContrast,
            needsOnboarding = !onboarded,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShellState())

    /** 引导完成（或被跳过）。与「下一步走完」走同一条路径，不区分来源 */
    fun completeOnboarding() {
        viewModelScope.launch { settings.setOnboardingDone(true) }
    }
}
