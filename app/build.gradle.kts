plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.uepflip_flashcard"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.uepflip_flashcard"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

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
    buildFeatures {
        viewBinding = true
    }
    packaging {
        // Handle duplicate META-INF files from PDFBox libraries
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1,DEPENDENCIES}"
        }
    }
    testOptions {
        // AI service tests exercise android.util.Log paths on the JVM.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.constraintlayout)

    // Lifecycle & Room & Coroutines
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.ksp)
    implementation(libs.kotlinx.coroutines.android)

    // PDFBox for text extraction
    implementation(libs.pdfbox)
    implementation(libs.fontbox)

    // OkHttp & Moshi for AI API communication
    implementation(libs.okhttp)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)

    // Firebase BoM
    implementation(platform("com.google.firebase:firebase-bom:33.2.0"))
    // Firebase Remote Config
    implementation("com.google.firebase:firebase-config-ktx")
    // Firebase Auth, Database
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    
    // WorkManager for background sync
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}