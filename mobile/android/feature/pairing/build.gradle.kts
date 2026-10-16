plugins {
    alias(libs.plugins.lpm.android.feature)
}

dependencies {
    implementation(projects.core.tailnet)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.compose)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.mlkit.vision)
    implementation(libs.mlkit.barcode.scanning)

    testImplementation(projects.core.testing)
}
