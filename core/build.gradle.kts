plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// `core` is deliberately plain Kotlin/JVM: the vault model, the Markdown
// parsing, the GESH client and the sync engine never touch an Android API, so
// they compile and test without an SDK — and could be shared with a desktop or
// server build unchanged.
// Targeted rather than toolchained, so the module builds on any JDK 17 or
// newer without provisioning a second one.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll("-Xjvm-default=all", "-Xjdk-release=17")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)
    api(libs.okhttp)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnit()
    testLogging { events("passed", "failed", "skipped") }
}
