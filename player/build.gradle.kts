plugins {
    id("awn.android-library")
}

android { namespace = "app.awn.quran.player" }

dependencies {
    implementation(libs.media3.exoplayer)
}
