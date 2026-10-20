plugins {
    id("awn.android-library")
}

android { namespace = "app.awn.quran.player" }

dependencies {
    api(project(":core:data"))
    api(libs.kotlinx.coroutinesCore)
    implementation(libs.media3.exoplayer)
}
