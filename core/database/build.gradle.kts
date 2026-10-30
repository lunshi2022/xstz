plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

// Room 的 schema 导出：每次改表都会落一份 JSON，用于后续写迁移测试
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    api(projects.core.model)
    api(libs.room.runtime)
    api(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.room.testing)
    testImplementation(libs.robolectric)
}
