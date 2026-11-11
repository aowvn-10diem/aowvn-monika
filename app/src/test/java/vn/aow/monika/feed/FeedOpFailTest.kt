package vn.aow.monika.feed

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.config.ConfigRepository
import vn.aow.monika.diag.Breadcrumbs
import vn.aow.monika.diag.Diagnostics
import vn.aow.monika.ui.TestApp
import java.io.File

/**
 * #305/#512: tải feed hỏng từng dồn 1400 lượt báo cáo `op:tai-feed` vì mọi mã HTTP và mọi `!!` đều thành lỗi app.
 * Nay 404 (bài đã xoá) và JSON lệch hình dạng là luồng thường (không lập báo cáo), chỉ 5xx mới là lỗi thật;
 * mỗi đường đọc có bước riêng. Test dùng OkHttp interceptor, không gọi mạng thật.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class FeedOpFailTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private var now = 1_000_000L
    private val sent = java.util.Collections.synchronizedList(mutableListOf<Diagnostics.Report>())

    @Before fun setUp() {
        File(app.filesDir, "diag").deleteRecursively()
        Diagnostics.pending.value = null
        Diagnostics.resetOpThrottle()
        now = 1_000_000L
        Diagnostics.opClock = { now }
        sent.clear()
        Diagnostics.opSender = { _, r -> sent += r }
    }

    @After fun tearDown() {
        Diagnostics.opClock = { System.currentTimeMillis() }
        Diagnostics.opSender = null
        Diagnostics.resetOpThrottle()
    }

    private fun opFails() = Diagnostics.list(app).filter { it.kind == "op-fail" }

    /** Repo trả đúng 1 mã HTTP + body cố định cho mọi request (không ra mạng). */
    private fun repo(code: Int, body: String = """{"feed":{"entry":[]}}"""): FeedRepository {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("test")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        return FeedRepository(http, ConfigRepository(app, http), ctx = app)
    }

    @Test fun baiDaBiGo404KhongTaoBaoCao() = runBlocking {
        // Danh sách lưu đĩa còn id bài đã xoá → mở bài đó là 404, chuyện thường gặp nhất của người chơi.
        val error = runCatching { repo(404, """{"error":{"message":"Not Found"}}""").fetchPost("bai-cu") }.exceptionOrNull()
        assertTrue("phải là lỗi HTTP của feed, không phải IllegalStateException: $error", error is FeedHttpException)
        assertEquals(404, (error as FeedHttpException).code)
        assertTrue("4xx không được thành báo cáo lỗi app", opFails().isEmpty())
        assertTrue(sent.isEmpty())
        assertTrue(
            "nhưng vẫn phải để lại vệt để biết đã bỏ qua",
            Breadcrumbs.read(app, android.os.Process.myPid()).any { it.contains("tai-bai") && it.contains("không báo") },
        )
    }

    @Test fun loiMayChu500VanBaoOpFail() = runBlocking {
        runCatching { repo(500, """{"error":{"message":"Internal"}}""").fetchList("trang-chu") }
        val report = opFails().single()
        assertEquals("op:tai-feed", report.component)
        assertTrue("báo cáo phải ghi đúng bước: ${report.detail}", report.detail.contains("bước: tai-danh-sach"))
        assertTrue("mã lỗi phải là lỗi HTTP: ${report.detail}", report.detail.contains("FeedHttpException"))
        assertEquals(1, sent.size)
    }

    @Test fun moiDuongDocCoBuocRieng() = runBlocking {
        // Trước đây cả 4 đường dùng chung "tai-bai" nên không biết hỏng ở đâu; nay mỗi đường 1 bước.
        val shared = repo(503, """{"error":{}}""")
        runCatching { shared.fetchList("k") }
        runCatching { shared.fetchIndex(maxPages = 1) }
        runCatching { shared.fetchPost("bai") }
        runCatching { shared.fetchPage("https://www.aow.vn/p/tai-game.html") }
        val steps = opFails().map { it.reason.substringAfter('/').substringBefore(':') }.toSet()
        assertEquals(setOf("tai-danh-sach", "tai-chi-muc", "tai-bai", "tai-trang"), steps)
    }

    @Test fun jsonThieuEntryNemLoiShapeChuKhongNPE() = runBlocking {
        // Bài bị gỡ: Blogger trả JSON không có "entry" (trước đây là NPE ở `!!`).
        val post = runCatching { repo(200, """{"feed":{}}""").fetchPost("bai-day-du") }.exceptionOrNull()
        assertTrue("phải là FeedShapeException, thực tế: $post", post is FeedShapeException)
        assertTrue("bài bị gỡ không lập báo cáo", opFails().isEmpty())

        // Danh sách: mất hẳn "feed" (Blogger trả HTML/sai URL config) vẫn là lỗi thật → có báo cáo.
        val list = runCatching { repo(200, """{}""").fetchList("k") }.exceptionOrNull()
        assertTrue("thiếu \"feed\" phải là lỗi shape rõ ràng, thực tế: $list", list is FeedShapeException)
        val report = opFails().single()
        assertTrue("URL feed sai là lỗi thật: ${report.detail}", report.detail.contains("bước: tai-danh-sach"))
    }

    @Test fun feedRongVanLaDanhSachRong() = runBlocking {
        // Blogger bỏ "entry" khi chưa có bài — hợp lệ, không được coi là lỗi.
        assertTrue(repo(200, """{"feed":{}}""").fetchList("k").isEmpty())
        assertTrue(opFails().isEmpty())
    }
}
