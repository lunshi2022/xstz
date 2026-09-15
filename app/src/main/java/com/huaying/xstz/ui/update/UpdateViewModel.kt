package com.huaying.xstz.ui.update

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huaying.xstz.data.PreferenceManager
import com.huaying.xstz.data.model.UpdateInfo
import com.huaying.xstz.data.repository.UpdateRepository
import com.huaying.xstz.util.DownloadUtil
import com.huaying.xstz.util.InstallUtil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 更新检查 ViewModel
 */
class UpdateViewModel(context: Context) : ViewModel() {

    private val repository = UpdateRepository(context)
    private val downloadUtil = DownloadUtil(context)
    private val appContext = context.applicationContext
    private val preferenceManager = PreferenceManager(context)

    // 当前版本名
    val currentVersionName: String = try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        packageInfo.versionName ?: ""
    } catch (e: Exception) {
        ""
    }

    // 是否显示更新对话框
    var showUpdateDialog by mutableStateOf(false)
        private set

    // 是否显示检查中对话框
    var showCheckingDialog by mutableStateOf(false)
        private set

    // 是否显示已是最新对话框
    var showNoUpdateDialog by mutableStateOf(false)
        private set

    // 是否显示错误对话框
    var showErrorDialog by mutableStateOf(false)
        private set

    // 更新信息
    var updateInfo by mutableStateOf<UpdateInfo?>(null)
        private set

    // 错误信息
    var errorMessage by mutableStateOf("")
        private set

    // 是否正在下载
    var isDownloading by mutableStateOf(false)
        private set

    // GitHub 配置
    companion object {
        const val GITHUB_OWNER = "lunshi2022"  // GitHub 用户名
        const val GITHUB_REPO = "xstz"         // 仓库名
        private const val DISMISS_COOLDOWN_MS = 3 * 24 * 60 * 60 * 1000L // 3天冷却期
    }

    /**
     * 检查更新
     * @param showNoUpdate 是否显示"已是最新"提示
     */
    fun checkForUpdate(showNoUpdate: Boolean = false) {
        viewModelScope.launch {
            // 自动检查时，如果在冷却期内则跳过
            if (!showNoUpdate) {
                val lastDismissed = preferenceManager.lastUpdateDismissed.first()
                if (lastDismissed > 0 && System.currentTimeMillis() - lastDismissed < DISMISS_COOLDOWN_MS) {
                    return@launch
                }
            }

            showCheckingDialog = true
            val result = repository.checkForUpdate(GITHUB_OWNER, GITHUB_REPO)
            showCheckingDialog = false

            when {
                result.hasUpdate && result.updateInfo != null -> {
                    updateInfo = result.updateInfo
                    showUpdateDialog = true
                }
                showNoUpdate && result.error == null -> {
                    showNoUpdateDialog = true
                }
                result.error != null && showNoUpdate -> {
                    errorMessage = result.error
                    showErrorDialog = true
                }
            }
        }
    }

    /**
     * 下载并安装更新
     */
    fun downloadAndInstallUpdate() {
        updateInfo?.let { info ->
            isDownloading = true
            val fileName = "xstz-update-${info.versionName}.apk"
            val downloadId = downloadUtil.downloadApk(info.downloadUrl, fileName)
            downloadUtil.registerDownloadCompleteListener(downloadId, fileName) { uri ->
                isDownloading = false
                InstallUtil.installApk(appContext, uri)
            }
            dismissUpdateDialog()
        }
    }

    /**
     * 关闭更新对话框（记录冷却时间）
     */
    fun dismissUpdateDialog() {
        showUpdateDialog = false
        viewModelScope.launch {
            preferenceManager.setLastUpdateDismissed(System.currentTimeMillis())
        }
    }

    /**
     * 关闭检查中对话框
     */
    fun dismissCheckingDialog() {
        showCheckingDialog = false
    }

    /**
     * 关闭已是最新对话框
     */
    fun dismissNoUpdateDialog() {
        showNoUpdateDialog = false
    }

    /**
     * 关闭错误对话框
     */
    fun dismissErrorDialog() {
        showErrorDialog = false
    }
}
