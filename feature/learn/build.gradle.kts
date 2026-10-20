plugins {
    id("awn.android-library")
    id("awn.compose")
}

android { namespace = "app.awn.quran.feature.learn" }

dependencies { implementation(project(":core:ui")) }
