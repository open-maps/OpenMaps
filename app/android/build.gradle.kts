plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    namespace = "nz.eloque.openmaps"
    compileSdk = libs.versions.compileSdk.get().toInt()
    buildToolsVersion = libs.versions.buildTools.get()

    defaultConfig {
        applicationId = "nz.eloque.openmaps"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = (property("openmaps.versionCode") as String).toInt()
        versionName = property("openmaps.versionName") as String
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getProperty("user.home") + "/work/_temp/keystore.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            keyAlias = System.getenv("SIGNING_KEY_ALIAS")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs["release"]
        }
        debug {
            applicationIdSuffix = ".dev"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // MapLibre's native library is about 10 MB per CPU type, so releases get one APK per type next to a universal one.
    // Debug builds stay a single APK.
    splits {
        abi {
            isEnable = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    // AGP must not re-strip native libraries such as MapLibre's, or builds stop being reproducible.
    packaging {
        jniLibs {
            keepDebugSymbols += "**/*.so"
        }
    }
}

dependencies {
    implementation(project(":ui"))
    implementation(project(":core:model"))
    implementation(project(":core:platform"))
    implementation(project(":data:transit"))
    implementation(project(":data:geocoding"))
    implementation(project(":data:routing"))
    implementation(project(":data:storage"))
    implementation(project(":data:fuel"))
    implementation(project(":data:weather"))
    implementation(project(":data:reviews"))
    implementation(project(":data:flights"))
    implementation(project(":map"))
    implementation(libs.maplibre.compose)
    implementation(libs.androidx.car.app)
    implementation(libs.androidx.core)
    implementation(libs.androidx.car.app.projected)
    implementation(libs.compose.foundation)
    implementation(libs.nav3.ui)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.compose.runtime)
    implementation(libs.koin.android)
    implementation(libs.okhttp)
    testImplementation(kotlin("test-junit"))
}
