plugins {
    id("awn.android-library")
}

android {
    namespace = "app.awn.quran.player"
    // Third-party libraries are listed as explicit coordinates here, not in the shared catalog.
    lint { disable += "UseTomlInstead" }
}

dependencies {
    api(project(":core:data"))
    api(libs.kotlinx.coroutinesCore)
    // The app passes in one shared OkHttpClient, so the type is part of this module's surface.
    api("com.squareup.okhttp3:okhttp:5.4.0")
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation("androidx.media3:media3-datasource-okhttp:1.11.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
