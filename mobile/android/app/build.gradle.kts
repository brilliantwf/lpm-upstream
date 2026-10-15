plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "cx.lpm.mobile"
    compileSdk = 37

    defaultConfig {
        applicationId = "cx.lpm.mobile"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"
    }

    sourceSets {
        // mobile/shared holds web/ (xterm.js terminal, markdown renderer), the
        // same pages the iOS app bundles, so the WebView loads them from assets/web.
        getByName("main") {
            assets.directories.add("../../shared")
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
