import com.android.build.api.variant.UnitTest

plugins {
    id("awn.android-library")
    id("awn.compose")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

android { namespace = "app.awn.quran.feature.hifz" }

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
    // The practice bar drives the recite controller and the audio player.
    implementation(project(":feature:recite"))
    implementation(project(":player"))
    implementation(project(":tracker"))

    implementation(libs.kotlinx.coroutinesCore)
    implementation(libs.androidx.roomRuntime)
    implementation(libs.androidx.roomKtx)
    ksp(libs.androidx.roomCompiler)
    implementation(libs.androidx.activityCompose)
    implementation(libs.androidx.lifecycleRuntimeCompose)
    implementation(libs.androidx.coreKtx)

    testImplementation(libs.kotlinx.coroutinesTest)
    testImplementation(libs.androidx.roomTesting)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.testExtJunit)
    testImplementation(libs.compose.uiTestJunit4)
    testImplementation(libs.androidx.espressoCore)
    // Registers the activity that the Compose test rule launches under Robolectric.
    testImplementation(libs.compose.uiTestManifest)
}
