package com.huaying.xstz.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huaying.xstz.core.domain.repository.OperationLogEntry
import com.huaying.xstz.core.domain.repository.OperationLogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OperationLogUiState(
    val entries: List<OperationLogEntry> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class OperationLogViewModel @Inject constructor(
    private val repository: OperationLogRepository,
) : ViewModel() {

    val state: StateFlow<OperationLogUiState> =
        repository.observeRecent(RECENT_LIMIT)
            .map { OperationLogUiState(entries = it, loading = false) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = OperationLogUiState(),
            )

    /** 清空日志。无确认对话框——日志不是用户数据，可随时重记 */
    fun clear() {
        viewModelScope.launch { repository.clear() }
    }

    companion object {
        /** 与 DAO 的默认上限保持一致；界面尾部提示也引用此值 */
        const val RECENT_LIMIT = 200
    }
}
