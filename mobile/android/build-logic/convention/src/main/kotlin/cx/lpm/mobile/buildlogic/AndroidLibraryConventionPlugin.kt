package cx.lpm.mobile.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = libs.pluginId("android-library"))
            apply(plugin = libs.pluginId("lpm-spotless"))
            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
                namespace = "cx.lpm.mobile" + path.replace(':', '.').replace('-', '_')
                testOptions.targetSdk = Sdk.TARGET
                lint.targetSdk = Sdk.TARGET
            }
        }
    }
}
