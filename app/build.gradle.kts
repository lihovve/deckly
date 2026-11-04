plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "app.deckly"
    compileSdk = 37

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
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":ink"))
    implementation(project(":ui-contract"))
    implementation(project(":design-e"))
}
