plugins {
    alias(libs.plugins.android.library)
}

dependencies {
    api(projects.core.model)
    api(projects.core.common)
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
}
