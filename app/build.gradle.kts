plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.p2pchat.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.p2pchat.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 10
        versionName = "1.0.10"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystorePath = rootProject.file("p2pchat.keystore")
            if (keystorePath.exists()) {
                storeFile = keystorePath
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "4daptBXz0Hp6z8kxcsDhvQVx"
                keyAlias = System.getenv("KEY_ALIAS") ?: "ciphervault"
                keyPassword = System.getenv("KEY_PASSWORD") ?: "4daptBXz0Hp6z8kxcsDhvQVx"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release").takeIf {
                it.storeFile?.exists() == true
            } ?: signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.webkit:webkit:1.9.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.camera:camera-core:1.3.1")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
    implementation("com.google.android.material:material:1.11.0")
}
