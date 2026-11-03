plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "app.deckly.spike"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.deckly.spike"
        minSdk = 35
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"

        ndk { abiFilters += "arm64-v8a" }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
        jniLibs { pickFirsts += "**/libc++_shared.so" }
    }
}

dependencies {
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

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.core.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.room3.runtime)
    ksp(libs.room3.compiler)
}
