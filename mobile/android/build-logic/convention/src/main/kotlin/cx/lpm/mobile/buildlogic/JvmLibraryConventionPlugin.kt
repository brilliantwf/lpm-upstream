package cx.lpm.mobile.buildlogic

import com.android.build.api.dsl.Lint
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = libs.pluginId("kotlin-jvm"))
            apply(plugin = "com.android.lint")
            apply(plugin = libs.pluginId("lpm-spotless"))
            configureKotlinJvm()
            extensions.configure<Lint> { configureLint() }
        }
    }
}
