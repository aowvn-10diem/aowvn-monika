import org.gradle.api.artifacts.VersionCatalogsExtension

// V192: ghim BouncyCastle cho classpath plugin Gradle (AGP kéo bcpkix/bcprov cũ).
// V0811: ghim thêm guava (AGP kéo bản cũ) — cùng bản với APK.
// Phiên bản lấy từ `gradle/libs.versions.toml` — không ghi số ở đây.
buildscript {
    val catalog = project.the<VersionCatalogsExtension>().named("libs")
    fun pinned(name: String) = catalog.findVersion(name).get().requiredVersion
    val bc = pinned("bouncycastle")
    val guava = pinned("guava")
    configurations.configureEach {
        if (name == "classpath") {
            resolutionStrategy.force(
                "org.bouncycastle:bcpkix-jdk18on:$bc",
                "org.bouncycastle:bcprov-jdk18on:$bc",
                "org.bouncycastle:bcprov-jdk15to18:$bc",
                "org.bouncycastle:bcutil-jdk18on:$bc",
                "com.google.guava:guava:$guava",
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
