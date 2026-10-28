import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// The Google Services plugin needs app/google-services.json (see README step 2).
// Without it the project still builds, and the app shows a "Firebase not configured" screen.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
// Only needed when your Realtime Database is NOT in us-central1, e.g.
// FIREBASE_DATABASE_URL=https://YOUR-PROJECT-default-rtdb.europe-west1.firebasedatabase.app
val firebaseDbUrl: String = localProps.getProperty("FIREBASE_DATABASE_URL", "")
// Optional TURN relay for file sharing on strict networks (see README, "File sharing")
val turnUrl: String = localProps.getProperty("TURN_URL", "")
val turnUser: String = localProps.getProperty("TURN_USER", "")
val turnPass: String = localProps.getProperty("TURN_PASS", "")

android {
    namespace = "com.nhdeveloper.nhconnect"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nhdeveloper.nhconnect"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "3.0.0"
        buildConfigField("String", "FIREBASE_DATABASE_URL", "\"$firebaseDbUrl\"")
        buildConfigField("String", "TURN_URL", "\"$turnUrl\"")
        buildConfigField("String", "TURN_USER", "\"$turnUser\"")
        buildConfigField("String", "TURN_PASS", "\"$turnPass\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions { unitTests.isReturnDefaultValues = true }

    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.process)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)
    implementation(libs.firebase.messaging)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime.ktx)
    implementation(libs.coil.compose)
    implementation(libs.webrtc)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play.services)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
