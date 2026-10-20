plugins {
    id("awn.android-library")
    id("awn.compose")
}

android { namespace = "app.awn.quran.feature.recite" }

dependencies { implementation(project(":core:ui")) }
