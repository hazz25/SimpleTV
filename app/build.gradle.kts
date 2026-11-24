plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// The build number comes from GitHub Actions so every new APK installs over the previous one.
val buildNumber = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1

android {
    namespace = "com.simpletv.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.simpletv.app"
        minSdk = 21
        targetSdk = 34
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    // Releases are signed with a private key. The key file is stored encrypted in the repository
    // and the build decrypts it with the SIGNING_KEY secret, which only GitHub knows.
    val signingKey: String? = System.getenv("SIGNING_KEY")?.takeIf { it.isNotBlank() }
    signingConfigs {
        create("release") {
            if (signingKey != null) {
                storeFile = file("../keystore/simpletv.jks")
                storePassword = signingKey
                keyAlias = "simpletv"
                keyPassword = signingKey
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (signingKey != null) signingConfigs.getByName("release") else null
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
        buildConfig = true
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    implementation("io.coil-kt:coil:2.7.0")
}
