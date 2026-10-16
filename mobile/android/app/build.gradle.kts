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
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.core.navigation)
    implementation(projects.core.tailnet)
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
