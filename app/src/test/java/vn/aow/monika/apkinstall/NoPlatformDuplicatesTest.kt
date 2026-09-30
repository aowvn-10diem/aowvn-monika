package vn.aow.monika.apkinstall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

/**
 * Chống lặp lại lỗi IncompatibleClassChangeError (v0.4.5): thư viện đóng kèm bản sao lớp CỦA ANDROID
 * (org.xmlpull, android.util.AttributeSet...) bị R8 đổi tên → app gọi nhầm ngay khi mở. Jar ARSCLib đã lọc phải sạch.
 */
class NoPlatformDuplicatesTest {
    @Test fun strippedArscLibHasNoAndroidPlatformClasses() {
        val jar = File("build/stripped-libs/ARSCLib-stripped.jar")
        assertTrue("Thiếu ${jar.path} (tác vụ arscStripped chưa chạy)", jar.isFile)
        val bad = ZipFile(jar).use { z -> z.entries().asSequence().map { it.name }.filter { it.endsWith(".class") && (it.startsWith("org/xmlpull/") || it.startsWith("android/") || it.startsWith("javax/") || it.startsWith("java/")) }.toList() }
        assertEquals("Lớp trùng với hệ thống trong jar: $bad", emptyList<String>(), bad)
    }
}
