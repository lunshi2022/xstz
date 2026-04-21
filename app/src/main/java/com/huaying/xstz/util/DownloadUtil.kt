package com.huaying.xstz.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File

/**
 * 下载工具类
 */
class DownloadUtil(private val context: Context) {

    /**
     * 下载 APK 文件
     * @param url 下载链接
     * @param fileName 文件名
     * @return 下载任务 ID
     */
    fun downloadApk(url: String, fileName: String): Long {
        // 使用应用私有目录，不需要存储权限
        val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir
        val file = File(downloadDir, fileName)

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("正在下载更新")
            .setDescription("下载完成后将自动安装")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(file))
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            // 允许在移动网络下下载
            .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return downloadManager.enqueue(request)
    }

    /**
     * 注册下载完成监听器
     * @param downloadId 下载任务 ID
     * @param fileName 文件名
     * @param onComplete 下载完成回调
     */
    fun registerDownloadCompleteListener(
        downloadId: Long,
        fileName: String,
        onComplete: (Uri) -> Unit
    ) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    // 从应用私有目录获取文件
                    val downloadDir = context?.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                        ?: context?.filesDir
                    val file = File(downloadDir, fileName)
                    
                    if (file.exists()) {
                        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            FileProvider.getUriForFile(
                                context!!,
                                "${context.packageName}.fileprovider",
                                file
                            )
                        } else {
                            Uri.fromFile(file)
                        }
                        onComplete(uri)
                    }
                    context?.unregisterReceiver(this)
                }
            }
        }

        context.registerReceiver(
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        )
    }
}
