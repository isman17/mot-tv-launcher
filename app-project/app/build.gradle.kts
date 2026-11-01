plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Permanent project signing key (created 2026-11-01 after the ephemeral
// auto-generated debug keys changed between build sessions and caused
// "package conflicts" update failures on the user's TV). The keystore lives
// in the workspace (~/workspace/smart-tv-launcher/keystore/) so it survives
// VM restarts; ALL builds from 0.3.0-fixed onward use it.
//
// The keystore is NOT committed to git (private signing key). Public clones
// therefore don't have it: the "mot" signing config is only created and
// applied when the keystore + properties are actually present; otherwise
// the build silently falls back to Gradle's default debug signing so a
// fresh clone still builds and runs.
val ksStoreFile = rootProject.file("../keystore/mot-launcher.keystore")
val ksMap = mutableMapOf<String, String>()
val ksFile = rootProject.file("../keystore/keystore.properties")
if (ksFile.exists()) {
    ksFile.forEachLine { line ->
        val parts = line.split("=", limit = 2)
        if (parts.size == 2) ksMap[parts[0].trim()] = parts[1].trim()
    }
}
val hasMotKey = ksStoreFile.exists() && ksFile.exists()

android {
    namespace = "com.mottv.launcher"
    compileSdk = 34

    signingConfigs {
        if (hasMotKey) {
            create("mot") {
                storeFile = ksStoreFile
                storePassword = ksMap["storePassword"]
                keyAlias = ksMap["keyAlias"]
                keyPassword = ksMap["keyPassword"]
            }
        }
    }

    defaultConfig {
        applicationId = "com.mottv.launcher"
        minSdk = 26
        targetSdk = 34
        versionCode = 10
        versionName = "0.6.1"
    }

    buildTypes {
        debug {
            if (hasMotKey) signingConfig = signingConfigs.getByName("mot")
        }
        release {
            if (hasMotKey) signingConfig = signingConfigs.getByName("mot")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

// Dependencies are resolved POM-only on this VM (no Gradle module metadata),
// which loses Kotlin stdlib alignment: old kotlin-stdlib-jdk7/jdk8 (1.6.21,
// pulled transitively by androidx POMs) duplicate classes already inside
// kotlin-stdlib 2.0.20. Exclude them; the 2.0.20 stdlib contains those classes.
configurations.configureEach {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.leanback:leanback:1.0.0")
    implementation("androidx.core:core-ktx:1.13.1")
    // Phase 2: Media3 player (frame 5). 1.4.1 is the newest line whose AAR
    // metadata accepts compileSdk 34 (1.5.1 demands compileSdk 35 — verified
    // by checkDebugAarMetadata failure), matching this project's toolchain.
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
}
