plugins {
    id("awn.android-library")
    id("awn.compose")
}

android {
    namespace = "app.awn.quran.feature.recite"

    // Two test-only libraries are declared by coordinate because the shared version catalog is
    // owned by the maintainer; they move to the catalog when it is next edited.
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:data"))
    implementation(project(":tracker"))
    implementation(project(":player"))

    implementation(libs.kotlinx.coroutinesCore)
    implementation(libs.androidx.datastorePreferences)
    implementation(libs.androidx.activityCompose)
    implementation(libs.androidx.lifecycleRuntimeCompose)
    implementation(libs.androidx.coreKtx)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.testExtJunit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.uiTestJunit4)
    // Test dispatcher for the session loop; the version catalog has no entry for it.
    testImplementation(libs.kotlinx.coroutinesTest)
    // The Espresso that Compose tests pull in by default calls an InputManager method that Android
    // 16 removed.
    testImplementation(libs.androidx.espressoCore)
    // Declares the ComponentActivity that the Robolectric Compose rule launches.
    testImplementation(libs.compose.uiTestManifest)
}
