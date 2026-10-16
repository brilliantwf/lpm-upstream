plugins {
    alias(libs.plugins.lpm.jvm.library)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
}

// Demo Mode's sample files (mobile/shared/demo), the same ones the iOS demo bundles.
sourceSets.named("main") {
    resources.srcDir(rootProject.file("../shared/demo"))
}
