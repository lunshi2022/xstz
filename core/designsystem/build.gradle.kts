plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

dependencies {
    api(projects.core.model)

    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.ui.graphics)
    api(libs.compose.foundation)
    api(libs.compose.material3)
    api(libs.compose.material.icons)
    api(libs.compose.ui.tooling.preview)
    api(libs.compose.ui.util)
    debugImplementation(libs.compose.ui.tooling)

    // Liquid Glass 三件套（Haze 2.0 的正确坐标，没有 haze-materials）
    api(libs.haze)
    implementation(libs.haze.blur)
    implementation(libs.haze.blur.materials)
}
