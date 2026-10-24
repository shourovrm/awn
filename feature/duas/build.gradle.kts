plugins {
    id("awn.android-library")
    id("awn.compose")
}

android { namespace = "app.awn.quran.feature.duas" }

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:data"))
    // Dua cards play the ayah, check a recitation and read word timings.
    implementation(project(":player"))
    implementation(project(":feature:recite"))
    // Dua cards draw their words with the Mushaf line view and open its word card.
    implementation(project(":feature:read"))
    implementation(project(":tracker"))

    implementation(libs.kotlinx.coroutinesCore)
    implementation(libs.androidx.datastorePreferences)
    implementation(libs.androidx.activityCompose)
    implementation(libs.androidx.lifecycleViewmodelCompose)
    implementation(libs.androidx.lifecycleRuntimeCompose)
    implementation(libs.androidx.coreKtx)

    testImplementation(libs.kotlinx.coroutinesTest)
    testImplementation(libs.robolectric)
    // Back closes the layers menu in screen tests.
    testImplementation(libs.androidx.espressoCore)
    testImplementation(libs.androidx.testExtJunit)
    testImplementation(libs.compose.uiTestJunit4)
    // Supplies the activity that Robolectric Compose tests launch.
    testImplementation(libs.compose.uiTestManifest)
}
