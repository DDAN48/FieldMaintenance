plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-kapt")
}

android {
    namespace = "com.example.fieldmaintenance"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.fieldmaintenance"
        minSdk = 24
        targetSdk = 36
        versionCode = 5
        versionName = "5.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            // Exclude the problematic native library from Coil if not critical
            // This library is used for image processing optimizations but may not be essential
            excludes += "/lib/arm64-v8a/libimage_processing_util_jni.so"
            excludes += "/lib/armeabi-v7a/libimage_processing_util_jni.so"
            excludes += "/lib/x86/libimage_processing_util_jni.so"
            excludes += "/lib/x86_64/libimage_processing_util_jni.so"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    
    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)
    
    // Navigation
    implementation(libs.androidx.navigation.compose)
    
    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    
    // Permissions
    implementation(libs.accompanist.permissions)
    
    // JSON
    implementation(libs.gson)
    
    // PDF
    implementation(libs.itext)
    
    // ZIP
    implementation(libs.zip4j)
    
    // Icons Extended
    implementation("androidx.compose.material:material-icons-extended")

    // Images (updated for 16 KB page size support)
    // Note: libimage_processing_util_jni.so is excluded in packaging.resources to fix 16 KB alignment
    // Coil will work without this native library, just with slightly less image processing optimization
    implementation("io.coil-kt:coil-compose:2.7.0")
    
    // EXIF (for correct orientation on export)
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // DataStore (Settings)
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
