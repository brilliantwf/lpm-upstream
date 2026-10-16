plugins {
    alias(libs.plugins.lpm.android.feature)
}

dependencies {
    implementation(projects.core.web)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    testImplementation(projects.core.testing)
}
