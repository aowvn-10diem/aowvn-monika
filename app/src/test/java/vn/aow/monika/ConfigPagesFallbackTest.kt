package vn.aow.monika

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.config.ConfigEndpoints
import vn.aow.monika.config.ConfigRepository
import vn.aow.monika.diag.Diagnostics
import vn.aow.monika.ui.TestApp
import java.io.File

/**
 * V181: app đọc cấu hình từ GitHub Pages trước, chỉ gọi Worker khi Pages lỗi.
 * Test đếm đúng URL đã gọi để chắc chắn Pages tốt thì KHÔNG chạm Worker.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class ConfigPagesFallbackTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val cache get() = File(app.filesDir, ConfigRepository.ASSET_NAME)
    private val bundled get() = ConfigRepository.parse(
        app.assets.open(ConfigRepository.ASSET_NAME).bufferedReader().use { it.readText() }
    ).configVersion
    private val calls = mutableListOf<String>()

    private fun body(version: Int) = """{"configVersion":$version,"feed":{"url":"https://example.test/feed"}}"""

    private fun repo(answer: (String) -> Pair<Int, String>): ConfigRepository =
        ConfigRepository(app, OkHttpClient.Builder().addInterceptor { chain ->
            val url = chain.request().url.toString()
            calls += url
            val (code, text) = answer(url)
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message(if (code == 200) "OK" else "Lỗi")
                .body(text.toResponseBody("application/json".toMediaType())).build()
        }.build())

    @Before fun clean() {
        calls.clear()
        // Không để bộ chống spam op-fail dùng chung ảnh hưởng test khác (cùng mã lỗi "configVersion thấp hơn").
        Diagnostics.resetOpThrottle()
        listOf(
            cache, File(cache.path + ".new"), File(cache.path + ".bak"),
            File(app.filesDir, "config.checked"), File(app.filesDir, "diag"),
        ).forEach { it.deleteRecursively() }
    }

    @After fun clearOpThrottle() = Diagnostics.resetOpThrottle()

    /** (a) Pages trả JSON hợp lệ → dùng luôn, không gọi Worker. */
    @Test fun pagesHopLeDuocDungVaKhongGoiWorker() = runBlocking {
        val r = repo { url ->
            if (url == ConfigEndpoints.PAGES_CONFIG_URL) 200 to body(bundled + 1)
            else 200 to body(bundled + 9)
        }
        assertTrue(r.refresh(force = true).isSuccess)
        assertEquals(bundled + 1, r.current.configVersion)
        assertEquals("chỉ được gọi Pages", listOf(ConfigEndpoints.PAGES_CONFIG_URL), calls)
    }

    /** (b) Pages lỗi HTTP → rơi về Worker. */
    @Test fun pagesLoiThiGoiWorker() = runBlocking {
        val r = repo { url ->
            if (url == ConfigEndpoints.PAGES_CONFIG_URL) 500 to "hỏng"
            else 200 to body(bundled + 2)
        }
        assertTrue(r.refresh(force = true).isSuccess)
        assertEquals(bundled + 2, r.current.configVersion)
        assertEquals(listOf(ConfigEndpoints.PAGES_CONFIG_URL, ConfigEndpoints.WORKER_CONFIG_URL), calls)
    }

    /** (b) Pages trả JSON hỏng cũng phải rơi về Worker, không làm hỏng cấu hình đang chạy. */
    @Test fun pagesSaiJsonThiGoiWorker() = runBlocking {
        val r = repo { url ->
            if (url == ConfigEndpoints.PAGES_CONFIG_URL) 200 to "{không phải json"
            else 200 to body(bundled + 3)
        }
        assertTrue(r.refresh(force = true).isSuccess)
        assertEquals(bundled + 3, r.current.configVersion)
        assertEquals(2, calls.size)
    }

    /** (c) Cả hai lỗi → giữ cấu hình đang dùng, không ghi cache. */
    @Test fun caHaiLoiThiGiuConfigDangDung() = runBlocking {
        val r = repo { 503 to "hỏng" }
        assertTrue(r.refresh(force = true).isFailure)
        assertEquals(bundled, r.current.configVersion)
        assertFalse("không ghi cache khi cả hai nguồn lỗi", cache.exists())
    }

    /** (d) Bản lùi phiên bản bị bỏ qua: cả hai nguồn thấp hơn APK → không áp dụng. */
    @Test fun configVersionThapHonBiBoQua() = runBlocking {
        val r = repo { 200 to body(bundled - 1) }
        assertTrue(r.refresh(force = true).isFailure)
        assertEquals(bundled, r.current.configVersion)
        assertFalse("không ghi cache bản lùi phiên bản", cache.exists())
    }

    /** Pages lùi phiên bản (đồng bộ chậm) không phải lỗi mạng → vẫn thử Worker. */
    @Test fun pagesLuiPhienBanThiGoiWorker() = runBlocking {
        val r = repo { url ->
            if (url == ConfigEndpoints.PAGES_CONFIG_URL) 200 to body(bundled - 1)
            else 200 to body(bundled + 4)
        }
        assertTrue(r.refresh(force = true).isSuccess)
        assertEquals(bundled + 4, r.current.configVersion)
        assertEquals(2, calls.size)
    }
}
