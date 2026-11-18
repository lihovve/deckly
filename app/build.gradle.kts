plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.deckly"
    compileSdk = 37

    buildFeatures { compose = true }

    defaultConfig {
        applicationId = "app.deckly"
        minSdk = 35
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"

        ndk { abiFilters += "arm64-v8a" }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildTypes {
        // Релизная сборка нужна, чтобы ART исполнял приложение без отладочных ограничений: у отлаживаемого приложения
        // часть оптимизаций выключена, и Compose в такой сборке заметно медленнее. Приложение ставится только на свой
        // планшет и в магазин не идёт, поэтому отдельного ключа нет: подпись отладочным ключом даёт ставить релизную
        // сборку поверх отладочной и обратно без удаления. R8 выключен, пока перо Onyx SDK (рефлексия) не проверено с ним
        // на планшете.
        release {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
        }
    }

    // onyxsdk-pen и onyxsdk-pennative приносят каждый свой libc++_shared.so.
    packaging {
        jniLibs { pickFirsts += "**/libc++_shared.so" }
    }

    testOptions {
        unitTests {
            // Robolectric берёт манифест и ресурсы из сборки.
            isIncludeAndroidResources = true
            // Robolectric грузит нативные библиотеки (SQLite, графика); в будущих JDK без этого флага это станет ошибкой.
            all { it.jvmArgs("--enable-native-access=ALL-UNNAMED") }
        }
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":ink"))
    implementation(project(":ui-contract"))
    implementation(project(":design-e"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.activity.compose)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    // Activity для createAndroidComposeRule<ComponentActivity>: без неё Robolectric не находит, куда поставить содержимое.
    debugImplementation(platform(libs.compose.bom))
    debugImplementation(libs.compose.ui.test.manifest)
}
