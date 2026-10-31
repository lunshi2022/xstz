plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

// Room 的 schema 导出：每次改表都会落一份 JSON，用于后续写迁移测试
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

android {
    // MigrationTestHelper 在运行时从测试 APK 的 assets 里读 schema JSON 来比对表结构。
    // 它期望的路径是 `<DatabaseClass>/<version>.json`，所以必须把 schemas 目录
    // 的**父目录**挂成 assets 根，而不是 schemas 目录本身——
    // 挂错一层的报错是 FileNotFoundException: Missing file: ...XstzDatabase/1.json。
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
}

dependencies {
    api(projects.core.model)
    api(libs.room.runtime)
    api(libs.room.ktx)
    ksp(libs.room.compiler)

    // 整库加密。SQLCipher 提供 SupportOpenHelperFactory，
    // Room 通过 openHelperFactory 接管后，DAO 层代码完全不需要改。
    implementation(libs.sqlcipher)

    testImplementation(libs.room.testing)
    testImplementation(libs.robolectric)

    // 设备侧测试：SQLCipher 的 native 库无法在 Robolectric 下加载，
    // 加密生效与 SUM() 聚合正确性只能在真机/模拟器上验。
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.coroutines.test)
}
