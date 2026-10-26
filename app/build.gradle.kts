plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.mowin.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.mowin.app"
        minSdk = 26
        // 28 on purpose: Android 10+ blocks execve on files in app-writable storage
        // for apps targeting API 29+. PRoot needs that. Sideload-only PoC.
        targetSdk = 28
        versionCode = 1
        versionName = "0.1.0-poc"

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            // Native libs must be extracted so proot and friends can be exec'd
            // from applicationInfo.nativeLibraryDir.
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("org.tukaani:xz:1.10")
    implementation("androidx.compose.material3:material3")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
