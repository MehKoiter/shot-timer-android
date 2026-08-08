plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.shottimer.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.shottimer.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    kapt("androidx.room:room-compiler:2.8.4")

    // Stable line (not no.nordicsemi.android.kotlin.ble, which is still Beta with a rewrite
    // in progress per Nordic's own guidance). NOT using the ble-ktx coroutine extension
    // artifact: its 2.11.0 release was compiled against a newer Kotlin than this project's
    // 2.0.21 (incompatible metadata version, fails at compile time) - rather than bump the
    // whole project's Kotlin/AGP toolchain just for a thin convenience wrapper, PiRepository
    // wraps the core library's callback API in a small Flow itself.
    implementation("no.nordicsemi.android:ble:2.11.0")

    testImplementation("junit:junit:4.13.2")
    // Real org.json implementation for local JVM unit tests - the org.json.* classes bundled
    // in Android's own SDK jar are stubs on the unit-test classpath (every method throws
    // "not mocked"), so parsePiEvent()'s tests need this to actually exercise JSON parsing.
    testImplementation("org.json:json:20260719")

    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.03"))
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
