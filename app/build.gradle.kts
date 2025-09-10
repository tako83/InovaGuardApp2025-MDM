plugins {
    alias(libs.plugins.android.application)
    // AÑADE ESTA LÍNEA CORREGIDA AQUÍ
    id("com.google.gms.google-services")
}

android {
    namespace = "com.inova.guard.mdm"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.inova.guard.mdm"
        minSdk = 24
        targetSdk = 36
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
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation("com.airbnb.android:lottie:6.0.0")

    implementation(libs.okhttp)
    implementation(libs.glide)
    annotationProcessor(libs.glide.compiler)
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // AÑADE ESTAS LÍNEAS AQUÍ para Firebase Messaging
    implementation(platform("com.google.firebase:firebase-bom:33.0.0")) // Versión corregida
    implementation("com.google.firebase:firebase-messaging")
}