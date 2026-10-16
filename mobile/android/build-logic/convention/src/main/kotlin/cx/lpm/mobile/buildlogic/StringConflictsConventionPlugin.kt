package cx.lpm.mobile.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.register

/** Applied to the root project: `check` fails on a string resource two modules define differently. */
class StringConflictsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "base")
            val checkStrings = tasks.register<StringConflictsTask>("checkStringConflicts") {
                subprojects.forEach { module ->
                    stringFiles.from(
                        module.fileTree("src") {
                            include("*/res/values*/strings.xml")
                        }
                    )
                }
                report.set(layout.buildDirectory.file("reports/string-conflicts.txt"))
                rootDirectory.set(layout.projectDirectory)
            }
            tasks.named("check") { dependsOn(checkStrings) }
        }
    }
}
