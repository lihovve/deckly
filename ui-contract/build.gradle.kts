plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.deckly.ui.contract"
    compileSdk = 37

    defaultConfig { minSdk = 35 }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    api(platform(libs.compose.bom))
    api(libs.compose.runtime)
}
