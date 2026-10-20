import com.android.build.api.variant.UnitTest

plugins {
    id("awn.android-library")
    id("awn.compose")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

android {
    namespace = "app.awn.quran.feature.learn"

    // The two test coordinates below are written out here on purpose, so that this module adds no
    // line to the shared version catalog while other modules are built in parallel.
}

room { schemaDirectory("$projectDir/schemas") }

// Room's exported schema JSON files are what the migration test opens as "the old database".
androidComponents {
    onVariants { variant ->
        variant.nestedComponents.filterIsInstance<UnitTest>().forEach { unitTest ->
            unitTest.sources.assets?.addStaticSourceDirectory("$projectDir/schemas")
        }
    }
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:data"))

    implementation(libs.kotlinx.coroutinesCore)
    implementation(libs.androidx.roomRuntime)
    implementation(libs.androidx.roomKtx)
    ksp(libs.androidx.roomCompiler)
    implementation(libs.androidx.datastorePreferences)
    implementation(libs.androidx.activityCompose)

    testImplementation(libs.kotlinx.coroutinesTest)
    testImplementation(libs.androidx.roomTesting)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.testExtJunit)
    testImplementation(libs.compose.uiTestJunit4)
    // Registers the activity that the Compose test rule launches under Robolectric.
    testImplementation(libs.compose.uiTestManifest)
}
