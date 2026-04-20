package com.huaying.xstz.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment

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
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("正在下载更新")
            .setDescription("下载完成后将自动安装")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return downloadManager.enqueue(request)
    }

    /**
     * 注册下载完成监听器
     * @param downloadId 下载任务 ID
     * @param onComplete 下载完成回调
     */
    fun registerDownloadCompleteListener(
        downloadId: Long,
        onComplete: (Uri) -> Unit
    ) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    val dm = context?.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    val uri = dm.getUriForDownloadedFile(downloadId)
                    uri?.let { onComplete(it) }
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
