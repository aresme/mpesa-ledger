plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.ares.mpesaledger"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ares.mpesaledger"
        minSdk = 24
        targetSdk = 37
        versionCode = 10000
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing comes ONLY from ~/.gradle/gradle.properties or environment variables,
    // never from the repo. Without them, assembleRelease produces an unsigned APK.
    val keystore = providers.gradleProperty("MPESA_KEYSTORE").orElse(providers.environmentVariable("MPESA_KEYSTORE"))
    fun secret(name: String) = providers.gradleProperty(name).orElse(providers.environmentVariable(name))
    signingConfigs {
        if (keystore.isPresent) {
            create("release") {
                storeFile = file(keystore.get())
                storePassword = secret("MPESA_KEYSTORE_PASSWORD").get()
                keyAlias = secret("MPESA_KEY_ALIAS").get()
                keyPassword = secret("MPESA_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        debug {
            // Separate package so a signed release and a debug build can live side by side
            // (different keys can't replace each other, and uninstalling would wipe the ledger).
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystore.isPresent) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        // java.time on API 24-25 needs core library desugaring.
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    constraints {
        // AGP pins androidTest to the app's runtime versions; without this the Room migration
        // test fails with AbstractMethodError (serialization-json 1.8.1 vs core 1.7.3).
        implementation(libs.kotlinx.serialization.core)
    }
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.splashscreen)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
    // Android's org.json is a stub in JVM unit tests; use the real library there.
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.room.testing)
}

// APKs are named MpesaLedger-<version>-<buildType>.apk.
base {
    archivesName.set("MpesaLedger-${android.defaultConfig.versionName}")
}
