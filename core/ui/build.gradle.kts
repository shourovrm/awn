plugins {
    id("awn.android-library")
    id("awn.compose")
}

android { namespace = "app.awn.quran.core.ui" }

dependencies {
    // Feature modules and the app get Compose through this module, so they all see one version.
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.foundation)
    api(libs.compose.material3)

    // WindowCompat sets the status-bar and navigation-bar icon colours.
    implementation(libs.androidx.coreKtx)
}

// The design and copy gates scan every module's sources, which Gradle does not see as inputs of
// this module's tests; without this a cached pass hid a gate failure that CI then caught.
tasks.withType<Test>().configureEach { outputs.upToDateWhen { false } }
