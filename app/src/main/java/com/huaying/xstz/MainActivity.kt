package com.huaying.xstz

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.google.gson.Gson
import com.huaying.xstz.core.init.AppInitState
import com.huaying.xstz.core.init.initializeApp
import com.huaying.xstz.data.PreferenceManager
import com.huaying.xstz.data.model.ExportData
import com.huaying.xstz.data.repository.FundRepository
import com.huaying.xstz.data.repository.OperationLogRepository
import com.huaying.xstz.data.repository.OperationLogger
import com.huaying.xstz.data.repository.UpdateRepository
import com.huaying.xstz.data.security.CryptoManager
import com.huaying.xstz.ui.mainscreen.MainScreen
import com.huaying.xstz.ui.theme.InvestmentManagerTheme
import com.huaying.xstz.ui.update.UpdateDialog
import com.huaying.xstz.util.DownloadUtil
import com.huaying.xstz.util.InstallUtil
import com.huaying.xstz.util.performance.StartupTracer
import com.jakewharton.threetenabp.AndroidThreeTen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * 主Activity
 * 仅负责Activity生命周期管理，其他职责委托给相应组件
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 记录启动开始时间
        StartupTracer.markAppStart()

        // 安装系统启动屏 - 必须在super.onCreate之前
        val splashScreen = installSplashScreen()

        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        StartupTracer.markMilestone("activity_created")

        // 初始化时间库 - 必须在setContent之前
        AndroidThreeTen.init(this)
        StartupTracer.markMilestone("threeten_initialized")

        setContent {
            InvestmentManagerApp()
        }

        StartupTracer.markMilestone("content_set")

        // 设置启动屏退出动画
        splashScreen.setOnExitAnimationListener { splashScreenView ->
            splashScreenView.view
                .animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    splashScreenView.remove()
                    StartupTracer.markMilestone("splash_exited")
                    Log.d("Startup", StartupTracer.getStartupReport())
                }
                .start()
        }
    }
}

/**
 * 投资管理应用主入口
 * 负责主题设置和应用初始化流程
 */
@Composable
fun InvestmentManagerApp() {
    val context = LocalContext.current
    val preferenceManager = remember { PreferenceManager(context) }
    val themeMode by preferenceManager.themeMode.collectAsState(initial = 0)
    val dynamicColorEnabled by preferenceManager.dynamicColorEnabled.collectAsState(initial = true)

    val systemDarkTheme = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        0 -> systemDarkTheme
        1 -> false
        2 -> true
        else -> systemDarkTheme
    }

    val view = LocalView.current
    SideEffect {
        val window = (view.context as android.app.Activity).window
        val insetsController = WindowCompat.getInsetsController(window, view)

        // 配置状态栏 - 使用透明背景，与App背景融合
        window.statusBarColor = android.graphics.Color.TRANSPARENT

        // 根据主题设置状态栏图标颜色
        // 深色主题：白色图标；浅色主题：黑色图标
        insetsController.isAppearanceLightStatusBars = !darkTheme
    }

    InvestmentManagerTheme(
        darkTheme = darkTheme,
        dynamicColorEnabled = dynamicColorEnabled
    ) {
        MainApp(preferenceManager, themeMode, dynamicColorEnabled, darkTheme)
    }
}

/**
 * 主应用组件
 * 负责应用初始化和状态管理
 */
@Composable
fun MainApp(
    preferenceManager: PreferenceManager,
    themeMode: Int,
    dynamicColorEnabled: Boolean,
    darkTheme: Boolean
) {
    val context = LocalContext.current

    // 使用produceState进行异步初始化，避免阻塞UI
    val appState by produceState<AppInitState>(initialValue = AppInitState.Loading) {
        value = initializeApp(context, preferenceManager)
    }

    when (appState) {
        is AppInitState.Loading -> {
            // 显示加载状态 - 使用简洁的加载指示器
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp
                )
            }
        }
        is AppInitState.Success -> {
            val state = appState as AppInitState.Success
            MainContent(
                preferenceManager = preferenceManager,
                themeMode = themeMode,
                dynamicColorEnabled = dynamicColorEnabled,
                darkTheme = darkTheme,
                repository = state.repository,
                operationLogRepository = state.operationLogRepository
            )
        }
        is AppInitState.Error -> {
            // 显示错误状态
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "加载失败，请重启应用",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * 主内容组件
 * 负责数据导入导出功能和主界面渲染
 */
@Composable
fun MainContent(
    preferenceManager: PreferenceManager,
    themeMode: Int,
    dynamicColorEnabled: Boolean,
    darkTheme: Boolean,
    repository: FundRepository,
    operationLogRepository: OperationLogRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gson = remember { Gson() }
    val cryptoManager = remember { CryptoManager() }

    // 自动检查更新状态
    var showAutoUpdateDialog by remember { mutableStateOf(false) }
    var autoUpdateInfo by remember { mutableStateOf<com.huaying.xstz.data.model.UpdateInfo?>(null) }

    // 应用启动时自动检查更新
    LaunchedEffect(Unit) {
        // 延迟 3 秒后检查更新，避免影响启动速度
        delay(3000)
        val updateRepository = UpdateRepository(context)
        val result = updateRepository.checkForUpdate("lunshi2022", "xstz")
        if (result.hasUpdate && result.updateInfo != null) {
            autoUpdateInfo = result.updateInfo
            showAutoUpdateDialog = true
        }
    }

    val refreshIntervalSeconds by preferenceManager.refreshIntervalSeconds.collectAsState(initial = 3)
    val refreshMode by preferenceManager.refreshMode.collectAsState(initial = 0)
    val privacyModeEnabled by preferenceManager.privacyModeEnabled.collectAsState(initial = false)

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri ->
            uri?.let {
                scope.launch {
                    try {
                        val (funds, transactions, records) = repository.getAllDataForExport()
                        val exportData = ExportData(funds, transactions, records)
                        val json = gson.toJson(exportData)
                        val encryptedData = cryptoManager.encrypt(json)
                        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                            OutputStreamWriter(outputStream).use { writer -> writer.write(encryptedData) }
                        }
                        val toast = android.widget.Toast.makeText(context, "加密数据导出成功", android.widget.Toast.LENGTH_SHORT)
                        toast.setGravity(android.view.Gravity.CENTER, 0, 0)
                        toast.show()
                    } catch (e: Exception) {
                        val toast = android.widget.Toast.makeText(context, "导出失败: ${e.message}", android.widget.Toast.LENGTH_SHORT)
                        toast.setGravity(android.view.Gravity.CENTER, 0, 0)
                        toast.show()
                    }
                }
            }
        }
    )

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                scope.launch {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            InputStreamReader(inputStream).use { reader ->
                                val content = reader.readText()
                                val json = try { cryptoManager.decrypt(content) } catch (e: Exception) { content }
                                val finalJson = if (json.isEmpty() && content.isNotEmpty()) content else json
                                val type = object : com.google.gson.reflect.TypeToken<ExportData>() {}.type
                                val importData: ExportData = gson.fromJson(finalJson, type)
                                repository.importAllData(importData.funds, importData.transactions, importData.records)
                                val toast = android.widget.Toast.makeText(context, "数据导入成功", android.widget.Toast.LENGTH_SHORT)
                                toast.setGravity(android.view.Gravity.CENTER, 0, 0)
                                toast.show()
                            }
                        }
                    } catch (e: Exception) {
                        val toast = android.widget.Toast.makeText(context, "导入失败: 确认文件正确", android.widget.Toast.LENGTH_SHORT)
                        toast.setGravity(android.view.Gravity.CENTER, 0, 0)
                        toast.show()
                    }
                }
            }
        }
    )

    MainScreen(
        repository = repository,
        operationLogRepository = operationLogRepository,
        preferenceManager = preferenceManager,
        themeMode = themeMode,
        dynamicColorEnabled = dynamicColorEnabled,
        refreshIntervalSeconds = refreshIntervalSeconds,
        refreshMode = refreshMode,
        privacyModeEnabled = privacyModeEnabled,
        darkTheme = darkTheme,
        exportLauncher = exportLauncher,
        importLauncher = importLauncher,
        scope = scope
    )

    // 自动更新对话框
    if (showAutoUpdateDialog && autoUpdateInfo != null) {
        UpdateDialog(
            updateInfo = autoUpdateInfo!!,
            onDismiss = { showAutoUpdateDialog = false },
            onConfirm = {
                val downloadUtil = DownloadUtil(context)
                val fileName = "xstz-update-${autoUpdateInfo!!.versionName}.apk"
                val downloadId = downloadUtil.downloadApk(autoUpdateInfo!!.downloadUrl, fileName)
                downloadUtil.registerDownloadCompleteListener(downloadId) { uri ->
                    InstallUtil.installApk(context, uri)
                }
                showAutoUpdateDialog = false
            }
        )
    }
}
