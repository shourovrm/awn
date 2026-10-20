plugins {
    id("awn.android-library")
    id("awn.compose")
}

android {
    namespace = "app.awn.quran.feature.listen"
    // Third-party libraries are listed as explicit coordinates here, not in the shared catalog.
    lint { disable += "UseTomlInstead" }
}

dependencies {
    api(project(":player"))
    implementation(project(":core:ui"))
    implementation(libs.androidx.datastorePreferences)
    implementation(libs.androidx.lifecycleViewmodelCompose)
    implementation(libs.androidx.lifecycleRuntimeCompose)

    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
