import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

val versionProperties = Properties().apply {
    rootProject.file("version.properties").inputStream().use(::load)
}

val releaseKeystore = providers.environmentVariable("SKALD_ANDROID_KEYSTORE_PATH")
val releaseStorePassword = providers.environmentVariable("SKALD_ANDROID_STORE_PASSWORD")

android {
    namespace = "no.vardir.skald"
    compileSdk = 35

    defaultConfig {
        applicationId = "no.vardir.skald"
        minSdk = 26
        targetSdk = 35
        versionCode = versionProperties.getProperty("VERSION_CODE").toInt()
        versionName = versionProperties.getProperty("VERSION_NAME")
    }

    signingConfigs {
        create("release") {
            if (releaseKeystore.isPresent) {
                storeFile = file(releaseKeystore.get())
                storePassword = releaseStorePassword.orNull
                keyAlias = "skald"
                // The Skald release keystore is PKCS12. PKCS12 uses the store
                // password for the private-key entry as well.
                keyPassword = releaseStorePassword.orNull
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".dev"
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
