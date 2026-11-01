package vn.aow.monika

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.config.ConfigRepository
import vn.aow.monika.ui.TestApp
import java.io.File

/** V157: cờ "cấu hình đến từ máy chủ" — chỉ khi có cờ này mới được chặn bản cũ. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class ConfigRemoteAppliedTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val cache get() = File(app.filesDir, ConfigRepository.ASSET_NAME)
    private val bundled get() = ConfigRepository.parse(app.assets.open(ConfigRepository.ASSET_NAME).bufferedReader().use { it.readText() }).configVersion
    private var reply = ""
    private fun repo() = ConfigRepository(app, OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(reply.toResponseBody("application/json".toMediaType())).build()
    }.build())
    private fun valid(version: Int) = """{"configVersion":$version,"feed":{"url":"https://example.test/feed"}}"""

    @Before fun clean() {
        listOf(cache, File(cache.path + ".new"), File(cache.path + ".bak"), File(app.filesDir, "config.checked"), File(app.filesDir, "diag"))
            .forEach { it.deleteRecursively() }
    }

    @Test fun freshInstallWithBundledConfigOnlyIsNotServerSourced() {
        assertFalse(repo().remoteApplied.value)
    }

    @Test fun cachedConfigFromEarlierDownloadCountsAsServerSourced() {
        cache.writeText(valid(bundled + 5))
        assertTrue(repo().remoteApplied.value)
    }

    @Test fun invalidCacheFallsBackToBundledAndIsNotServerSourced() {
        cache.writeText("""{"configVersion":${bundled + 5},"feed":{"url":"javascript:alert(1)"}}""")
        val r = repo()
        assertFalse(r.remoteApplied.value)
        assertFalse(r.current.configVersion == bundled + 5)
    }

    @Test fun successfulRefreshFlipsTheFlag() = runBlocking {
        val r = repo()
        assertFalse(r.remoteApplied.value)
        reply = valid(bundled + 10)
        assertTrue(r.refresh(force = true).isSuccess)
        assertTrue(r.remoteApplied.value)
    }
}
