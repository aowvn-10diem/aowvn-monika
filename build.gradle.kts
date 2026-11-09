import org.gradle.api.artifacts.VersionCatalogsExtension

// V192: ghim các thư viện chỉ nằm trên classpath plugin Gradle (AGP kéo bouncycastle cũ,
// grpc-netty kéo netty cũ, Kover kéo freemarker cũ). Phiên bản lấy từ
// `gradle/libs.versions.toml` — không ghi số ở đây; nâng cấp = sửa catalog.
// V0811: ghim thêm các gói AGP kéo bản cũ (chỉ công cụ build, không vào APK) — gồm cả guava.
buildscript {
    val catalog = project.the<VersionCatalogsExtension>().named("libs")
    fun pinned(name: String) = catalog.findVersion(name).get().requiredVersion
    val bc = pinned("bouncycastle")
    val guava = pinned("guava")
    val netty = pinned("netty")
    val freemarker = pinned("freemarker")
    val commonsIo = pinned("commonsIo")
    val jdom2 = pinned("jdom2")
    val jose4j = pinned("jose4j")
    val protobufJava = pinned("protobufJava")
    configurations.configureEach {
        if (name == "classpath") {
            resolutionStrategy {
                force(
                    "org.bouncycastle:bcpkix-jdk18on:$bc",
                    "org.bouncycastle:bcprov-jdk18on:$bc",
                    "org.bouncycastle:bcprov-jdk15to18:$bc",
                    "org.bouncycastle:bcutil-jdk18on:$bc",
                    "com.google.guava:guava:$guava",
                    "org.freemarker:freemarker:$freemarker",
                    "commons-io:commons-io:$commonsIo",
                    "org.jdom:jdom2:$jdom2",
                    "org.bitbucket.b_c:jose4j:$jose4j",
                    "com.google.protobuf:protobuf-java:$protobufJava",
                    "com.google.protobuf:protobuf-java-util:$protobufJava",
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
