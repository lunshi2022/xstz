package com.huaying.xstz.data.model

/**
 * 应用更新信息数据类
 */
data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val downloadUrl: String,
    val releaseNotes: String,
    val forceUpdate: Boolean = false
)

/**
 * 更新检查结果
 */
data class UpdateResult(
    val hasUpdate: Boolean,
    val updateInfo: UpdateInfo? = null,
    val error: String? = null
)
