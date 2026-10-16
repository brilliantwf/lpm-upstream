import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "cx.lpm.mobile.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.spotless.gradlePlugin)
}

tasks {
    validatePlugins {
        enableStricterValidation = true
        failOnWarning = true
    }
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = libs.plugins.lpm.android.application.get().pluginId
            implementationClass = "cx.lpm.mobile.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = libs.plugins.lpm.android.library.get().pluginId
            implementationClass = "cx.lpm.mobile.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = libs.plugins.lpm.android.compose.get().pluginId
            implementationClass = "cx.lpm.mobile.buildlogic.AndroidComposeConventionPlugin"
        }
        register("androidFeature") {
            id = libs.plugins.lpm.android.feature.get().pluginId
            implementationClass = "cx.lpm.mobile.buildlogic.AndroidFeatureConventionPlugin"
        }
        register("hilt") {
            id = libs.plugins.lpm.hilt.get().pluginId
            implementationClass = "cx.lpm.mobile.buildlogic.HiltConventionPlugin"
        }
        register("spotless") {
            id = libs.plugins.lpm.spotless.get().pluginId
            implementationClass = "cx.lpm.mobile.buildlogic.SpotlessConventionPlugin"
        }
        register("stringConflicts") {
            id = libs.plugins.lpm.string.conflicts.get().pluginId
            implementationClass = "cx.lpm.mobile.buildlogic.StringConflictsConventionPlugin"
        }
        register("jvmLibrary") {
            id = libs.plugins.lpm.jvm.library.get().pluginId
            implementationClass = "cx.lpm.mobile.buildlogic.JvmLibraryConventionPlugin"
        }
    }
}
