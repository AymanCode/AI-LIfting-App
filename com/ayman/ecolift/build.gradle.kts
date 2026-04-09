plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("ksp") version "1.8.20"
    id("org.jetbrains.kotlinx.serialization")
}

android {
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ayman.ecolift"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
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

    dependencies {
        implementation("androidx.core:core-ktx:1.9.0")
        implementation("androidx.appcompat:appcompat:1.6.1")
        implementation("com.google.android.material:material:1.8.0")
        implementation("androidx.compose.ui:ui:1.4.3")
        implementation("androidx.compose.ui:ui-tooling-preview:1.4.3")
        implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.1")
        implementation("androidx.navigation:navigation-compose:2.5.3")
        implementation("com.vico:vico-compose-material3:0.8.0")
        implementation("ai.google.media.pipe.tasks.genai:tasks-genai-android:0.4.0")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")

        testImplementation("androidx.test.ext:junit:1.1.5")
        testImplementation("androidx.test.espresso:espresso-core:3.5.1")
        testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.2")
        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.2")
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/src/main/schemas")
    }
}
