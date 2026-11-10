package vn.aow.monika

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * V144/V145 lô 1 — hai fixture giả của crash-worker nay nằm ở **resources công khai của app**
 * (`app/src/test/resources/`), là nguồn duy nhất cho `CrashContractTest` (Kotlin) và
 * `cloudflare/crash-worker/worker.test.js` (Node).
 *
 * Test này khoá **đúng byte/SHA** để lần chuyển kho sau không âm thầm đổi dữ liệu mẫu, và để
 * clone công khai vẫn kiểm được hợp đồng app ↔ Worker mà không cần thư mục `cloudflare/`.
 */
class CrashFixtureContractTest {

    private fun fixtureBytes(name: String): ByteArray =
        javaClass.classLoader!!.getResourceAsStream(name)?.use { it.readBytes() }
            ?: error("Thiếu fixture công khai: $name")

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    /** `app-report.json` phải ở **gốc classpath** cho `CrashContractTest`, đúng byte như bản cũ. */
    @Test fun appReportFixtureStaysAtClasspathRootWithTheSameBytes() {
        val bytes = fixtureBytes("app-report.json")
        assertEquals("Kích thước fixture không được đổi", 816, bytes.size)
        assertEquals(
            "Byte/SHA của app-report.json không được đổi",
            "9036b469a944ac084100cbfd367e0a95955619df89ce414f7be5708d34fd7893",
            sha256(bytes),
        )
    }

    /** Ảnh mẫu giữ nguyên byte (test Worker so byte sau POST → KV → GET) và vẫn là JPEG thật. */
    @Test fun userImageFixtureKeepsTheSameBytesAndIsAJpeg() {
        val bytes = fixtureBytes("user-image.jpg")
        assertEquals("Kích thước fixture không được đổi", 637, bytes.size)
        assertEquals(
            "Byte/SHA của user-image.jpg không được đổi",
            "22c3225e8dbce468d1ed494a6a5ffbba98bc0d4ca1109c5830fd22fd8e2cab03",
            sha256(bytes),
        )
        assertTrue(
            "Phải là JPEG thật (FF D8 FF)",
            bytes.size >= 3 &&
                bytes[0] == 0xFF.toByte() &&
                bytes[1] == 0xD8.toByte() &&
                bytes[2] == 0xFF.toByte(),
        )
    }
}
