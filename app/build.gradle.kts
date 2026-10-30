import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    `generate-backendname-objects`
}

fun gitCommand(vararg args: String): String {
    return providers.exec {
        commandLine("git", *args)
    }.standardOutput.asText.get().trim()
}

fun idForSimon(): String {
    return try {
        val mergeBase = gitCommand("merge-base", "simon/main", "main")
        gitCommand("rev-parse", "--short", mergeBase)
    } catch (_: Exception) {
        "UNOFFICIAL"
    }
}

fun issuesURL(): String {
    val originURL = gitCommand("ls-remote", "--get-url", "origin")
    return originURL.replaceFirst(Regex("\\.git/*$"), "") + "/issues"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

android {
    namespace = "io.github.nandomango702.stpuzzlerelay"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.github.nandomango702.stpuzzlerelay"
        minSdk = 21
        targetSdk = 36
        versionCode = 151
        versionName = "0.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resValue("string", "issues_url", issuesURL())
    }

    externalNativeBuild {
        ndkVersion = "29.0.14206865"
        cmake {
            path = File("src/main/jni/CMakeLists.txt")
        }
    }

    buildFeatures {
        viewBinding = true
        compose = true
        buildConfig = true
    }

    lint {
        // The sandbox has no working proxy for lint's network requests.
        checkReleaseBuilds = false
    }

    // Personal release key (cert CN=NandoMango702, baked in at creation).
    // keystore.properties is gitignored and lives next to this file;
    // without it the release build type gets NO signing config and
    // assembles unsigned.
    val keystorePropsFile = rootProject.file("keystore.properties")
    val keystoreProps = Properties()
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { keystoreProps.load(it) }
    }

    signingConfigs {
        // Pinned debug key: debug.keystore sits in the project root on the
        // build machine's persistent storage and is gitignored ON PURPOSE
        // (never committed). The AGP default debug keystore lives outside
        // persistent storage and is regenerated whenever the environment
        // restarts, which breaks update installs — hence the pin. Clean
        // checkouts don't have the file and fall back to the default
        // debug key (see buildTypes.debug below).
        create("persistentDebug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            storeFile = rootProject.file(keystoreProps.getProperty("storeFile", "release.keystore"))
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        debug {
            signingConfig = if (rootProject.file("debug.keystore").exists()) {
                signingConfigs.getByName("persistentDebug")
            } else {
                signingConfigs.getByName("debug")
            }
            versionNameSuffix = "-DEBUG-${idForSimon()}"
            ndk {
                isDebuggable = true
                debugSymbolLevel = "FULL"
            }
        }
        release {
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            ndk {
                isDebuggable = false
                debugSymbolLevel = "FULL"
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    packaging {
        jniLibs {
            // see GamePlay.startGameGenProcess(...): this is the easiest way to get puzzlesgen installed and found per-architecture
            useLegacyPackaging = true
        }
    }
}

androidComponents {
    // These .sav files are needed by GamePlayScreenshotsTest.launchTestGame(...)
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.sources.assets?.addStaticSourceDirectory("src/main/jni/icons")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.2.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.gridlayout:gridlayout:1.1.0")
    implementation("androidx.annotation:annotation:1.9.1")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.webkit:webkit:1.14.0")

    val composeBom = platform("androidx.compose:compose-bom:2025.10.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation(composeBom)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.20.0")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.annotation:annotation:1.9.1")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("tools.fastlane:screengrab:2.1.1")
}
