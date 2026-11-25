plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Android version of the English game: the same web game (../english), with Android's own
// English voices and speech recognition.
val buildNumber = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1

android {
    namespace = "com.simpletv.english"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.simpletv.english"
        minSdk = 21
        targetSdk = 34
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    sourceSets {
        getByName("main") {
            assets.setSrcDirs(listOf("../english"))
        }
    }

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
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}
