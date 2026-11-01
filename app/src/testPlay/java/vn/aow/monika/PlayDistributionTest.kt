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
        assertTrue(root.startsWith(app.filesDir.canonicalPath + "/"))
        assertFalse(GameStorage.isPublic(app))
    }
}
