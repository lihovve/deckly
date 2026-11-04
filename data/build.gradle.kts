plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room3)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.deckly.data"
    compileSdk = 37

    defaultConfig { minSdk = 35 }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    // Robolectric берёт манифест и ресурсы из сборки.
    testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Схема базы лежит в репозитории: каждая смена версии — явная миграция с тестом.
room3 { schemaDirectory("$projectDir/schemas") }

dependencies {
    implementation(project(":domain"))
    implementation(libs.room3.runtime)
    ksp(libs.room3.compiler)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.room3.testing)
}
