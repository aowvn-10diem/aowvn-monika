import org.gradle.api.artifacts.VersionCatalogsExtension

// V192: ghim các thư viện chỉ nằm trên classpath plugin Gradle (AGP kéo bouncycastle cũ,
// grpc-netty kéo netty cũ, Kover kéo freemarker cũ). Phiên bản lấy từ
// `gradle/libs.versions.toml` — không ghi số ở đây; nâng cấp = sửa catalog.
buildscript {
    val catalog = project.the<VersionCatalogsExtension>().named("libs")
    fun pinned(name: String) = catalog.findVersion(name).get().requiredVersion
    val bc = pinned("bouncycastle")
    val netty = pinned("netty")
    val freemarker = pinned("freemarker")
    configurations.configureEach {
        if (name == "classpath") {
            resolutionStrategy {
                force(
                    "org.bouncycastle:bcpkix-jdk18on:$bc",
                    "org.bouncycastle:bcprov-jdk18on:$bc",
                    "org.bouncycastle:bcprov-jdk15to18:$bc",
                    "org.bouncycastle:bcutil-jdk18on:$bc",
                    "org.freemarker:freemarker:$freemarker",
                )
                // Cả họ netty phải cùng phiên bản (handler, codec, codec-http, codec-http2, ...).
                eachDependency { if (requested.group == "io.netty") useVersion(netty) }
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
