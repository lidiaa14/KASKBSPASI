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

// Ensure google-services.json exists so builds never fail if omitted from repository clone/checkout
val googleServicesJsonFile = file("google-services.json")
if (!googleServicesJsonFile.exists()) {
    val envGoogleServices = System.getenv("GOOGLE_SERVICES_JSON")
    if (!envGoogleServices.isNullOrBlank()) {
        googleServicesJsonFile.writeText(envGoogleServices)
    } else {
        googleServicesJsonFile.writeText(
            """
            {
              "project_info": {
                "project_number": "1037396381254",
                "project_id": "uangkas-ef7cf",
                "storage_bucket": "uangkas-ef7cf.firebasestorage.app"
              },
              "client": [
                {
                  "client_info": {
                    "mobilesdk_app_id": "1:1037396381254:android:3a6fe42cc78be098760447",
                    "android_client_info": {
                      "package_name": "com.uangkas.community"
                    }
                  },
                  "oauth_client": [],
                  "api_key": [
                    {
                      "current_key": "AIzaSyBLzzewLA-WuzkcWDv7R0Yqz0AMIUjqqJg"
                    }
                  ],
                  "services": {
                    "appinvite_service": {
                      "other_platform_oauth_client": []
                    }
                  }
                }
              ],
              "configuration_version": "1"
            }
            """.trimIndent()
        )
    }
}

// Automatically sync System Environment variables into root local.properties file
val localPropsFile = rootProject.file("local.properties")
val localProperties = Properties()
if (localPropsFile.exists()) {
    localPropsFile.inputStream().use { localProperties.load(it) }
}

var localModified = false
val keysToSync = listOf("GEMINI_API_KEY", "GEMINI_API_KEY_FALLBACK_1")

for (key in keysToSync) {
    val envValue = System.getenv(key)
    val currentValue = localProperties.getProperty(key)
    if (!envValue.isNullOrEmpty() && currentValue != envValue) {
        localProperties.setProperty(key, envValue)
        localModified = true
    }
}

if (localModified || !localPropsFile.exists()) {
    localPropsFile.outputStream().use {
        localProperties.store(it, "Automatically generated/updated during Gradle build")
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
    propertiesFileName = "local.properties"
    defaultPropertiesFileName = "local.defaults.properties"
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
