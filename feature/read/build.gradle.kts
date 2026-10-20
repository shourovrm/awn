plugins {
    id("awn.android-library")
    id("awn.compose")
}

android { namespace = "app.awn.quran.feature.read" }

dependencies { implementation(project(":core:ui")) }
