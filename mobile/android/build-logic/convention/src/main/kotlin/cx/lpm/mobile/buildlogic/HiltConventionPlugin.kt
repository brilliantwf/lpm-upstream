package cx.lpm.mobile.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies

class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = libs.pluginId("ksp"))
            dependencies {
                "ksp"(libs.library("hilt-compiler"))
            }
            pluginManager.withPlugin(libs.pluginId("kotlin-jvm")) {
                dependencies {
                    "implementation"(libs.library("hilt-core"))
                }
            }
            pluginManager.withPlugin("com.android.base") {
                apply(plugin = libs.pluginId("hilt"))
                dependencies {
                    "implementation"(libs.library("hilt-android"))
                    "testImplementation"(libs.library("hilt-android-testing"))
                    "kspTest"(libs.library("hilt-compiler"))
                }
            }
        }
    }
}
