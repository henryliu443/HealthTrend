// :core:domain — Pure Kotlin (JVM). Domain models, repository interfaces, NutritionLookupProvider contracts.
// Must stay free of Android and Room dependencies.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core:common"))
    // Flow appears in Repository interfaces -> must be part of the public API.
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.kotest.assertions)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.test {
    useJUnitPlatform()
}
