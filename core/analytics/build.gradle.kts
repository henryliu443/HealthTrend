// :core:analytics — Pure Kotlin (JVM). Domain-agnostic math / time-series engine.
// HARD RULE: zero Android, zero business vocabulary (no Food/Weight/UricAcid...).
// Only RawDataPoint / TimeSeries in, pure math out.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core:domain"))
    implementation(libs.apache.commons.math3)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.test {
    useJUnitPlatform()
}
