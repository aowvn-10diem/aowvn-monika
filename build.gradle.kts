// V192b: hai thư viện **chỉ dùng lúc build** (không vào APK, không khai báo trong
// gradle/libs.versions.toml) đang nằm trên classpath plugin Gradle và dính cảnh báo mức
// Nghiêm trọng: `io.netty` 4.1.93.Final (AGP analytics kéo qua grpc-netty) và
// `org.freemarker:freemarker` 2.3.32 (Kover kéo qua coverage-report 1.0.25).
// Ghim tại đây: cả họ netty phải cùng phiên bản; nâng thì sửa hai số dưới.
buildscript {
    configurations.configureEach {
        if (name == "classpath") {
            resolutionStrategy {
                eachDependency {
                    if (requested.group == "io.netty") useVersion("4.1.137.Final")
                }
                force("org.freemarker:freemarker:2.3.35")
            }
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kover) apply false
}
