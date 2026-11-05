plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.deckly.design.e"
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
            // Robolectric грузит нативные библиотеки (графика); в будущих JDK без этого флага это станет ошибкой.
            all { it.jvmArgs("--enable-native-access=ALL-UNNAMED") }
        }
    }
}

dependencies {
    implementation(project(":ui-contract"))

    // Compose Foundation без Material: Material тянет ripple и анимации (документ архитектуры).
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)

    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    // Activity для createComposeRule: без неё Robolectric не находит, куда поставить содержимое.
    debugImplementation(platform(libs.compose.bom))
    debugImplementation(libs.compose.ui.test.manifest)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
