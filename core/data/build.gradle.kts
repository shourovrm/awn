import com.android.build.api.variant.UnitTest

plugins {
    id("awn.android-library")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

android { namespace = "app.awn.quran.core.data" }

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
    // Flow appears in the public repository interfaces, so consumers need it on their classpath.
    api(libs.kotlinx.coroutinesCore)

    implementation(libs.androidx.roomRuntime)
    implementation(libs.androidx.roomKtx)
    ksp(libs.androidx.roomCompiler)

    testImplementation(libs.androidx.roomTesting)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.testExtJunit)
}
