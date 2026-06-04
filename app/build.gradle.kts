import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    id("com.google.gms.google-services")
    alias(libs.plugins.secrets)
}

// Automatically sync System Environment variables & local.properties into root .env file
val envFile = rootProject.file(".env")
if (!envFile.exists()) {
    envFile.createNewFile()
}

val envProperties = Properties()
if (envFile.length() > 0) {
    envFile.inputStream().use { envProperties.load(it) }
}

var envModified = false

val localPropsFile = rootProject.file("local.properties")
val localProperties = Properties()
if (localPropsFile.exists()) {
    localPropsFile.inputStream().use { localProperties.load(it) }
}

val keysToLoad = listOf("GEMINI_API_KEY", "GEMINI_API_KEY_FALLBACK_1")
for (key in keysToLoad) {
    val sysValue = System.getenv(key)
    val localValue = localProperties.getProperty(key)
    val currentValue = envProperties.getProperty(key)

    val bestValue = when {
        !sysValue.isNullOrEmpty() -> sysValue
        !localValue.isNullOrEmpty() -> localValue
        else -> currentValue
    }

    if (!bestValue.isNullOrEmpty() && currentValue != bestValue) {
        envProperties.setProperty(key, bestValue)
        envModified = true
    }
}

if (envModified) {
    envFile.outputStream().use {
        envProperties.store(it, "Automatically generated/updated during Gradle build")
    }
}

android {
    namespace = "com.example"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.uangkas.community"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8" // compatible with Kotlin 1.9.22
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

secrets {
    propertiesFileName = ".env"
    defaultPropertiesFileName = ".env.example"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)

    // Room database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Networking & OkHttp
    implementation(libs.okhttp)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
