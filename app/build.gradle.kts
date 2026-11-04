import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.kino"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.kino"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        // FFmpeg is native code; ARM covers nearly every TV box and phone.
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
    }

    // The app is English-only; drop the dozens of translations Material and Media3 bring along.
    androidResources { localeFilters += "en" }

    // F-Droid rejects AGP's encrypted dependency metadata in the APK signing block.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    // Release signing comes from keystore.properties (git-ignored). Without it, assembleRelease builds unsigned.
    val keys = rootProject.file("keystore.properties").takeIf { it.exists() }
        ?.let { f -> Properties().apply { f.inputStream().use { load(it) } } }
    signingConfigs {
        if (keys != null) create("release") {
            storeFile = file(keys.getProperty("storeFile"))
            storePassword = keys.getProperty("storePassword")
            keyAlias = keys.getProperty("keyAlias")
            keyPassword = keys.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            if (keys != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    packaging {
        resources.excludes += setOf("kotlin/**", "kotlin-tooling-metadata.json", "META-INF/*.version")
        // Compress FFmpeg in the APK: a much smaller download, unpacked once at install.
        jniLibs.useLegacyPackaging = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { buildConfig = true }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-Xno-param-assertions", "-Xno-call-assertions", "-Xno-receiver-assertions")
    }
}

dependencies {
    // Playback, plus Material Components for the widgets. RecyclerView comes with media3-ui.
    val media3 = "1.7.1" // what the FFmpeg build below is compiled against
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    // Software audio for what TV boxes can't decode themselves: DTS, TrueHD, AC3/EAC3. Jellyfin's build of the Media3 extension.
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.6.1+2")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.13.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
