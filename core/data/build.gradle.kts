plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.core.model)
    api(projects.core.common)
    api(projects.core.domain)

    implementation(projects.core.database)
    implementation(projects.core.network)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.lifecycle.process)

    // 备份口令派生：纯 JVM 实现，不含 native 库，规避 16KB 页对齐问题
    implementation(libs.password4j)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.room.testing)
    testImplementation(libs.robolectric)
}
