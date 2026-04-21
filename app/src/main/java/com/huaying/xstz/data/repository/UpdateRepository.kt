package com.huaying.xstz.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.huaying.xstz.data.api.GitHubApi
import com.huaying.xstz.data.model.UpdateInfo
import com.huaying.xstz.data.model.UpdateResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.UnknownHostException
import java.net.SocketTimeoutException
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 应用更新检查仓库
 */
class UpdateRepository(private val context: Context) {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .proxy(Proxy.NO_PROXY) // 禁用代理，避免被系统代理干扰
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api = retrofit.create(GitHubApi::class.java)

    /**
     * 检查是否有新版本
     * @param owner GitHub 用户名
     * @param repo 仓库名
     * @return 更新检查结果
     */
    suspend fun checkForUpdate(owner: String, repo: String): UpdateResult {
        return withContext(Dispatchers.IO) {
            try {
                // 先检查网络连接
                if (!isNetworkAvailable()) {
                    return@withContext UpdateResult(hasUpdate = false, error = "网络连接不可用，请检查网络设置")
                }

                val release = api.getLatestRelease(owner, repo)
                val latestVersionName = release.tag_name.removePrefix("v")
                val currentVersionCode = getCurrentVersionCode()
                val currentVersionName = getCurrentVersionName()

                // 解析版本号 (如 "1.0.1" -> 10001)
                val latestVersionCode = parseVersionCode(latestVersionName)

                // 检查是否有更新
                val hasUpdate = latestVersionCode > currentVersionCode ||
                        (latestVersionCode == currentVersionCode && latestVersionName != currentVersionName)

                // 查找 APK 文件
                val apkAsset = release.assets.find { it.name.endsWith(".apk", ignoreCase = true) }

                if (hasUpdate && apkAsset != null) {
                    UpdateResult(
                        hasUpdate = true,
                        updateInfo = UpdateInfo(
                            versionName = latestVersionName,
                            versionCode = latestVersionCode,
                            downloadUrl = apkAsset.browser_download_url,
                            releaseNotes = release.body
                        )
                    )
                } else {
                    UpdateResult(hasUpdate = false)
                }
            } catch (e: UnknownHostException) {
                UpdateResult(hasUpdate = false, error = "无法连接到服务器，请检查网络设置")
            } catch (e: SocketTimeoutException) {
                UpdateResult(hasUpdate = false, error = "连接超时，请稍后重试")
            } catch (e: IOException) {
                UpdateResult(hasUpdate = false, error = "网络连接失败，请检查网络设置")
            } catch (e: Exception) {
                UpdateResult(hasUpdate = false, error = "检查更新失败，请稍后重试")
            }
        }
    }

    /**
     * 检查网络是否可用
     */
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * 获取当前应用版本号
     */
    private fun getCurrentVersionCode(): Int {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.longVersionCode.toInt()
        } catch (e: Exception) {
            1
        }
    }

    /**
     * 获取当前应用版本名称
     */
    private fun getCurrentVersionName(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    /**
     * 解析版本号为整数 (1.0.1 -> 10001)
     */
    private fun parseVersionCode(versionName: String): Int {
        val parts = versionName.split(".")
        var code = 0
        for (i in parts.indices) {
            if (i < 3) {
                val num = parts[i].toIntOrNull() ?: 0
                code = code * 100 + num
            }
        }
        return code
    }
}
