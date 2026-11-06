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
