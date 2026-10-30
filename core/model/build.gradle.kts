plugins {
    alias(libs.plugins.android.library)
}

dependencies {
    // 纯数据模型：不依赖 Android、不依赖协程，保证可以给任何层直接用
}
