plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "vn.aow.monika"
    compileSdk = 35

    defaultConfig {
        applicationId = "vn.aow.monika"
        minSdk = 26 // Android 8.0+
        targetSdk = 35
        // Tăng versionCode mỗi lần phát hành; app so số này với config để nhắc cập nhật.
        versionCode = 1
        versionName = "0.1.0"
        // Link file cấu hình từ xa. Đổi được mà không cần sửa code.
        buildConfigField(
            "String", "REMOTE_CONFIG_URL",
            "\"https://raw.githubusercontent.com/aowvn-10diem/aowvn-monika/main/config/monika-config.json\""
        )
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    // Khóa ký bản phát hành lấy từ biến môi trường (GitHub Secrets), không bao giờ lưu trong repo.
    val keystore = System.getenv("MONIKA_KEYSTORE")
    signingConfigs {
        create("release") {
            if (keystore != null) {
                storeFile = file(keystore)
                storePassword = System.getenv("MONIKA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MONIKA_KEY_ALIAS")
                keyPassword = System.getenv("MONIKA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName(if (keystore != null) "release" else "debug")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // Bản cấu hình dự phòng đóng gói trong APK = đúng file config/ ở gốc repo (1 nguồn duy nhất).
    sourceSets["main"].assets.srcDirs("src/main/assets", "../config")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.compose.tooling.preview)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.androidx.webkit)
    implementation(libs.libretrodroid)
    implementation(libs.libarchive) // Giải nén zip/rar/rar5/7z
    testImplementation(libs.junit)
}
