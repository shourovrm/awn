plugins {
    id("awn.android-library")
    id("awn.compose")
}

android {
    namespace = "app.awn.quran.feature.read"
    defaultConfig { testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
}

dependencies {
    implementation(project(":core:ui"))
    // Repository interfaces and fakes appear in this module's public classes, so they are api.
    api(project(":core:data"))
    // The page fonts and print scripts appear in the public signatures of this module.
    api(project(":core:quranfont"))
    api(project(":player"))
    // WordState, the colour state of a word, is defined by the tracker.
    api(project(":tracker"))

    implementation(libs.androidx.datastorePreferences)
    implementation(libs.androidx.activityCompose)
    implementation(libs.androidx.lifecycleViewmodelCompose)
    implementation(libs.androidx.lifecycleRuntimeCompose)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.testExtJunit)
    testImplementation(libs.compose.uiTestJunit4)
    // Supplies the activity that Robolectric Compose tests launch.
    testImplementation(libs.compose.uiTestManifest)
    testImplementation(libs.kotlinx.coroutinesTest)

    androidTestImplementation(libs.compose.uiTestJunit4)
    androidTestImplementation(libs.androidx.testExtJunit)
    androidTestImplementation(libs.androidx.testRunner)
    androidTestImplementation(libs.compose.uiTestManifest)
}
