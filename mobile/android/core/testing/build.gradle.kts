plugins {
    alias(libs.plugins.lpm.android.library)
}

dependencies {
    api(projects.core.data)
    api(projects.core.tailnet)
    api(libs.junit4)
    api(libs.kotlinx.coroutines.test)
}
