plugins {
    id("com.android.application")
}

android {
    namespace = "com.focus.tracker"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.focus.tracker"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    // lite: time + pill via usage access. No accessibility service, so Play
    //       Protect lets the sideloaded APK install. The default download.
    // full: adds Reels/Shorts counting, which needs an accessibility service;
    //       Play Protect blocks sideloading it (install with adb instead).
    flavorDimensions += "mode"
    productFlavors {
        create("lite") { dimension = "mode" }
        create("full") {
            dimension = "mode"
            versionNameSuffix = "-full"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
