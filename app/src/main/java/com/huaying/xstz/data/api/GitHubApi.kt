package com.huaying.xstz.data.api

import retrofit2.http.GET
import retrofit2.http.Path

/**
 * GitHub API 接口
 */
interface GitHubApi {
    /**
     * 获取最新 Release 信息
     */
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GitHubRelease
}

/**
 * GitHub Release 响应数据类
 */
data class GitHubRelease(
    val tag_name: String,
    val name: String,
    val body: String,
    val published_at: String,
    val assets: List<GitHubAsset>
)

/**
 * GitHub Release Asset 数据类
 */
data class GitHubAsset(
    val name: String,
    val size: Long,
    val download_count: Int,
    val browser_download_url: String
)
