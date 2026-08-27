plugins {
    id("com.android.application")
}

android {
    namespace = "dev.miniwx"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.miniwx"
        minSdk = 28
        targetSdk = 36
        versionCode = 8
        versionName = "0.7.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")
    implementation("org.luckypray:dexkit:2.2.0")
}
