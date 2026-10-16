plugins {
    alias(libs.plugins.lpm.android.library)
    alias(libs.plugins.lpm.hilt)
}

dependencies {
    api(projects.core.data)
}
