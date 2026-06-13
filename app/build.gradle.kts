plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.notifbypass"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.notifbypass"
        minSdk = 26          // Android 8.0 — needed for VibrationEffect amplitudes
        targetSdk = 34       // Android 14
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            // Populated from environment variables in CI (see .github/workflows).
            // When KEYSTORE_FILE is absent (e.g. local builds with no keystore),
            // the release build falls back to the debug key — see buildTypes below.
            val keystorePath = System.getenv("KEYSTORE_FILE")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Use the real release keystore when provided; otherwise fall back to
            // the auto-generated debug key so the APK is always installable.
            signingConfig = if (System.getenv("KEYSTORE_FILE") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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
        // Don't let a non-critical lint check fail the automated release build.
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
