plugins {
    id("com.android.application")
}

android {
    namespace = "dev.polisms.filter"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.polisms.filter"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "0.2.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(files("libs/libgmbridge.aar"))

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}
