import java.util.Properties
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
}

android {
    namespace = "vn.aow.monika"
    // V152: Android 16 (API 36). targetSdk 36 → bắt buộc edge-to-edge, màn lớn bỏ khoá xoay, predictive back mặc định.
    compileSdk = 36
    // Cùng NDK với :j2me: để AGP có llvm-strip và cắt ký hiệu debug khỏi libc++_shared/oboe/javam3g/mmapi... (≈ −14 MB APK; trước đó log báo "Unable to strip").
    ndkVersion = "22.1.7171670"

    defaultConfig {
        applicationId = "com.aow.monika"
        minSdk = 26 // Android 8.0+
        targetSdk = 36
        // Tăng versionCode mỗi lần phát hành; app so số này với config để nhắc cập nhật.
        // App thuần Việt: chỉ giữ tài nguyên tiếng Việt (J2ME Loader và thư viện kèm theo cũng hiện tiếng Việt
        // kể cả khi máy đặt ngôn ngữ khác). Bỏ ~40 ngôn ngữ thừa → APK nhẹ hơn.
        resourceConfigurations += listOf("vi")
        versionCode = 54
        versionName = "0.8.9"
        // V56: runner chỉ nằm trong APK instrumentation, không thêm lối vào app phát hành.
        testInstrumentationRunner = "vn.aow.monika.testlab.KirikiriArmRunner"
        // Link file cấu hình từ xa (Cloudflare Worker, repo giữ private).
        buildConfigField(
            "String", "REMOTE_CONFIG_URL",
            "\"https://aowvn-monika.aowvn-system.workers.dev/config.json\""
        )
        // [A65] flavor `play` (V150) đặt false: màn "Cần cập nhật" chỉ ghi "Cập nhật trên Google Play", không mở link APK ngoài.
        buildConfigField("boolean", "APK_DOWNLOAD_ALLOWED", "true")
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("github") { dimension = "distribution" }
        create("play") {
            dimension = "distribution"
            applicationId = "com.aow.monika.play"
            buildConfigField("String", "REMOTE_CONFIG_URL", "\"\"")
            buildConfigField("boolean", "APK_DOWNLOAD_ALLOWED", "false")
        }
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
            // 7-Zip native (~2,6 MB/ABI) không đóng trong APK: tải khi cần giải nén RAR/7z (config.modules.sevenzip).
            excludes += listOf("**/lib7-Zip-JBinding.so")
        }
        // org/bouncycastle/pqc/**: bảng số của thuật toán hậu lượng tử (Picnic) ~1,2 MB mà libadb không dùng (chỉ RSA/EC/SPAKE2).
        resources { excludes += listOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/*.kotlin_module", "org/bouncycastle/pqc/**") }
    }
    // Bản cấu hình dự phòng đóng gói trong APK = đúng file config/ ở gốc repo (1 nguồn duy nhất).
    sourceSets["test"].java.setSrcDirs(emptyList<String>())
    sourceSets["testGithub"].java.srcDir("src/test/java")
    sourceSets["testGithub"].resources.srcDirs("src/test/resources", "../cloudflare/crash-worker/fixtures")
    // Ghi công, cheat và script RGSS dùng chung; chỉ catalog gốc dành cho GitHub.
    sourceSets["main"].assets.srcDir("src/main/assets")
    sourceSets["github"].assets.srcDir("../config")
    sourceSets["androidTest"].java.setSrcDirs(emptyList<String>())
    sourceSets["androidTestGithub"].java.srcDir("src/androidTest/java")
    sourceSets["androidTestGithub"].assets.srcDirs("src/androidTest/assets", layout.buildDirectory.dir("generated/v56-assets"))
}

// Giữ lệnh test hiện hành cho CI và trước push; chạy cả hai bản.
tasks.register("testDebugUnitTest") { dependsOn("testGithubDebugUnitTest", "testPlayDebugUnitTest") }
tasks.register("koverXmlReportDebug") { dependsOn("koverXmlReportGithubDebug", "koverXmlReportPlayDebug") }
tasks.register("koverHtmlReportDebug") { dependsOn("koverHtmlReportGithubDebug", "koverHtmlReportPlayDebug") }
val playConfig by tasks.registering(Exec::class) {
    val source = rootProject.file("config/monika-config.json")
    val script = rootProject.file("scripts/play-config.py")
    val output = layout.buildDirectory.file("generated/play-assets/monika-config.json")
    inputs.files(source, script)
    outputs.file(output)
    commandLine("python3", script.absolutePath, source.absolutePath, output.get().asFile.absolutePath)
}
android.sourceSets["play"].assets.srcDir(playConfig.map {
    layout.buildDirectory.dir("generated/play-assets").get().asFile
})
// AGP chuyển srcDir thành File khi tạo một số task nên vẫn cần cạnh dependency tường minh.
tasks.matching {
    it.name.startsWith("mergePlay") && it.name.endsWith("Assets") ||
        it.name.contains("Play") && it.name.contains("lint", ignoreCase = true)
}.configureEach { dependsOn(playConfig) }

// Test trên máy tính: lấy lib7-Zip-JBinding.so bản Linux (cùng bản 16.02) để chạy code 7-Zip của Android.
val sevenZipTestNatives: Configuration by configurations.creating
val unpackSevenZipNatives by tasks.registering(Copy::class) {
    from({ sevenZipTestNatives.map { zipTree(it) } }) { include("Linux-amd64/*.so"); eachFile { path = name } }
    into(layout.buildDirectory.dir("sevenzip-natives"))
    includeEmptyDirs = false
}
tasks.withType<Test>().configureEach {
    dependsOn(unpackSevenZipNatives)
    // V152: sandbox Robolectric API 36 (Android 16) chỉ dựng được khi JVM >= 21 — Robolectric 4.16.1
    // ném UnsupportedOperationException "Android SDK 36 requires Java 21" trên JDK 17. Gradle vẫn
    // chạy được trên JDK 17; chỉ JVM chạy test cần 21 (toolchain, xem gradle.properties).
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    })
    systemProperty("java.library.path", layout.buildDirectory.dir("sevenzip-natives").get().asFile.absolutePath)
    // Giống Android: tên file luôn UTF-8 (máy CI có thể để locale POSIX → tên tiếng Việt thành "??").
    environment("LC_ALL", "C.UTF-8")
    jvmArgs("-Dsun.jnu.encoding=UTF-8", "-Dfile.encoding=UTF-8")
}

// Bộ nạp data (module :loader) → D8 → assets/monika-loader.dex. Trình cài game chèn file này vào APK game (Cách 1).
val d8Tool: Configuration by configurations.creating
val loaderDex by tasks.registering(JavaExec::class) {
    val out = layout.buildDirectory.dir("generated/loader-dex")
    val jar = project(":loader").tasks.named<Jar>("jar")
    dependsOn(jar)
    classpath = d8Tool
    mainClass.set("com.android.tools.r8.D8")
    val sdk = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
        ?: Properties().also { p -> rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(p::load) }.getProperty("sdk.dir")
    doFirst {
        out.get().asFile.deleteRecursively(); out.get().asFile.mkdirs()
        args("--release", "--min-api", "21", "--lib", "$sdk/platforms/android-36/android.jar", "--output", out.get().asFile.absolutePath, jar.get().archiveFile.get().asFile.absolutePath)
    }
    inputs.files(jar.map { it.archiveFile })
    outputs.dir(out)
}
val loaderAsset by tasks.registering(Sync::class) {
    dependsOn(loaderDex)
    from(layout.buildDirectory.dir("generated/loader-dex")) { include("classes.dex"); rename { "monika-loader.dex" } }
    into(layout.buildDirectory.dir("generated/loader-assets"))
}
// Provider giữ quan hệ tạo → dùng cho mọi tác vụ đọc assets, gồm cả lint model bản release.
android.sourceSets["github"].assets.srcDir(loaderAsset.map { it.destinationDir })
tasks.matching {
    it.name.startsWith("mergeGithub") && it.name.endsWith("Assets") ||
        it.name.contains("Github") && it.name.contains("lint", ignoreCase = true)
}.configureEach { dependsOn(loaderAsset) }
tasks.withType<Test>().matching { it.name.contains("Github") }.configureEach { dependsOn(loaderAsset, arscStripped) }

// ARSCLib đóng kèm bản sao lớp CỦA ANDROID (org.xmlpull.v1.*, android.util.AttributeSet, android.content.res.XmlResourceParser).
// R8 đổi tên các bản sao này → cả app (kể cả vẽ icon vector của Compose) gọi nhầm → IncompatibleClassChangeError ngay khi mở app.
// Lọc bỏ chúng khỏi jar: lớp hệ thống luôn dùng bản của Android.
val arscRaw: Configuration by configurations.creating
val arscStripped by tasks.registering(Jar::class) {
    archiveFileName.set("ARSCLib-stripped.jar")
    destinationDirectory.set(layout.buildDirectory.dir("stripped-libs"))
    from({ arscRaw.map { zipTree(it) } })
    exclude("org/xmlpull/**", "android/**")
    // Bộ khung tài nguyên Android 25–35 mà ARSCLib đóng kèm (~2 MB) chỉ để in tên thuộc tính ra chữ; ta sửa manifest theo mã số nên không cần.
    // Bỏ khỏi cả test lẫn APK cùng lúc → test Cách 1 (ApkRepackerTest…) chạy đúng bản như trong APK.
    exclude("frameworks/**")
    // Đổi danh sách loại bỏ phải làm tác vụ chạy lại (Gradle không luôn nhận ra thay đổi exclude khi nguồn là zipTree).
    inputs.property("stripped", "org/xmlpull,android,frameworks")
}

dependencies {
    arscRaw(libs.arsclib)
    d8Tool(libs.r8)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.splashscreen) // Màn chờ khi mở app
    implementation(libs.androidx.browser) // Custom Tab: đăng nhập Google trên web
    implementation(libs.androidx.documentfile) // Chọn thư mục lưu file tải (SAF)
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
    // Dịch màn hình: OCR của Google ML Kit bản qua Google Play Services (mô hình tải khi dùng lần đầu, APK không chứa). Dịch dùng khóa API của người dùng.
    implementation(libs.mlkit.ocr)
    implementation(libs.mlkit.ocr.ja)
    implementation(libs.androidx.webkit)
    implementation(project(":libretrodroid")) // bản nhúng (docs/LIBRETRODROID.md)
    implementation(project(":kirikiri")) // Kirikiroid2Yuri nhúng: mã Java ở đây, lib native + tài nguyên là gói tải thêm
    implementation(project(":renpy")) // Java của Ren'Py 8 (RAPT 8.5.3); lib native + Python là gói tải thêm
    implementation(project(":rgss")) // lớp SDL 2.26.3 đổi gói cho mkxp-z; lib native là gói tải thêm
    implementation(libs.zip4j) // Giải nén .zip (kể cả mật khẩu)
    implementation(libs.commons.compress) // 7z thuần Java (dự phòng khi 7-Zip native chưa tải được)
    implementation(libs.xz) // LZMA/LZMA2 cho 7z
    implementation(libs.sevenzip.android) // 7-Zip native: RAR/RAR5 có mật khẩu, RAR nhiều phần
    "githubImplementation"(files(arscStripped)) // Chỉ bản GitHub có trình cài/sửa APK game.
    "githubImplementation"(libs.libadb)
    "githubImplementation"(libs.apksig)
    sevenZipTestNatives(libs.sevenzip.jvm.natives)
    implementation(project(":j2me")) // Giả lập Java J2ME (J2ME Loader nhúng sẵn)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Test giao diện trên JVM (Robolectric): mở từng màn hình, bắt crash, chụp ảnh → app/build/screenshots/
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
