# 先生投资 (XSTZ)

一款基于 Jetpack Compose 的 Android 基金投资管理应用。

## 功能

- **资产概览** — 实时跟踪基金行情，支持智能/固定频率刷新，自动识别交易日与节假日
- **图表分析** — 收益走势、资产配置等多维度图表（MPAndroidChart）
- **再平衡** — 根据目标仓位自动计算买卖建议，支持自定义阈值
- **目标仓位** — 灵活配置各基金目标占比
- **交易管理** — 记录买卖交易，查看交易历史
- **日历视图** — 按日查看资产变动，标注法定节假日
- **数据安全** — AES 加密导入导出，Android Keystore 保护
- **深色模式** — 支持自动/浅色/深色主题与动态取色
- **在线更新** — 通过 GitHub Release 检查并下载新版本

## 技术栈

| 技术 | 版本 |
|------|------|
| Kotlin | 1.9.22 |
| Jetpack Compose (BOM) | 2024.02.00 |
| Compose Compiler | 1.5.8 |
| Hilt | 2.50 |
| Room | 2.6.1 |
| MPAndroidChart | v3.1.0 |
| Retrofit + OkHttp | 2.9.0 / 4.12.0 |
| Android Gradle Plugin | 8.2.2 |
| Min SDK / Target SDK | 34 (Android 14) |

## 构建

```powershell
# 设置 JAVA_HOME（使用 Android Studio 内置 JBR）
$env:JAVA_HOME="D:\LeStoreSoftstore\Install\Android_Projram\Studio\jbr"

# 构建 Debug APK
.\gradlew.bat assembleDebug

# 安装到设备
.\gradlew.bat installDebug
```

APK 产物路径：`app/build/outputs/apk/debug/app-debug.apk`

## 项目结构

```
app/src/main/java/com/huaying/xstz/
├── core/init/           # 应用初始化
├── data/
│   ├── api/             # Retrofit 网络接口
│   ├── dao/             # Room DAO
│   ├── entity/          # Room 实体
│   ├── model/           # 业务数据模型
│   ├── repository/      # 仓库层
│   ├── security/        # 加密管理
│   ├── AppDatabase.kt   # Room 数据库
│   └── PreferenceManager.kt
├── di/                  # Hilt 依赖注入
├── ui/                  # Compose UI
│   ├── assetoverview/   # 资产概览
│   ├── charts/          # 图表分析
│   ├── rebalance/       # 再平衡
│   ├── targetallocation/# 目标仓位
│   ├── fund/            # 基金详情与交易
│   ├── calendar/        # 日历视图
│   ├── settings/        # 设置与关于
│   ├── theme/           # 主题与样式
│   └── component/       # 通用组件
├── util/                # 工具类
├── InvestmentApp.kt     # Application
└── MainActivity.kt      # 主 Activity
```

## 发布新版本

1. 修改 `app/build.gradle.kts` 中的 `versionCode` 和 `versionName`
2. 构建 Release APK：`.\gradlew.bat assembleRelease`
3. 在 GitHub 创建 Release：
   - Tag 格式：`vX.Y.Z`（如 `v1.7.0`）
   - 上传 APK 作为 Asset（文件名须以 `.apk` 结尾）
   - 填写更新说明

应用通过 GitHub API (`repos/lunshi2022/xstz/releases/latest`) 自动检查更新。

## 许可证

[MIT License](LICENSE)
