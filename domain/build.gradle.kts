import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    // Тесты фигур сверяют виды и числа с макетом (`DesignSources` читает `../design`). Файлы вне модуля Gradle сам входами
    // не считает: без этого правка макета оставляла бы тесты UP-TO-DATE.
    inputs.files(rootProject.file("design/build.py"), rootProject.file("design/editor-shapes.js"))
        .withPropertyName("designSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
