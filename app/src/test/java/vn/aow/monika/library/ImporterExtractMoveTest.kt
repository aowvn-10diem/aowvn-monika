package vn.aow.monika.library

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.AppGraph
import vn.aow.monika.diag.Diagnostics
import vn.aow.monika.ui.TestApp
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Giải nén tại chỗ: chuyển tệp ra thư mục game **trước** khi xoá thư mục tạm.
 * `renameTo` hỏng mà vẫn xoá tạm là mất luôn tệp vừa giải nén của người chơi (không có bản sao).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class ImporterExtractMoveTest {
    @get:Rule val tmp = TemporaryFolder()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @Before fun setUp() {
        AppGraph.init(app)
        File(app.filesDir, "diag").deleteRecursively()
        Diagnostics.resetOpThrottle()
        Diagnostics.opSender = { _, _ -> }
    }

    @After fun tearDown() {
        Diagnostics.opSender = null
        Diagnostics.resetOpThrottle()
    }

    /** Thư mục game chứa sẵn một file nén có đúng một tệp [entry]. */
    private fun gameDirWithZip(entry: String): Pair<File, File> {
        val dir = File(tmp.root, "game").apply { mkdirs() }
        val archive = File(dir, "game.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry(entry))
            zip.write("du lieu nguoi choi".toByteArray())
            zip.closeEntry()
        }
        return dir to archive
    }

    @Test fun chuyenThatBaiThiGiuThuMucTamVaBaoLoi() {
        val (dir, archive) = gameDirWithZip("noi-dung.txt")
        // Đích đã là một thư mục khác rỗng cùng tên ⇒ renameTo(tệp → thư mục) hỏng trên cả Windows và Linux.
        File(File(dir, "noi-dung.txt").apply { mkdirs() }, "giu-cho.txt").writeText("x")

        val result = Importer.extractInPlace(dir, passwords = emptyList(), context = app)

        assertNotNull("người chơi phải nhận lỗi, không im lặng", result.error)
        assertTrue("tệp đã giải nén phải còn trong thư mục tạm",
            File(File(dir, ".dang-giai-nen"), "noi-dung.txt").exists())
        assertTrue("file nén gốc phải còn để thử lại", archive.exists())
        assertEquals("phải có báo cáo op-fail kèm bước chuyển", 1, Diagnostics.list(app).count {
            it.kind == "op-fail" && it.component == "op:giai-nen" &&
                it.detail.contains("bước: chuyen-ra-thu-muc-game")
        })
    }

    @Test fun chuyenThanhCongThiXoaThuMucTamVaFileNen() {
        val (dir, archive) = gameDirWithZip("rom.3ds")

        val result = Importer.extractInPlace(dir, passwords = emptyList(), context = app)

        assertNull(result.error)
        assertTrue("tệp phải nằm trong thư mục game", File(dir, "rom.3ds").exists())
        assertTrue("thư mục tạm phải được dọn", !File(dir, ".dang-giai-nen").exists())
        assertTrue("file nén đã giải nén xong thì xoá", !archive.exists())
    }
}
