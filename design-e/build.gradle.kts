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
            all {
                it.jvmArgs("--enable-native-access=ALL-UNNAMED")
                // Тесты сверяют тексты, значки и токены с макетом (`MockupSources` читает `../design`). Файлы вне модуля Gradle
                // сам входами не считает: без этого правка макета оставляла бы тесты UP-TO-DATE, и «зелёный» ничего не значил.
                it.inputs.files(
                    rootProject.file("design/build.py"),
                    rootProject.file("design/styles.css"),
                    rootProject.file("design/editor-shapes.js"),
                )
                    .withPropertyName("mockupSources")
                    .withPathSensitivity(PathSensitivity.RELATIVE)
            }
        }
    }
}

dependencies {
    implementation(project(":ui-contract"))

    // Compose Foundation без Material: Material тянет ripple и анимации (документ архитектуры).
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    // BackHandler: открытое меню карточки закрывается системной «Назад», а открыто ли оно, знает только дизайн.
    implementation(libs.activity.compose)

    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    // Activity для createComposeRule: без неё Robolectric не находит, куда поставить содержимое.
    debugImplementation(platform(libs.compose.bom))
    debugImplementation(libs.compose.ui.test.manifest)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
