import com.google.firebase.appdistribution.gradle.firebaseAppDistribution

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.gms.google-services")
    id("com.google.firebase.appdistribution")
}

android {
    namespace = "com.shottimer.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.shottimer.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "0.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Builds handed to testers via Firebase App Distribution. Kept separate from the
            // developer's own local debug installs only in intent - same variant, since the
            // full App Distribution SDK (shake-to-report) below is already gated to debug-only.
            firebaseAppDistribution {
                releaseNotes = "Same as 0.3.0 (custom drills, tap-to-expand drill list, " +
                    "shooter rename/remove, share/export runs, trend lines). Welcome aboard!"
                testers = "lymberkyle@gmail.com,jess54191@gmail.com,georgepace8@gmail.com,jrugjr@gmail.com"
            }
        }
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
        // Needed for BuildConfig.DEBUG, which gates the shake-to-report feedback detector and
        // Settings "Send Feedback" row to debug builds only - AGP 8+ no longer generates
        // BuildConfig by default.
        buildConfig = true
    }

    sourceSets {
        // MigrationTestHelper reads the exported schema JSON at runtime to know the exact
        // starting-version table shape - without this, every createDatabase(name, version) call
        // fails looking for it in test assets.
        getByName("androidTest").assets.srcDirs("$projectDir/schemas")
    }

    testOptions {
        unitTests {
            // ViewModels under test call getString()/getQuantityString() on the real
            // Application context (via Robolectric) - without this they'd get a blank
            // ShadowAssetManager and every string resource would resolve to "".
            isIncludeAndroidResources = true
        }
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

    // Google sign-in + cloud backup. Credential Manager (not the deprecated GoogleSignInClient)
    // for the sign-in UI/flow; Firebase Auth turns its ID token into a signed-in FirebaseUser;
    // Firestore is the actual backup store, chosen for its built-in offline write queue - this
    // app is used at ranges where connectivity is often spotty or absent.
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Firebase App Distribution: builds handed to testers, plus shake-to-report feedback.
    // The full SDK (feedback UI, self-update checks) is debug-only per Google's own warning
    // that shipping it in a Play Store release can get the app removed from Play; the -api
    // artifact alone is safe for all variants but isn't needed outside debug here since
    // nothing else in the app calls it.
    implementation("com.google.firebase:firebase-appdistribution-api:16.0.0-beta20")
    debugImplementation("com.google.firebase:firebase-appdistribution:16.0.0-beta20")

    testImplementation("junit:junit:4.13.2")
    // ViewModel/DAO unit tests run against a real (Robolectric-shadowed) Application context and
    // Room/SharedPreferences, rather than an emulator - keeps them part of `testDebugUnitTest`.
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.03"))
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.room:room-testing:2.8.4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

kapt {
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}
