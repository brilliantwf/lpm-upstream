plugins {
    alias(libs.plugins.lpm.android.feature)
}

dependencies {
    implementation(projects.core.web)
    implementation(libs.androidx.activity.compose)

    testImplementation(projects.core.testing)
}
