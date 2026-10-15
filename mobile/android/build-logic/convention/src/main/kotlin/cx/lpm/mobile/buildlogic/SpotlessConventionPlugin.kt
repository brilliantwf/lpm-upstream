package cx.lpm.mobile.buildlogic

import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

/**
 * ktlint over a module's own sources only: walking build/ races with tasks that
 * generate code into it. The root project also covers build-logic.
 */
class SpotlessConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = libs.pluginId("spotless"))
            val ktlintVersion = libs.findVersion("ktlint").get().requiredVersion
            val composeRules =
                "io.nlopez.compose.rules:ktlint:" +
                    libs.findVersion("composeRulesKtlint").get().requiredVersion
            val isRoot = this == rootProject
            extensions.configure<SpotlessExtension> {
                kotlin {
                    target(if (isRoot) "build-logic/convention/src/**/*.kt" else "src/**/*.kt")
                    ktlint(ktlintVersion).customRuleSets(listOf(composeRules))
                }
                kotlinGradle {
                    target(
                        if (isRoot) {
                            listOf(
                                "*.kts",
                                "build-logic/*.kts",
                                "build-logic/convention/*.kts"
                            )
                        } else {
                            listOf("*.kts")
                        }
                    )
                    ktlint(ktlintVersion)
                }
            }
        }
    }
}
