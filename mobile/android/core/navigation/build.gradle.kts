plugins {
    alias(libs.plugins.lpm.android.library)
    alias(libs.plugins.lpm.android.compose)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.core.model)
    api(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
}
