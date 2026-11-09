package vn.aow.monika

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * [A95] + [A65]: chặn tái phát ở nguồn.
 *
 * Bản Play không được có câu chỉ dẫn trả tiền ngoài Google Play (anti-steering), không được
 * đóng gói dữ liệu chuyển khoản/QR, không được kéo lại quyền mà `:j2me` mang vào manifest,
 * và không được hiện lại thẻ tài khoản / mục bài viết. Test đỏ ngay khi ai thêm lại.
 *
 * Quét nguồn thay vì APK vì CI đã có `scripts/check-play-apk.py` quét artifact thật.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class PlayAntiSteeringTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    /** Từ khoá đã chốt: chữ có dấu, tên khoá và tiếng Anh. */
    private val bannedCopy = listOf(
        "ủng hộ", "Ủng hộ", "chuyển khoản", "Chuyển khoản",
        "vietqr", "VietQR", "donate", "Donate",
    )

    /** Thư mục module `app` (nơi có `src/play`), không phụ thuộc thư mục làm việc của Gradle. */
    private fun appModule(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "src/play").isDirectory) dir = dir.parentFile
        return dir ?: error("Không tìm thấy thư mục app/src/play từ ${File("").absolutePath}")
    }

    private fun sources(relative: String): List<File> =
        File(appModule(), relative).walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "xml") }
            .toList()

    @Test fun playAndSharedSourcesHaveNoDonationCopy() {
        val root = appModule()
        val hits = mutableListOf<String>()
        listOf("src/play", "src/main").forEach { relative ->
            sources(relative).forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    bannedCopy.firstOrNull { line.contains(it) }
                        ?.let { hits += "${file.relativeTo(root).invariantSeparatorsPath}:${index + 1}: $it" }
                }
            }
        }
        assertTrue("Bản Play còn chuỗi mời trả tiền ngoài cửa hàng:\n" + hits.joinToString("\n"), hits.isEmpty())
    }

    @Test fun playCatalogCarriesNoDonationData() {
        val json = app.assets.open("monika-config.json").bufferedReader().use { it.readText() }
        bannedCopy.forEach { assertFalse("Catalog Play còn chuỗi \"$it\"", json.contains(it)) }
        listOf("donateUrl", "voteUrl", "memoPrefix", "accountNo", "bankBin", "img.vietqr.io").forEach {
            assertFalse("Catalog Play còn trường/địa chỉ $it", json.contains(it))
        }
    }

    @Test fun playManifestRemovesPermissionsDraggedByJ2me() {
        val manifest = File(appModule(), "src/play/AndroidManifest.xml").readText()
        listOf(
            "android.permission.RECORD_AUDIO",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.BLUETOOTH",
            "android.permission.BLUETOOTH_ADMIN",
            "android.permission.BLUETOOTH_ADVERTISE",
            "android.permission.BLUETOOTH_CONNECT",
            "android.permission.BLUETOOTH_SCAN",
            "com.android.launcher.permission.INSTALL_SHORTCUT",
        ).forEach { permission ->
            val block = Regex("""<uses-permission[^>]*android:name="${Regex.escape(permission)}"[^>]*/>""")
                .find(manifest)?.value
            assertNotNull("Bản Play phải gỡ quyền $permission", block)
            assertTrue("Quyền $permission phải có tools:node=\"remove\"", block!!.contains("tools:node=\"remove\""))
        }
    }

    @Test fun playScreensHideAccountCardAndArticles() {
        val settings = File(appModule(), "src/play/java/vn/aow/monika/ui/screens/PlaySettings.kt").readText()
        assertFalse("Cài đặt bản Play còn thẻ tài khoản AowVN", settings.contains("AccountCard"))
        val main = File(appModule(), "src/play/java/vn/aow/monika/ui/MainActivity.kt").readText()
        assertFalse("Màn chính bản Play còn trình đọc bài viết", main.contains("PlayArticles"))
        assertFalse("Màn chính bản Play còn lối vào bài viết", main.contains("\"articles\""))
    }
}
