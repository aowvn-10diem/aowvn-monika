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
import vn.aow.monika.library.GameDetector
import vn.aow.monika.library.GameStorage
import vn.aow.monika.library.Importer
import vn.aow.monika.ui.TestApp
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class CorePreparationCacheTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val requests = mutableListOf<String>()
    private var offline = false
    private val rom = ByteArray(1024) { (it % 251).toByte() } // Dữ liệu tự sinh, không có ROM/BIOS thật.
    private fun zip(name: String, bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { z -> z.putNextEntry(ZipEntry(name)); z.write(bytes); z.closeEntry() }
    }.toByteArray()

    private fun client() = OkHttpClient.Builder().addInterceptor { chain ->
        requests += chain.request().url.encodedPath
        if (offline) throw IOException("offline fixture")
        val payload = if (chain.request().url.encodedPath.startsWith("/system")) zip("nds/bios.bin", rom)
            else zip("melondsds_libretro_android.so", ByteArray(64) { 1 })
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(payload.toResponseBody()).build()
    }.build()

    private fun config(system: String = "system.zip"): ConfigRepository {
        val version = ConfigRepository(app, OkHttpClient()).current.configVersion
        File(app.filesDir, ConfigRepository.ASSET_NAME).writeText("""{"configVersion":$version,"cores":{"melondsds":{"version":"one","url":"https://example.test/core-{abi}.zip","systemFiles":"https://example.test/$system"}},"systems":[{"id":"nds","name":"NDS","extensions":["nds"],"runner":"libretro","core":"melondsds"}]}""")
        return ConfigRepository(app, OkHttpClient())
    }

    @Before fun clean() {
        listOf("cores", "system", ConfigRepository.ASSET_NAME, ConfigRepository.ASSET_NAME + ".bak", ConfigRepository.ASSET_NAME + ".new")
            .forEach { File(app.filesDir, it).deleteRecursively() }
        GameStorage.games(app).listFiles().orEmpty().forEach { it.deleteRecursively() }
        app.applicationInfo.nativeLibraryDir = "/fake/arm64"
        requests.clear(); offline = false
    }

    private fun onMain(block: suspend () -> Unit) = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try { block() } finally { Dispatchers.resetMain() }
    }

    @Test fun nextLaunchAndNewManagerReuseCoreBiosAndExtractedRomWithoutNetworkOrExtraction() = onMain {
        val cfg = config()
        val core = CoreManager(app, client(), cfg).ensureCore("melondsds")
        assertEquals(2, requests.size)
        val archive = File(app.cacheDir, "v97-synthetic.zip").apply { writeBytes(zip("Game.nds", rom)) }
        val imported = Importer.importFile(app, archive, deleteSource = true, passwords = emptyList())
        assertNull(imported.error); assertFalse(archive.exists())
        val game = GameDetector.detect(imported.dir, cfg.current)
        val entry = game.entry!!
        val timestamp = entry.lastModified()
        val bios = File(app.filesDir, "system/nds/bios.bin")
        val biosTimestamp = bios.lastModified()
        offline = true
        repeat(3) {
            val manager = CoreManager(app, client(), config())
            assertTrue(manager.isReady("melondsds"))
            assertEquals(core.canonicalPath, manager.ensureCore("melondsds").canonicalPath)
            val reopened = GameDetector.detect(imported.dir, cfg.current)
            assertFalse(reopened.needsExtract)
            assertEquals("nds", reopened.system?.id)
            assertEquals(entry.canonicalPath, reopened.entry!!.canonicalPath)
            assertArrayEquals(rom, reopened.entry!!.readBytes())
        }
        assertEquals(2, requests.size)
        assertEquals(timestamp, entry.lastModified()); assertEquals(biosTimestamp, bios.lastModified())
    }

    @Test fun missingBiosOrChangedSystemUrlRebuildsOnlySystemCache() = onMain {
        val manager = CoreManager(app, client(), config())
        manager.ensureCore("melondsds")
        File(app.filesDir, "system/nds/bios.bin").delete()
        assertFalse(manager.isReady("melondsds"))
        manager.ensureCore("melondsds")
        assertEquals(listOf("/core-arm64-v8a.zip", "/system.zip", "/system.zip"), requests)
        val next = CoreManager(app, client(), config("system-new.zip"))
        assertFalse(next.isReady("melondsds"))
        next.ensureCore("melondsds")
        assertEquals("/system-new.zip", requests.last())
        assertEquals(1, requests.count { it.startsWith("/core-") })
    }

    @Test fun emptyCachedCoreCannotBeReportedReady() = onMain {
        val manager = CoreManager(app, client(), config())
        val file = manager.ensureCore("melondsds")
        file.writeBytes(byteArrayOf())
        assertFalse(manager.isReady("melondsds"))
        manager.ensureCore("melondsds")
        assertTrue(manager.isReady("melondsds"))
        assertEquals(2, requests.count { it.startsWith("/core-") })
        assertEquals(1, requests.count { it.startsWith("/system") })
    }
}
