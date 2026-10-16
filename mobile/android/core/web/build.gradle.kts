plugins {
    alias(libs.plugins.lpm.android.library)
    alias(libs.plugins.lpm.android.compose)
}

android {
    sourceSets {
        // mobile/shared holds web/ (xterm.js terminal, markdown renderer), the
        // same pages the iOS app bundles, served to the WebView from assets/web.
        getByName("main") {
            assets.directories.add("../../../shared")
        }
    }
    // shared/demo is Demo Mode's sample data, which :core:demo carries as resources.
    androidResources {
        ignoreAssetsPatterns += "<dir>demo"
    }
}

dependencies {
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.ui)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
}
