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
        versionCode = 3
        versionName = "0.3.0"
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
    implementation("androidx.work:work-runtime:2.11.2")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}
