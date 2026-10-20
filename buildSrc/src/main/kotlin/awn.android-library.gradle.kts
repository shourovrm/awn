import com.android.build.api.dsl.LibraryExtension

// Shared settings for every Android library module. Module build files set only the namespace
// and their own dependencies.
plugins {
    id("com.android.library")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

extensions.configure<LibraryExtension> {
    compileSdk = 37

    defaultConfig { minSdk = 26 }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // No debug build is used anywhere; unit and instrumented tests run on the release variant.
    testBuildType = "release"

    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Robolectric's Android 16 sandbox reaches into JDK internals on Java 21.
        unitTests.all {
            it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
        }
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        // These checks query the network for newer versions, which makes the build result depend
        // on the day it runs. Dependabot reports newer versions instead.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
}

dependencies {
    add("testImplementation", libs.findLibrary("junit").get())
}
