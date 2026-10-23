package vn.aow.monika

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.config.ConfigRepository
import vn.aow.monika.library.GameLibrary
import vn.aow.monika.library.GameStorage
import vn.aow.monika.ui.TestApp
import java.io.File

/**
 * V132: cấu hình từ xa tăng bản thì Thư viện đọc lại ngay (không cần khởi động lại);
 * bản thấp hơn bản đang dùng bị bỏ qua, không ghi đè bản đang chạy.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class ConfigUpdateTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val cache get() = File(app.filesDir, ConfigRepository.ASSET_NAME)
    private val bundled get() = ConfigRepository.parse(
        app.assets.open(ConfigRepository.ASSET_NAME).bufferedReader().use { it.readText() }
    ).configVersion
    private var reply = ""

    private fun repo() = ConfigRepository(app, OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(ResponseBody.create(null, reply)).build()
    }.build())

    /** Config chỉ đổi phần `systems`: thêm hệ "V132" nhận đuôi .v132rom. */
    private fun payload(version: Int) =
        """{"configVersion":$version,"systems":[{"id":"v132","name":"V132","runner":"libretro","extensions":["v132rom"]}]}"""

    @Before fun clean() {
        listOf(cache, File(cache.path + ".new"), File(cache.path + ".bak"), File(app.filesDir, "config.checked"), File(app.filesDir, "diag"))
            .forEach { it.deleteRecursively() }
        File(app.filesDir, "device-games.json").delete()
        GameStorage.games(app).deleteRecursively()
        File(GameStorage.games(app), "V132 Game").mkdirs()
        File(GameStorage.games(app), "V132 Game/game.v132rom").writeBytes(ByteArray(2_000) { 1 })
    }

    /** Hệ nhận diện được của game trong Thư viện; null = chưa nhận ra (đúng như trước khi có config mới). */
    private fun detected(repo: ConfigRepository) =
        GameLibrary(app, repo).list().firstOrNull { it.name == "V132 Game" }?.system?.id

    @Test fun higherVersionRefreshesLibraryWithoutRestart() = runBlocking {
        val repo = repo()
        assertNull(detected(repo))

        reply = payload(bundled + 1)
        assertTrue(repo.refresh(force = true).isSuccess)
        assertEquals(bundled + 1, repo.config.value.configVersion)
        assertEquals("v132", detected(repo))
    }

    @Test fun lowerVersionIsIgnoredAndCacheIsNotOverwritten() = runBlocking {
        val repo = repo()
        reply = payload(bundled - 1)
        repo.refresh(force = true)

        assertEquals(bundled, repo.config.value.configVersion)
        assertNull(detected(repo))
        assertTrue(!cache.exists())
    }
}
