import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val keystorePropertiesFile = rootProject.file("key.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.xaozora.manager"
    ndkVersion = "27.0.12077973"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    signingConfigs {
        create("release") {
            keyAlias = keystoreProperties["keyAlias"] as String
            keyPassword = keystoreProperties["keyPassword"] as String
            storeFile = file(keystoreProperties["storeFile"] as String)
            storePassword = keystoreProperties["storePassword"] as String
        }
    }
    defaultConfig {
        applicationId = "com.xaozora.manager"
        minSdk = 29
        //noinspection OldTargetApi
        targetSdk = 36
        versionCode = 2
        versionName = "2.5.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
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
    buildFeatures {
        compose = true
    }
    
    sourceSets {
        getByName("main") {
            jniLibs.directories.clear()
            jniLibs.directories.add("src/main/libs")
        }
    }
}


val rustProjectDir = file("src/main/rust/xaozora_daemon")
val rustOutputBinary = file("src/main/rust/xaozora_daemon/target/aarch64-linux-android/release/xaozora_daemon")
val assetsOutputDir = file("src/main/assets")

val ndkDir: String by lazy {
    val envNdk = System.getenv("ANDROID_NDK_HOME")
    if (!envNdk.isNullOrBlank() && File(envNdk).exists()) return@lazy envNdk

    val localProps = rootProject.file("local.properties")
    if (localProps.exists()) {
        val props = Properties()
        props.load(FileInputStream(localProps))

        val ndkFromLocal = props.getProperty("ndk.dir")
        if (!ndkFromLocal.isNullOrBlank() && File(ndkFromLocal).exists()) return@lazy ndkFromLocal

        val sdkFromLocal = props.getProperty("sdk.dir")
        if (!sdkFromLocal.isNullOrBlank()) {
            val ndkBase = File(sdkFromLocal, "ndk")
            if (ndkBase.exists()) {
                val latest = ndkBase.listFiles()
                    ?.filter { f -> f.isDirectory }
                    ?.maxByOrNull { f -> f.name }
                if (latest != null) return@lazy latest.absolutePath
            }
        }
    }

    error("Cannot find Android NDK. Set ANDROID_NDK_HOME, or add ndk.dir to local.properties.")
}

tasks.register<Exec>("buildRustDaemon") {
    group = "rust"
    description = "Compiles the Rust xaozora_daemon for aarch64-linux-android"

    workingDir = rustProjectDir
    environment("ANDROID_NDK_HOME", ndkDir)

    commandLine("cargo", "ndk", "-t", "arm64-v8a", "build", "--release")

    inputs.dir(rustProjectDir.resolve("src"))
    inputs.file(rustProjectDir.resolve("Cargo.toml"))
    inputs.file(rustProjectDir.resolve("Cargo.lock"))
    outputs.file(rustOutputBinary)
}

tasks.register<Exec>("buildRustJni") {
    group = "rust"
    description = "Compiles the Rust xaozora_jni native library for Android targets"

    workingDir = file("src/main/rust/xaozora_jni")
    environment("ANDROID_NDK_HOME", ndkDir)

    commandLine("cargo", "ndk", "-t", "arm64-v8a", "-o", "../../libs", "build", "--release")

    inputs.dir(file("src/main/rust/xaozora_jni/src"))
    inputs.file(file("src/main/rust/xaozora_jni/Cargo.toml"))
    outputs.dir(file("src/main/libs"))
}

tasks.register<Copy>("copyRustDaemonToAssets") {
    group = "rust"
    description = "Copies the compiled Rust daemon binary to the Android assets folder"
    dependsOn("buildRustDaemon")

    from(rustOutputBinary)
    into(assetsOutputDir)
}

afterEvaluate {
    tasks.matching { 
        it.name.matches(Regex("merge(Debug|Release)Assets")) ||
        it.name.matches(Regex("lintVitalAnalyze(Debug|Release)")) ||
        it.name.matches(Regex("generate(Debug|Release)LintVitalReportModel"))
    }.configureEach {
        dependsOn("copyRustDaemonToAssets")
    }

    tasks.matching {
        it.name.matches(Regex("merge(Debug|Release)JniLibFolders")) ||
        it.name.matches(Regex("pre(Debug|Release)Build"))
    }.configureEach {
        dependsOn("buildRustJni")
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.haze.blur)
    implementation(libs.haze.materials)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}