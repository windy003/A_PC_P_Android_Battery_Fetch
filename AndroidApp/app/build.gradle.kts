plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.batterymonitor"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.batterymonitor"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "2026/7/25-1"
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
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
}
