plugins {
    alias(libs.plugins.lpm.android.application)
    alias(libs.plugins.lpm.android.compose)
    alias(libs.plugins.lpm.hilt)
}

android {
    namespace = "cx.lpm.mobile"

    defaultConfig {
        applicationId = "cx.lpm.mobile"
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    sourceSets {
        // mobile/shared holds web/ (xterm.js terminal, markdown renderer), the
        // same pages the iOS app bundles, so the WebView loads them from assets/web.
        getByName("main") {
            assets.directories.add("../../shared")
        }
    }
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(libs.androidx.activity.compose)
}
