package vn.aow.monika.library

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.AppGraph
import vn.aow.monika.diag.Diagnostics
import vn.aow.monika.ui.TestApp
import java.io.File

/**
 * Nhập một tệp **không phải** file nén mà chép hỏng (nguồn biến mất, hết dung lượng, thiếu quyền) từng không
 * để lại báo cáo nào — chỉ hiện một câu lỗi chung. Nay phải thành `op-fail` `op:nhap-game` kèm bước đang chạy.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class ImporterOpStepTest {
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

    @Test fun nhapTepThuongHongThiBaoCaoCoTenThaoTacVaBuoc() {
        // Tệp nguồn không tồn tại: renameTo hỏng, rồi inputStream() ném FileNotFoundException.
        val missing = File(app.cacheDir, "game-khong-co.bin")

        val thrown = runCatching { Importer.importFile(app, missing, deleteSource = false, passwords = emptyList()) }

        assertTrue("lỗi vẫn phải ném lại cho giao diện", thrown.isFailure)
        val reports = Diagnostics.list(app).filter { it.kind == "op-fail" && it.component == "op:nhap-game" }
        assertEquals("đúng một báo cáo cho một lần nhập hỏng", 1, reports.size)
        assertTrue(
            "báo cáo phải nêu bước đang chạy, không chỉ tên thao tác",
            reports.first().detail.contains("bước: chep-file"),
        )
    }
}
