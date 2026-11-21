plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.kapt")
}

// CI run number keeps every signed build installable as an update (V10.1 = versionCode 11).
val ciRunNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()

android {
    namespace = "com.moraswood.qande"
    compileSdk = 35

    defaultConfig {
        // Same application ID as the installed Q&E V10.1 app so the signed native build updates it in place.
        applicationId = "com.moraswood.qande"
        minSdk = 26
        targetSdk = 35
        versionCode = if (ciRunNumber != null) 100 + ciRunNumber else 12
        versionName = "10.1-native" + (ciRunNumber?.let { ".$it" } ?: "")
    }

    // Same permanent signing pattern as .github/workflows/build-qe-android.yml (alias moras-qe).
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

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

kapt {
    correctErrorTypes = true
    arguments { arg("room.schemaLocation", "$projectDir/schemas") }
}

/**
 * Copies the V10.1 letterhead logo (repo-root app/src/main/assets/mwi-logo.png, same file the
 * WebView app used) into a generated drawable resource `R.drawable.mwi_logo`.
 * The WebView assets themselves (app.js, index.html, CSS) are NOT packaged in this APK.
 */
abstract class CopyMwiLogo : DefaultTask() {
    @get:InputFile abstract val source: RegularFileProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty
    @TaskAction fun copyLogo() {
        val target = outputDir.get().file("drawable-nodpi/mwi_logo.png").asFile
        target.parentFile.mkdirs()
        source.get().asFile.copyTo(target, overwrite = true)
    }
}

val copyMwiLogo = tasks.register<CopyMwiLogo>("copyMwiLogo") {
    source.set(rootProject.layout.projectDirectory.file("../app/src/main/assets/mwi-logo.png"))
    outputDir.set(layout.buildDirectory.dir("generated/mwiLogo"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(copyMwiLogo, CopyMwiLogo::outputDir)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    testImplementation("junit:junit:4.13.2")
}
