plugins {
    id("awn.android-library")
    id("awn.compose")
}

android {
    namespace = "app.awn.quran.feature.listen"
}

dependencies {
    api(project(":player"))
    implementation(project(":core:ui"))
    implementation(libs.androidx.datastorePreferences)
    implementation(libs.androidx.activityCompose)
    implementation(libs.androidx.lifecycleViewmodelCompose)
    implementation(libs.androidx.lifecycleRuntimeCompose)

    testImplementation(libs.kotlinx.coroutinesTest)
}
