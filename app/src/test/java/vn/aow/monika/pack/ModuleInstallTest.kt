package vn.aow.monika.pack

import android.app.Application
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import vn.aow.monika.azahar.AzaharModule
import vn.aow.monika.config.ConfigRepository
import vn.aow.monika.ui.TestApp
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class ModuleInstallTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private fun elf(machine: Int = 183) = ByteArray(64).apply {
        this[0] = 127; this[1] = 69; this[2] = 76; this[3] = 70; this[4] = 2; this[5] = 1
        this[18] = machine.toByte(); this[19] = (machine shr 8).toByte()
    }
    private fun zip(main: String, data: ByteArray = elf(), extra: Map<String, ByteArray> = emptyMap()): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for ((name, bytes) in mapOf(main to data) + extra) { z.putNextEntry(ZipEntry(name)); z.write(bytes); z.closeEntry() }
        }
        return out.toByteArray()
    }
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private var archive = ByteArray(0)
    private var failNetwork = false
    private var downloads = 0
    private fun interruptedBody() = object : ResponseBody() {
        override fun contentType(): MediaType? = null
        override fun contentLength() = archive.size.toLong()
        override fun source() = object : Source {
            private var offset = 0
            override fun read(sink: Buffer, byteCount: Long): Long {
                if (offset >= archive.size / 2) throw IOException("mất mạng giữa chừng")
                val n = minOf(byteCount.toInt(), archive.size / 2 - offset)
                sink.write(archive, offset, n); offset += n; return n.toLong()
            }
            override fun timeout() = Timeout.NONE
            override fun close() {}
        }.buffer()
    }
    private fun http() = OkHttpClient.Builder().addInterceptor { chain ->
        synchronized(this) { downloads++ }
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(if (failNetwork) interruptedBody() else archive.toResponseBody()).build()
    }.build()
    private fun config(id: String, expected: String): ConfigRepository {
        val base = ConfigRepository(app, OkHttpClient()).current.configVersion
        File(app.filesDir, ConfigRepository.ASSET_NAME).writeText("""{"configVersion":$base,"modules":{"$id":{"version":"2","url":"https://example.test/pack.zip","abis":["arm64-v8a"],"sha256":"$expected"}}}""")
        return ConfigRepository(app, OkHttpClient())
    }
    private fun old(dir: File, main: String) { File(dir, main).apply { parentFile!!.mkdirs(); writeText("old-good") }; File(dir, "version").writeText("1") }
    private fun assertOld(dir: File, main: String) { assertEquals("old-good", File(dir, main).readText()); assertEquals("1", File(dir, "version").readText()) }

    @Before fun clean() {
        listOf("packs", "engines", ConfigRepository.ASSET_NAME, ConfigRepository.ASSET_NAME + ".bak", ConfigRepository.ASSET_NAME + ".new", "diag").forEach { File(app.filesDir, it).deleteRecursively() }
        app.applicationInfo.nativeLibraryDir = "/fake/arm64"
        ReflectionHelpers.setStaticField(Build::class.java, "SUPPORTED_ABIS", arrayOf("arm64-v8a"))
        failNetwork = false; downloads = 0
    }

    @Test fun simpleNetworkHashAbiMissingFileAndZipSlipPreserveOld() = runBlocking {
        val main = "lib/main.so"; archive = zip(main)
        val module = SimpleModule(app, http(), config("fake", hash(archive)))
        val dir = module.dir("fake"); old(dir, main)
        failNetwork = true; assertTrue(runCatching { module.ensure("fake", main) }.isFailure); assertOld(dir, main)
        failNetwork = false; archive = zip(main, elf(40))
        assertTrue(runCatching { module.ensure("fake", main) }.isFailure); assertOld(dir, main) // Hash không khớp.
        for (bad in listOf(zip(main, elf(40)), zip("missing.so"), zip(main, extra = mapOf("../escape" to byteArrayOf(1))),
            zip(main, extra = mapOf("needed.txt" to "libnotshipped.so".toByteArray())),
            zip(main, extra = mapOf("manifest.json" to """{"abi":"arm64-v8a","loadOrder":["missing.so"]}""".toByteArray())))) {
            archive = bad
            val candidate = SimpleModule(app, http(), config("fake", hash(bad)))
            assertTrue(runCatching { candidate.ensure("fake", main) }.isFailure); assertOld(dir, main)
        }
    }

    @Test(timeout = 20000) fun simpleConcurrentEnsureDownloadsOnceAndPromotesWholePackage() = runBlocking {
        val main = "lib/main.so"; archive = zip(main)
        val cfg = config("fake", hash(archive)); val client = http()
        val one = SimpleModule(app, client, cfg); val two = SimpleModule(app, client, cfg)
        old(one.dir("fake"), main)
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val a = async(Dispatchers.IO) { one.ensure("fake", main) { entered.complete(Unit); runBlocking { release.await() } } }
        entered.await()
        val b = async(Dispatchers.IO) { two.ensure("fake", main) }
        assertOld(one.dir("fake"), main)
        release.complete(Unit); a.await(); b.await()
        assertEquals(1, downloads); assertTrue(one.ready("fake", main))
        assertArrayEquals(elf(), File(one.dir("fake"), main).readBytes())
    }

    @Test fun azaharBadHashAndWrongAbiKeepOldThenValidPackageReplaces() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        try {
            val main = "libcitra-android.so"; val dir = File(app.filesDir, "engines/azahar")
            archive = zip(main); old(dir, main)
            val module = AzaharModule(app, http(), config("azahar", "0".repeat(64)))
            assertTrue(runCatching { module.ensure() }.isFailure); assertOld(dir, main)
            archive = zip(main, elf(40))
            assertTrue(runCatching { AzaharModule(app, http(), config("azahar", hash(archive))).ensure() }.isFailure); assertOld(dir, main)
            archive = zip(main)
            val good = AzaharModule(app, http(), config("azahar", hash(archive)))
            good.ensure(); assertTrue(good.ready()); assertArrayEquals(elf(), File(dir, main).readBytes())
        } finally { Dispatchers.resetMain() }
    }
}
