plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.moraswood.qande"
    compileSdk = 35

    val signingPath = System.getenv("QE_SIGNING_STORE_FILE")
    val signingStorePassword = System.getenv("QE_SIGNING_STORE_PASSWORD")
    val signingKeyAlias = System.getenv("QE_SIGNING_KEY_ALIAS")
    val signingKeyPassword = System.getenv("QE_SIGNING_KEY_PASSWORD")
    val hasReleaseSigning = listOf(signingPath, signingStorePassword, signingKeyAlias, signingKeyPassword).all { !it.isNullOrBlank() }

    signingConfigs {
        if (hasReleaseSigning) {
            create("qeRelease") {
                storeFile = file(signingPath!!)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "com.moraswood.qande"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.plus(10) ?: 10)
        versionName = "10.0." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("qeRelease")
            }
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
