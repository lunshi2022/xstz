plugins {
    alias(libs.plugins.android.library)
}

dependencies {
    api(projects.core.model)
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.coroutines.test)
}
