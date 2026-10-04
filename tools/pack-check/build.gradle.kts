plugins { alias(libs.plugins.kotlin.jvm); application }
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    // Biên dịch trực tiếp mã app; không sao chép hay viết lại thuật toán kiểm gói.
    sourceSets.main {
        kotlin.srcDir(providers.gradleProperty("packSourceRoot").getOrElse("../../app/src/main/java"))
        kotlin.include("vn/aow/monika/pack/PackTransaction.kt", "vn/aow/monika/pack/CheckPacks.kt")
    }
    sourceSets.test {
        kotlin.srcDir("../../app/src/test/java")
        kotlin.include("vn/aow/monika/pack/PackTransactionTest.kt")
        resources.srcDir("../../app/src/test/resources")
    }
}
dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
application { mainClass.set("vn.aow.monika.pack.CheckPacksKt") }

java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
