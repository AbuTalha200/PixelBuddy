plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.pixelbuddy.ai"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pixelbuddy.ai"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "0.9.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Signed with the local debug key so the release APK installs directly.
            // Replace with your own signing config before publishing to a store.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
