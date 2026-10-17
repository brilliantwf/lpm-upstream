plugins {
    alias(libs.plugins.lpm.android.application)
    alias(libs.plugins.lpm.android.compose)
    alias(libs.plugins.lpm.hilt)
}

// FCM's client config, from the Firebase console (Project settings → Your apps).
// It isn't in the repo: a build without it runs with no push, and only the Play
// bundle insists on it.
val firebase = firebaseResources(
    providers.fileContents(layout.projectDirectory.file("google-services.json")).asText.orNull,
    packageName = "cx.lpm.mobile"
)

android {
    namespace = "cx.lpm.mobile"

    buildFeatures {
        resValues = true
    }

    defaultConfig {
        applicationId = "cx.lpm.mobile"
        versionCode = 1
        versionName = "0.1"
        // The values the google-services plugin would generate, read by Firebase at launch.
        firebase.forEach { (name, value) -> resValue("string", name, value) }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
}

val hasFirebase = firebase.isNotEmpty()
tasks.matching { it.name == "bundleRelease" }.configureEach {
    // A local copy: the configuration cache can't keep a reference to this script.
    val present = hasFirebase
    doFirst {
        check(present) {
            "app/google-services.json is missing: a Play build without it has no push notifications"
        }
    }
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.push)
    implementation(projects.core.designsystem)
    implementation(projects.core.navigation)
    implementation(projects.core.tailnet)
    implementation(projects.core.ui)
    implementation(projects.core.web)
    implementation(projects.feature.actions)
    implementation(projects.feature.activity)
    implementation(projects.feature.automations)
    implementation(projects.feature.claudeaccount)
    implementation(projects.feature.config)
    implementation(projects.feature.connection)
    implementation(projects.feature.files)
    implementation(projects.feature.git)
    implementation(projects.feature.mac)
    implementation(projects.feature.memory)
    implementation(projects.feature.notes)
    implementation(projects.feature.pairing)
    implementation(projects.feature.projects)
    implementation(projects.feature.settings)
    implementation(projects.feature.speech)
    implementation(projects.feature.terminal)
    implementation(projects.feature.usage)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.ui)
}

@Suppress("UNCHECKED_CAST")
fun firebaseResources(json: String?, packageName: String): Map<String, String> {
    if (json == null) return emptyMap()
    val root = groovy.json.JsonSlurper().parseText(json) as Map<String, Any?>
    val project = root["project_info"] as Map<String, Any?>
    val client = (root["client"] as List<Map<String, Any?>>).firstOrNull {
        val info = it["client_info"] as Map<String, Any?>
        (info["android_client_info"] as Map<String, Any?>)["package_name"] == packageName
    } ?: error("google-services.json has no Android app for $packageName")
    val apiKey = (client["api_key"] as List<Map<String, Any?>>).first()["current_key"] as String
    return listOfNotNull(
        "google_app_id" to
            (client["client_info"] as Map<String, Any?>)["mobilesdk_app_id"] as String,
        "google_api_key" to apiKey,
        "gcm_defaultSenderId" to project["project_number"] as String,
        "project_id" to project["project_id"] as String,
        (project["storage_bucket"] as String?)?.let { "google_storage_bucket" to it }
    ).toMap()
}
