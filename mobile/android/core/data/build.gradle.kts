plugins {
    alias(libs.plugins.lpm.android.library)
    alias(libs.plugins.lpm.hilt)
}

dependencies {
    api(projects.core.model)
    api(projects.core.protocol)
    api(projects.core.demo)
    api(libs.kotlinx.coroutines.android)
    api(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)

    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.okhttp.tls)
}
