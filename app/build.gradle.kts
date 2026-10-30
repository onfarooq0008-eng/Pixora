import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing: create keystore.properties in the project root (never commit it). See PLAY_CONSOLE_GUIDE.md.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.pixorapdf"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pixorapdf"
        minSdk = 29
        targetSdk = 36
        // CI passes -PversionCodeOverride=<run number> so every Play upload has a higher code
        versionCode = (project.findProperty("versionCodeOverride") as String?)?.toIntOrNull() ?: 2
        versionName = "1.0.1"
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreProps.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    // Yandex lint fails release builds whenever a newer ads SDK exists; keep builds reproducible instead
    lint {
        disable += "YandexAdsSdkOutdatedVersion"
        abortOnError = false   // report is still uploaded by CI; findings no longer block the APK/AAB
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
        // Keep native libs uncompressed so they stay 16 KB zip-aligned (Play 16 KB page-size rule)
        jniLibs { useLegacyPackaging = false }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Camera (Scan Document)
    val camerax = "1.4.2"   // 1.3.x ships libimage_processing_util_jni.so without 16 KB alignment
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    // Real PDF merge / split / rotate / password / JPEG-compressed pages
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // Yandex Mobile Ads (native + interstitial)
    implementation("com.yandex.android:mobileads:8.6.0")
}
