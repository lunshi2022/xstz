package com.huaying.xstz

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.huaying.xstz.core.data.settings.SettingsRepositoryImpl
import com.huaying.xstz.core.domain.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
            val systemDark = isSystemInDarkTheme()
            val dark = when (settings.themeMode) {
                SettingsRepositoryImpl.THEME_LIGHT -> false
                SettingsRepositoryImpl.THEME_DARK -> true
                else -> systemDark
            }
            XstzApp(
                darkTheme = dark,
                reduceMotion = settings.reduceMotion,
                reduceTransparency = settings.reduceTransparency,
            )
        }
    }
}

data class ShellState(
    val themeMode: Int = SettingsRepositoryImpl.THEME_AUTO,
    val reduceTransparency: Boolean = false,
    val reduceMotion: Boolean = false,
)

@HiltViewModel
class ShellViewModel @Inject constructor(
    settings: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<ShellState> = combine(
        settings.observeThemeMode(),
        settings.observeReducedTransparency(),
    ) { theme, transparency ->
        ShellState(
            themeMode = theme,
            reduceTransparency = transparency,
            // 动画与透明度暂时同源：两者都是"减少视觉刺激"的无障碍诉求。
            // 后续接入 Settings.Global.ANIMATOR_DURATION_SCALE 后可独立控制。
            reduceMotion = transparency,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShellState())
}
