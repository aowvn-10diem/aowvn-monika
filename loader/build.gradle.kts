import java.util.Properties
// Bộ nạp data chạy BÊN TRONG game (Cách 1 của trình cài game Android). Java thuần, không phụ thuộc thư viện nào.
// Biên dịch ra .jar → app dùng D8 đổi thành monika-loader.dex và nhúng vào assets (xem app/build.gradle.kts, tác vụ loaderAsset).
plugins { `java-library` }

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

val sdkDir: String = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    ?: Properties().also { p -> rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(p::load) }.getProperty("sdk.dir")
    ?: error("Không tìm thấy Android SDK (đặt ANDROID_HOME hoặc sdk.dir trong local.properties)")

dependencies {
    compileOnly(files("$sdkDir/platforms/android-35/android.jar"))
}
