plugins {
    alias(libs.plugins.lpm.android.library)
    alias(libs.plugins.lpm.hilt)
}

dependencies {
    implementation(projects.core.data)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.process)

    testImplementation(projects.core.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
