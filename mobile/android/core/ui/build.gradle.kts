plugins {
    alias(libs.plugins.lpm.android.library)
    alias(libs.plugins.lpm.android.compose)
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.designsystem)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
