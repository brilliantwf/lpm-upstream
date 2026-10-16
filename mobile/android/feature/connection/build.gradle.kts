plugins {
    alias(libs.plugins.lpm.android.feature)
}

dependencies {
    implementation(projects.core.ui)
    implementation(projects.core.tailnet)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)

    testImplementation(projects.core.testing)
}
