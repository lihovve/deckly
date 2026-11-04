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

    testOptions {
        unitTests {
            // Robolectric берёт манифест и ресурсы из сборки.
            isIncludeAndroidResources = true
            // Robolectric грузит нативную SQLite; в будущих JDK без этого флага это станет ошибкой.
            all { it.jvmArgs("--enable-native-access=ALL-UNNAMED") }
        }
    }
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
