package cx.lpm.mobile.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies

class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = libs.pluginId("kotlin-compose"))
            (extensions.getByName("android") as CommonExtension).buildFeatures.compose = true
            dependencies {
                val bom = platform(libs.library("androidx-compose-bom"))
                "implementation"(bom)
                "testImplementation"(bom)
                "implementation"(libs.library("androidx-compose-ui-tooling-preview"))
                "debugImplementation"(libs.library("androidx-compose-ui-tooling"))
                "debugImplementation"(libs.library("androidx-compose-ui-test-manifest"))
                "testImplementation"(libs.library("androidx-compose-ui-test-junit4"))
                "testImplementation"(libs.library("robolectric"))
                // ui-test still pulls Espresso 3.5, which breaks on API 34+ (InputManager.getInstance).
                "testImplementation"(libs.library("androidx-test-core"))
                "testImplementation"(libs.library("androidx-test-espresso-core"))
            }
        }
    }
}
