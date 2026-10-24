plugins {
    id("awn.android-library")
    id("awn.compose")
}

android { namespace = "app.awn.quran.core.quranfont" }

dependencies {
    implementation(project(":core:ui"))
    // Repository interfaces appear in the public signatures, so they are api.
    api(project(":core:data"))

    implementation(libs.kotlinx.coroutinesCore)

    testImplementation(libs.kotlinx.coroutinesTest)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.testExtJunit)
    testImplementation(libs.compose.uiTestJunit4)
    // Supplies the activity that Robolectric Compose tests launch.
    testImplementation(libs.compose.uiTestManifest)
}
