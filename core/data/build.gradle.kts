// :core:data — Android Library. Room database, DAOs, repository implementations.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.healthtrend.core.data"
    compileSdk = 36

    defaultConfig {
        minSdk = 35
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Room migration tests read the exported schemas from androidTest assets.
    sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// AGENTS.md 4.5: exportSchema = true -> JSON schemas are versioned in VCS.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core:domain"))
    implementation(libs.kotlinx.coroutines.android)

    // `api`, not `implementation`: `HealthTrendDatabase` is public API of this module and extends
    // `RoomDatabase`, so every consumer must have Room on its compile classpath to resolve it.
    api(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // JVM unit tests: Robolectric supplies the Android runtime so Room can run in-memory.
    testImplementation(libs.junit4)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
