plugins {
    id("awn.android-library")
}

android {
    namespace = "app.awn.quran.player"
}

dependencies {
    api(project(":core:data"))
    api(libs.kotlinx.coroutinesCore)
    // The app passes in one shared OkHttpClient, so the type is part of this module's surface.
    api(libs.okhttp)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.datasourceOkhttp)
    implementation(libs.kotlinx.coroutinesAndroid)

    testImplementation(libs.kotlinx.coroutinesTest)
}
