plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.deckly.ink"
    compileSdk = 37

    defaultConfig { minSdk = 35 }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    testOptions {
        unitTests {
            // Robolectric берёт манифест и ресурсы из сборки.
            isIncludeAndroidResources = true
            // Robolectric грузит нативные библиотеки; в будущих JDK без этого флага это станет ошибкой.
            all { it.jvmArgs("--enable-native-access=ALL-UNNAMED") }
        }
    }
}

dependencies {
    implementation(project(":domain"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)

    // Onyx SDK с исключениями и зависимостями прототипа (spike/build.gradle.kts).
    implementation(libs.onyx.device) {
        exclude(group = "com.android.support", module = "support-compat")
    }
    implementation(libs.onyx.pen) {
        exclude(group = "com.android.support", module = "support-compat")
        exclude(group = "com.android.support", module = "appcompat-v7")
        exclude(group = "com.tencent", module = "mmkv")
    }
    implementation(libs.onyx.base) {
        exclude(group = "com.android.support", module = "support-compat")
        exclude(group = "com.android.support", module = "appcompat-v7")
        exclude(group = "com.tencent", module = "mmkv")
    }
    implementation(libs.hiddenapibypass)
    implementation(libs.rxjava2)
    implementation(libs.rxandroid2)

    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    // Activity для createComposeRule: без неё Robolectric не находит, куда поставить содержимое.
    debugImplementation(platform(libs.compose.bom))
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
