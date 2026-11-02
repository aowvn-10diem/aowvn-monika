package vn.aow.monika

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.config.ConfigRepository
import vn.aow.monika.library.GameStorage
import vn.aow.monika.runner.RgssCompat
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class PlayDistributionTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @Test fun apkDownloadIsDisabledInPlayBuild() {
        assertFalse(BuildConfig.APK_DOWNLOAD_ALLOWED)
    }

    @Test fun legacyCacheAndRefreshCannotRestoreExternalDistribution() = runBlocking {
        val cache = File(app.filesDir, ConfigRepository.ASSET_NAME)
        cache.writeText("""{"configVersion":999,"app":{"apkUrl":"https://example.test/update.apk"}}""")
        try {
            var requests = 0
            val http = OkHttpClient.Builder().addInterceptor {
                requests++
                throw AssertionError("Play không được hỏi Worker để nhận lại link tải ngoài")
            }.build()
            val repo = ConfigRepository(app, http)
            assertTrue(repo.refresh(force = true).isSuccess)
            assertEquals(0, requests)
            assertTrue(repo.current.app.apkUrl.isEmpty())
            assertTrue(repo.current.externalApps.isEmpty())
            assertTrue(repo.current.downloadHosts.isEmpty())
            assertTrue(repo.current.cores.values.all { it.url.isEmpty() && it.artifacts.isEmpty() })
            assertTrue(repo.current.modules.values.all { it.url.isEmpty() })
            assertEquals("com.aow.monika.play", BuildConfig.APPLICATION_ID)
        } finally { cache.delete() }
    }

    @Test fun importsStayInsidePrivateStorage() {
        val root = GameStorage.root(app).canonicalPath
        assertTrue(root.startsWith(app.filesDir.canonicalPath + File.separator))
        assertFalse(GameStorage.isPublic(app))
    }

    @Test fun attributionRemainsAvailableInPlayAssets() {
        val j2me = app.assets.open("licenses/j2me-loader.txt").bufferedReader().use { it.readText() }
        val icons = app.assets.open("licenses/fluent-system-icons.txt").bufferedReader().use { it.readText() }
        assertTrue(j2me.contains("Apache License"))
        assertTrue(icons.contains("Microsoft Corporation"))
    }

    @Test fun rgssCompatibilityScriptsCanBeLoaded() {
        val script = RgssCompat.ensure(app)
        assertTrue(script.readText().contains("module MonikaWin32"))
        assertTrue(app.assets.open("rgss/monika-ruby18.rb").use { it.readBytes().isNotEmpty() })
    }

    /**
     * [A78] V154: bản Play ký bằng Play App Signing — catalog Play để trống chứng thư nên bản Play
     * bỏ qua tự kiểm, người dùng Play không bị cảnh báo "app giả mạo" sai.
     */
    @Test fun playCatalogSkipsCertCheckUntilPlaySigningCertIsKnown() {
        val official = ConfigRepository(app, OkHttpClient()).current.security.officialCertSha256
        assertTrue(official, official.isEmpty())
        assertFalse(vn.aow.monika.security.OfficialCert.isForeignBuild("f".repeat(64), official, debugBuild = false))
    }
}
