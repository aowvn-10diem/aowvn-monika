// Mô-đun đọc PDF của Aow Monika: màn đọc + nhớ trang + xử lý PDF lỗi.
// Dùng PdfRenderer có sẵn của Android nên không thêm thư viện ngoài.
// Giao diện nhận bảng màu/chữ từ app (ReaderStyle) để mô-đun không phụ thuộc :app
// nhưng màn hình vẫn vẽ đúng token Monika.* của app truyền vào.
plugins {
    // AGP đã có trên classpath nhờ plugins ở build.gradle.kts gốc.
    id("com.android.library")
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// V0911: Robolectric 4.16.1 kéo `bcprov-jdk18on` 1.81 (có cảnh báo mức critical) vào
// classpath test của mô-đun này. Ghim cùng bản trong `gradle/libs.versions.toml` như `:app`
// để classpath test không tự tụt bản khi thêm/sửa test. Chỉ ảnh hưởng classpath test,
// không vào APK (APK đã bị `:app` ghim sẵn).
val bcVersion = libs.versions.bouncycastle.get()
configurations.configureEach {
    resolutionStrategy.force(
        "org.bouncycastle:bcprov-jdk18on:$bcVersion",
        "org.bouncycastle:bcprov-jdk15to18:$bcVersion",
        "org.bouncycastle:bcpkix-jdk18on:$bcVersion",
        "org.bouncycastle:bcutil-jdk18on:$bcVersion",
    )
}

android {
    namespace = "vn.aow.monika.reader"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isIncludeAndroidResources = true }
    buildFeatures { compose = true }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
