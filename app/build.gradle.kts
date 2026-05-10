plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.vtop"
    compileSdk = 36

    // 1. Define the signing configuration using your retrieved password
    signingConfigs {
        create("release") {
            // Replace with your actual .jks file path.
            // Tip: Use forward slashes (/) even on Windows to avoid escape character errors.
            storeFile = file("C:/Users/manid/Downloads/vtopbs/vtop_keystore.jks")
            storePassword = "M@nideep14"
            keyAlias = "key0" // Or your specific alias
            keyPassword = "M@nideep14"
        }
    }
    defaultConfig {
        applicationId = "com.vtop"
        minSdk = 24
        targetSdk = 36
        versionCode = 2 // Incremented for the new update
        versionName = "1.1.4"

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            // 2. Link the release build to the signing config defined above
            signingConfig = signingConfigs.getByName("release")

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        debug {
            // Optional: Use release signing for debug to test Google Login while developing
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
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
    implementation(libs.androidx.appcompat)

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:32.8.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-config")
    implementation("com.google.firebase:firebase-messaging-ktx")

    // Play Services
    implementation("com.google.android.gms:play-services-auth:21.0.0")
    implementation("io.coil-kt:coil-compose:2.5.0")

    // UI & Icons
    implementation("com.mikepenz:multiplatform-markdown-renderer-m3:0.37.0")
    implementation("com.composables:icons-lucide:1.0.0")
    implementation("androidx.compose.material:material:1.6.0")
    implementation("androidx.compose.material:material-icons-extended:1.6.0")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Networking & Logic
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.google.code.gson:gson:2.10.1")

    // Glance & Widgets
    implementation("androidx.glance:glance-appwidget:1.0.0")
    implementation("androidx.glance:glance-material3:1.0.0")

    // Background Tasks
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}