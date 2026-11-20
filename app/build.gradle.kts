plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.moraswood.qande"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.moraswood.qande"
        minSdk = 26
        targetSdk = 35
        versionCode = 11
        versionName = "10.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (!System.getenv("QE_KEYSTORE_PATH").isNullOrBlank()) {
            create("qePermanent") {
                storeFile = file(System.getenv("QE_KEYSTORE_PATH"))
                storePassword = System.getenv("QE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("QE_KEY_ALIAS") ?: "moras-qe"
                keyPassword = System.getenv("QE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("qePermanent")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.webkit:webkit:1.12.1")
}
