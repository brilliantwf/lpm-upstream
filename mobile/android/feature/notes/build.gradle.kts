plugins {
    alias(libs.plugins.lpm.android.feature)
}

dependencies {
    implementation(libs.androidx.activity.compose)

    testImplementation(projects.core.testing)
}
