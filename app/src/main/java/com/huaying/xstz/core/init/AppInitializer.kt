package com.huaying.xstz.core.init

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.huaying.xstz.data.AppDatabase
import com.huaying.xstz.data.PreferenceManager
import com.huaying.xstz.data.repository.FundRepository
import com.huaying.xstz.data.repository.OperationLogRepository
import com.huaying.xstz.data.repository.OperationLogger
import com.huaying.xstz.ui.theme.InvestmentManagerTheme
import com.huaying.xstz.util.performance.StartupTracer

/**
 * 应用初始化状态
 */
sealed class AppInitState {
    data object Loading : AppInitState()
    data class Success(
        val repository: FundRepository,
        val operationLogRepository: OperationLogRepository
    ) : AppInitState()
    data class Error(val message: String) : AppInitState()
}

/**
 * 异步初始化应用
 * 使用suspend函数避免阻塞主线程
 */
suspend fun initializeApp(
    context: Context,
    preferenceManager: PreferenceManager
): AppInitState {
    return try {
        StartupTracer.markMilestone("init_start")

        // 并行初始化数据库和其他资源
        val database = AppDatabase.getDatabase(context)
        StartupTracer.markMilestone("database_ready")

        val repository = FundRepository(database)
        val operationLogRepository = OperationLogRepository(database)
        StartupTracer.markMilestone("repository_ready")

        // 初始化全局操作日志记录器
        OperationLogger.init(operationLogRepository)
        StartupTracer.markMilestone("logger_ready")

        // 预加载必要数据（如果需要）
        // 这里可以添加其他初始化逻辑

        AppInitState.Success(repository, operationLogRepository)
    } catch (e: Exception) {
        Log.e("AppInitializer", "初始化失败", e)
        AppInitState.Error(e.message ?: "未知错误")
    }
}
