// V192: BouncyCastle trên classpath của plugin Gradle (AGP kéo `bcpkix`/`bcprov` 1.77) đang
// dính lỗ hổng mức Nghiêm trọng. Đây là thư viện **chỉ dùng lúc build**, không nằm trong APK và
// không khai báo trong `gradle/libs.versions.toml`, nên ghim thẳng ở đây (một nguồn duy nhất).
// Nâng thì sửa số dưới; bốn artifact phải cùng phiên bản.
buildscript {
    configurations.configureEach {
        if (name == "classpath") {
            resolutionStrategy.force(
                "org.bouncycastle:bcpkix-jdk18on:1.85",
                "org.bouncycastle:bcprov-jdk18on:1.85",
                "org.bouncycastle:bcprov-jdk15to18:1.85",
                "org.bouncycastle:bcutil-jdk18on:1.85",
            )
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
