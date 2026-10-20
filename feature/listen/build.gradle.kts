plugins {
    id("awn.android-library")
    id("awn.compose")
}

android { namespace = "app.awn.quran.feature.listen" }

dependencies { implementation(project(":core:ui")) }
