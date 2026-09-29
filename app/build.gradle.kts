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
        applicationId = "com.aow.monika"
        minSdk = 26 // Android 8.0+
        targetSdk = 35
        // Tăng versionCode mỗi lần phát hành; app so số này với config để nhắc cập nhật.
        // App thuần Việt: chỉ giữ tài nguyên tiếng Việt (J2ME Loader và thư viện kèm theo cũng hiện tiếng Việt
        // kể cả khi máy đặt ngôn ngữ khác). Bỏ ~40 ngôn ngữ thừa → APK nhẹ hơn.
        resourceConfigurations += listOf("vi")
        versionCode = 14
        versionName = "0.4.1"
        // Link file cấu hình từ xa (Cloudflare Worker, repo giữ private).
        buildConfigField(
            "String", "REMOTE_CONFIG_URL",
            "\"https://aowvn-monika.aowvn-system.workers.dev/config.json\""
        )
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
            // R8: thu gọn + bỏ code thừa. Luật giữ lớp: proguard-rules.pro (+ luật của j2me/).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    // Mỗi loại chip 1 APK (nhẹ ~1/2) + 1 bản chung cho mọi máy.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isIncludeAndroidResources = true }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // Thư viện native trùng tên giữa các gói (LibretroDroid, ffmpeg của J2ME Loader...) → lấy 1 bản.
    packaging {
        jniLibs {
            pickFirsts += listOf("**/libc++_shared.so")
            // Chỉ hỗ trợ máy ARM (điện thoại thật); bỏ bản x86 của thư viện cho APK chung nhẹ đi.
            excludes += listOf("lib/x86/**", "lib/x86_64/**")
        }
        resources { excludes += listOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/*.kotlin_module") }
    }
    // Bản cấu hình dự phòng đóng gói trong APK = đúng file config/ ở gốc repo (1 nguồn duy nhất).
    sourceSets["main"].assets.srcDirs("src/main/assets", "../config")
}

// Test trên máy tính: lấy lib7-Zip-JBinding.so bản Linux (cùng bản 16.02) để chạy code 7-Zip của Android.
val sevenZipTestNatives: Configuration by configurations.creating
val unpackSevenZipNatives by tasks.registering(Copy::class) {
    from({ sevenZipTestNatives.map { zipTree(it) } }) { include("Linux-amd64/*.so"); eachFile { path = name } }
    into(layout.buildDirectory.dir("sevenzip-natives"))
    includeEmptyDirs = false
}
tasks.withType<Test>().configureEach {
    dependsOn(unpackSevenZipNatives)
    systemProperty("java.library.path", layout.buildDirectory.dir("sevenzip-natives").get().asFile.absolutePath)
    // Giống Android: tên file luôn UTF-8 (máy CI có thể để locale POSIX → tên tiếng Việt thành "??").
    environment("LC_ALL", "C.UTF-8")
    jvmArgs("-Dsun.jnu.encoding=UTF-8", "-Dfile.encoding=UTF-8")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.splashscreen) // Màn chờ khi mở app
    implementation(libs.androidx.browser) // Custom Tab: đăng nhập Google trên web
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.tooling.preview)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.androidx.webkit)
    implementation(libs.libretrodroid)
    implementation(libs.libarchive) // Giải nén zip/rar/rar5/7z
    implementation(libs.commons.compress) // 7z có mật khẩu (libarchive không hỗ trợ)
    implementation(libs.xz) // LZMA/LZMA2 cho 7z
    implementation(libs.sevenzip.android) // 7-Zip native: RAR/RAR5 có mật khẩu, RAR nhiều phần
    sevenZipTestNatives(libs.sevenzip.jvm.natives)
    implementation(project(":j2me")) // Giả lập Java J2ME (J2ME Loader nhúng sẵn)
    testImplementation(libs.junit)
    // Test giao diện trên JVM (Robolectric): mở từng màn hình, bắt crash, chụp ảnh → app/build/screenshots/
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
