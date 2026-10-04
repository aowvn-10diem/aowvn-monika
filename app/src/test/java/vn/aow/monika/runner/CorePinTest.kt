package vn.aow.monika.runner

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.config.ConfigRepository
import vn.aow.monika.ui.TestApp
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class CorePinTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private var requests = mutableListOf<String>()
    private var payload = byteArrayOf()
    private fun hash(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
    private fun zip(machine: Int = 183, file: String = "gambatte_libretro_android.so"): ByteArray {
        val bytes = ByteArray(64).apply {
            this[0] = 127; this[1] = 69; this[2] = 76; this[3] = 70
            this[4] = 2; this[5] = 1; this[18] = machine.toByte(); this[19] = (machine shr 8).toByte()
        }
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { it.putNextEntry(ZipEntry(file)); it.write(bytes); it.closeEntry() }
        return out.toByteArray()
    }
    private fun http() = OkHttpClient.Builder().addInterceptor { chain ->
        requests.add(chain.request().url.toString())
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(payload.toResponseBody()).build()
    }.build()
    private fun cfg(version: String, expected: String, abi: String = "arm64-v8a", pinned: Boolean = true): ConfigRepository {
        val base = ConfigRepository(app, OkHttpClient()).current.configVersion
        val artifacts = if (pinned) """, "artifacts":{"$abi":{"url":"https://example.test/$version-$abi.zip","sha256":"$expected","version":"$version"}}""" else ""
        File(app.filesDir, ConfigRepository.ASSET_NAME).writeText("""{"configVersion":$base,"cores":{"gambatte":{"version":"legacy","url":"https://example.test/latest/{abi}.zip","abis":["arm64-v8a","armeabi-v7a"]$artifacts}}}""")
        return ConfigRepository(app, OkHttpClient())
    }
    private val dir get() = File(app.filesDir, "cores/gambatte")
    private val main get() = File(dir, "gambatte_libretro_android.so")
    private fun old() { dir.mkdirs(); main.writeText("old-good"); File(dir, "version").writeText("old") }
    @Before fun clean() {
        for (path in listOf("cores", ConfigRepository.ASSET_NAME, ConfigRepository.ASSET_NAME + ".bak", ConfigRepository.ASSET_NAME + ".new")) File(app.filesDir, path).deleteRecursively()
        app.applicationInfo.nativeLibraryDir = "/fake/arm64"; requests.clear(); payload = zip()
    }
    private suspend fun <T> onMain(body: suspend () -> T): T {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try { return body() } finally { Dispatchers.resetMain() }
    }
    @Test fun pinnedUrlHashVersionAndConfigRollback() = runBlocking { onMain {
        val first = CoreManager(app, http(), cfg("one", hash(payload)))
        first.ensureCore("gambatte"); assertTrue(first.isReady("gambatte")); assertEquals("one", first.installedVersion("gambatte"))
        assertEquals("https://example.test/one-arm64-v8a.zip", requests.last())
        val second = CoreManager(app, http(), cfg("two", hash(payload)))
        assertFalse(second.isReady("gambatte")); second.ensureCore("gambatte"); assertEquals("two", second.installedVersion("gambatte"))
        // PM quay config về URL/hash/version cũ (configVersion vẫn tăng): tải lại đúng ảnh chụp một.
        val rollback = CoreManager(app, http(), cfg("one", hash(payload)))
        rollback.ensureCore("gambatte"); assertEquals("one", rollback.installedVersion("gambatte")); assertTrue(rollback.isReady("gambatte"))
    } }
    @Test fun wrongHashAbiOrMissingMainPreservesInstalledCore() = runBlocking { onMain {
        old()
        val manager = CoreManager(app, http(), cfg("new", "0".repeat(64)))
        assertTrue(runCatching { manager.ensureCore("gambatte") }.isFailure)
        assertEquals("old-good", main.readText()); assertEquals("old", manager.installedVersion("gambatte"))
        for (bad in listOf(zip(40), zip(file = "wrong.so"))) {
            payload = bad
            val next = CoreManager(app, http(), cfg("new", hash(bad)))
            assertTrue(runCatching { next.ensureCore("gambatte") }.isFailure)
            assertEquals("old-good", main.readText()); assertEquals("old", next.installedVersion("gambatte"))
        }
    } }
    @Test fun pinnedAbiNeverFallsBackToLatestAndAbisStillRestricts() = runBlocking { onMain {
        val noArtifact = CoreManager(app, http(), cfg("one", hash(payload), abi = "armeabi-v7a"))
        assertFalse(noArtifact.supports("gambatte")); assertTrue(noArtifact.missing(listOf("gambatte")).isEmpty())
        assertTrue(runCatching { noArtifact.ensureCore("gambatte") }.isFailure); assertTrue(requests.isEmpty())
    } }
    @Test fun legacyConfigKeepsUrlAndVersionBehavior() = runBlocking { onMain {
        val legacy = CoreManager(app, http(), cfg("unused", "", pinned = false))
        assertTrue(legacy.supports("gambatte")); legacy.ensureCore("gambatte")
        assertEquals("https://example.test/latest/arm64-v8a.zip", requests.single()); assertEquals("legacy", legacy.installedVersion("gambatte")); assertTrue(legacy.isReady("gambatte"))
    } }
}
