// Kotlin встроен в AGP 9, плагин org.jetbrains.kotlin.android не применяется.
// Версию KGP поднимает classpath ниже.
buildscript {
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room3) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// Разрешённые связи между модулями проекта (документ архитектуры, раздел «Модули»).
val allowedModuleDependencies = mapOf(
    "app" to setOf("domain", "data", "ink", "ui-contract", "design-e"),
    "data" to setOf("domain"),
    "ink" to setOf("domain"),
    "design-e" to setOf("ui-contract"),
    "ui-contract" to emptySet(),
    "domain" to emptySet(),
)

// Модули, которые в проверке не участвуют. Любой другой модуль обязан быть в таблице выше.
val modulesOutsideModuleGraph = setOf("spike")

tasks.register("verifyModuleGraph") {
    group = "verification"
    description = "Падает, если модуля нет в таблице связей или он зависит от запрещённого модуля."

    doLast {
        val unknownModules = subprojects
            .map { it.name }
            .filter { it !in allowedModuleDependencies && it !in modulesOutsideModuleGraph }
            .map { "$it: модуля нет в таблице связей" }
        val forbiddenDependencies = subprojects
            .filter { it.name in allowedModuleDependencies }
            .flatMap { module ->
                val allowed = allowedModuleDependencies.getValue(module.name)
                module.configurations
                    .flatMap { configuration -> configuration.allDependencies.withType<ProjectDependency>() }
                    // AGP кладёт сам модуль в конфигурации androidTest: это не граница модулей.
                    .filter { dependency -> dependency.path != module.path }
                    .map { dependency -> dependency.path.removePrefix(":") }
                    .filter { it !in allowed }
                    .map { "${module.name} → $it" }
            }
        val violations = (unknownModules + forbiddenDependencies).distinct().sorted()
        if (violations.isNotEmpty()) {
            throw GradleException("Нарушены границы модулей:\n" + violations.joinToString("\n") { "  $it" })
        }
    }
}

subprojects {
    tasks.matching { it.name == "check" }.configureEach { dependsOn(":verifyModuleGraph") }
}
