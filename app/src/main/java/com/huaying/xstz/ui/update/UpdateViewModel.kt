package com.huaying.xstz.ui.update

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huaying.xstz.data.model.UpdateInfo
import com.huaying.xstz.data.repository.UpdateRepository
import com.huaying.xstz.util.DownloadUtil
import com.huaying.xstz.util.InstallUtil
import kotlinx.coroutines.launch

/**
 * 更新检查 ViewModel
 */
class UpdateViewModel(context: Context) : ViewModel() {

    private val repository = UpdateRepository(context)
    private val downloadUtil = DownloadUtil(context)
    private val appContext = context.applicationContext

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
    // GitHub 配置
    companion object {
        const val GITHUB_OWNER = "lunshi2022"  // GitHub 用户名
        const val GITHUB_REPO = "xstz"         // 仓库名
    }

    /**
     * 检查更新
     * @param showNoUpdate 是否显示"已是最新"提示
     */
    fun checkForUpdate(showNoUpdate: Boolean = false) {
        viewModelScope.launch {
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
            val fileName = "xstz-update-${info.versionName}.apk"
            val downloadId = downloadUtil.downloadApk(info.downloadUrl, fileName)
            downloadUtil.registerDownloadCompleteListener(downloadId, fileName) { uri ->
                InstallUtil.installApk(appContext, uri)
            }
            dismissUpdateDialog()
        }
    }

    /**
     * 关闭更新对话框
     */
    fun dismissUpdateDialog() {
        showUpdateDialog = false
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
