package com.huaying.xstz.data.repository

import android.content.Context
import android.content.pm.PackageManager
import com.huaying.xstz.data.api.GitHubApi
import com.huaying.xstz.data.model.UpdateInfo
import com.huaying.xstz.data.model.UpdateResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * 应用更新检查仓库
 */
class UpdateRepository(private val context: Context) {

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
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
            } catch (e: Exception) {
                UpdateResult(hasUpdate = false, error = e.message)
            }
        }
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
