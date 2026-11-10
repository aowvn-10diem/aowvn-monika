package vn.aow.monika

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.config.ConfigRepository
import vn.aow.monika.config.ConfigValidation
import vn.aow.monika.diag.Diagnostics
import vn.aow.monika.ui.TestApp
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class ConfigRepositoryValidationTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val cache get() = File(app.filesDir, ConfigRepository.ASSET_NAME)
    private val bundled get() = ConfigRepository.parse(app.assets.open(ConfigRepository.ASSET_NAME).bufferedReader().use { it.readText() }).configVersion
    private var reply = ""
    private var previousSender: ((android.content.Context, Diagnostics.Report) -> Unit)? = null
    private fun repo() = ConfigRepository(app, OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(ResponseBody.create(null, reply)).build()
    }.build())
    private fun valid(version: Int) = """{"configVersion":$version,"feed":{"url":"https://example.test/feed"}}"""

    @Before fun clean() {
        clearFiles()
        // Chỉ kiểm repository: không để báo lỗi tự gửi khởi tạo AppGraph từ cache giả thiếu dữ liệu.
        previousSender = Diagnostics.opSender
        Diagnostics.opSender = null
    }

    @After fun restore() {
        clearFiles()
        Diagnostics.opSender = previousSender
    }

    private fun clearFiles() {
        listOf(cache, File(cache.path + ".new"), File(cache.path + ".bak"), File(app.filesDir, "config.checked"), File(app.filesDir, "diag"))
            .forEach { it.deleteRecursively() }
    }

    @Test fun unsafeSaveProfilesAreRejectedBeforeCaching() {
        val payloads = listOf(
            """{"saveDirectory":"../games","sizes":[512]}""",
            """{"imports":{"sav":"run-code"},"sizes":[512]}""",
            """{"sizes":[512],"targetBytes":1024}""",
            """{"kind":"psp","treeDirectory":"../game","imports":{"zip":"psp-tree"}}""",
            """{"kind":"3ds","nativeDirectory":"/outside","imports":{"zip":"3ds-tree"}}""",
        )
        payloads.forEach { profile ->
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                ConfigValidation.parse("""{"saveTransfer":{"systems":{"gba":$profile}}}""", 0)
            }
        }
    }

    @Test fun oldCacheWithoutExperimentalFlagFallsBackToBundledWarning() {
        assertEquals(62, bundled)
        cache.writeText(valid(38))
        val current = repo().current
        assertEquals(62, current.configVersion)
        assertTrue(current.systems.single { it.id == "kirikiri" }.experimental)
    }

    @Test fun cache47WithoutConsoleSaveProfilesFallsBackToBundled62() {
        cache.writeText("""{"configVersion":47,"saveTransfer":{"systems":{}}}""")
        val current = repo().current
        assertEquals(62, current.configVersion)
        assertEquals(setOf("java", "nds", "gba", "3ds", "psp", "ps1"), current.saveTransfer.systems.keys)
    }

    @Test fun cache39FallsBackToBundledDocumentConfiguration() {
        cache.writeText("""{"configVersion":39,"documentExtensions":[]}""")
        val current = repo().current
        assertEquals(62, current.configVersion)
        assertEquals(listOf("pdf", "doc", "docx", "txt", "epub"), current.documentExtensions)
        assertTrue(current.systems.single { it.id == "kirikiri" }.experimental)
    }

    @Test fun cache55WithStable088FallsBackToBundledTrial0810() {
        cache.writeText("""{"configVersion":55,"app":{"latestVersionCode":53,"latestVersionName":"0.8.8","minVersionCode":1,"apkUrl":"https://github.com/aowvn-10diem/aowvn-monika/releases/download/v0.8.8/AowVN-Monika-v0.8.8.apk"}}""")
        val current = repo().current
        assertEquals(62, current.configVersion)
        assertEquals(55, current.app.latestVersionCode)
        assertEquals("0.8.10", current.app.latestVersionName)
        assertEquals(1, current.app.minVersionCode)
        assertEquals("https://github.com/aowvn-10diem/aowvn-monika/releases/download/v0.8.10/AowVN-Monika-v0.8.10.apk", current.app.apkUrl)
        assertTrue(current.app.changelog.contains("Bản thử"))
        assertEquals("Bản thử 0.8.10: Thư viện có trang chi tiết game, sao lưu trước khi xóa. Sửa phím ảo và phím Java; dễ xếp hai màn hình Nintendo DS hơn. Thêm nơi kiểm tra bản Monika chính thức.", current.app.changelog)
    }

    @Test fun downgradeFromRunningVersionIsRejectedAndOldBytesAreKept() = runBlocking {
        val r = repo(); val high = bundled + 10
        reply = valid(high); assertTrue(r.refresh(true).isSuccess)
        val before = cache.readText()
        reply = valid(high - 1); assertTrue(r.refresh(true).isFailure)
        assertEquals(high, r.current.configVersion); assertEquals(before, cache.readText())
        assertEquals("op:tai-cau-hinh", Diagnostics.list(app).first().component)
        assertEquals(high, repo().current.configVersion)
    }

    @Test fun invalidUrlsHashesAndRunnerNeverReplaceActiveConfig() = runBlocking {
        val r = repo(); val version = bundled + 10
        reply = valid(version); assertTrue(r.refresh(true).isSuccess)
        val before = cache.readText()
        val payloads = listOf(
            """"crash":{"endpoint":"http://example.test/report"}""",
            """"community":{"discord":"javascript:alert(1)"}""",
            """"adblock":{"lists":["http://example.test/list"]}""",
            """"webPlayers":{"x":{"script":"file:///private/script.js"}}""",
            """"modules":{"x":{"version":"1","url":"https://github.com/aowvn-10diem/aowvn-monika-packs/releases/download/x/{abi}.zip","sha256":"bad"}}""",
            """"modules":{"x":{"version":"1","sha256ByAbi":{"arm64-v8a":"${"g".repeat(64)}"}}}""",
            """"systems":[{"id":"x","name":"X","runner":"unknown"}]""",
            """"feed":{"url":42}"""
        )
        for (payload in payloads) {
            reply = """{"configVersion":${version + 1},$payload}"""
            assertTrue(payload, r.refresh(true).isFailure)
            assertEquals(before, cache.readText()); assertEquals(version, r.current.configVersion)
        }
        reply = "{"; assertTrue(r.refresh(true).isFailure)
        assertEquals(before, cache.readText())
    }

    @Test fun artifactUrlsMustStayInsideOwnerPathPrefix() {
        // V206: host đúng chưa đủ — URL mã/gói phải nằm trong tiền tố chủ sở hữu đã chốt.
        val hash = "c".repeat(64)
        fun config(url: String) = """{"configVersion":1,"modules":{"x":{"version":"1","url":"$url","sha256":"$hash"}}}"""
        val allowed = listOf(
            "https://github.com/aowvn-10diem/aowvn-monika-packs/releases/download/v1/x.zip",
            "https://raw.githubusercontent.com/aowvn-10diem/aowvn-monika/main/x.zip",
            "https://codeload.github.com/aowvn-10diem/aowvn-monika/zip/refs/heads/main",
            "https://cdn.jsdelivr.net/npm/@ruffle-rs/ruffle@0.1.0/dist/ruffle.js",
        )
        for (url in allowed) {
            assertEquals(url, url, ConfigValidation.parse(config(url), 0).modules.getValue("x").url)
        }
        val rejected = listOf(
            "https://github.com/evil/repo/releases/download/v1/core.zip",
            "https://github.com/aowvn-10diem-khac/repo/x.zip",
            "https://raw.githubusercontent.com/evil/repo/main/x.zip",
            "https://codeload.github.com/evil/repo/zip/refs/heads/main",
            "https://cdn.jsdelivr.net/gh/abpvn/abpvn@master/filter/x.txt",
            "https://objects.githubusercontent.com/x.zip",
            "https://release-assets.githubusercontent.com/x.zip",
        )
        for (url in rejected) {
            val error = runCatching { ConfigValidation.parse(config(url), 0) }.exceptionOrNull()
            assertTrue(url, error is IllegalArgumentException)
        }
    }

    @Test fun enginePatternBoundariesAcceptDefaultAndMaximumLiteralValues() {
        fun config(patterns: String?) = """{"engines":[{"system":"rgss","markers":[]${patterns?.let { ",\"errorPatterns\":$it" }.orEmpty()}}]}"""
        assertTrue(ConfigValidation.parse(config(null), 0).engines.single().errorPatterns.isEmpty())
        assertTrue(ConfigValidation.parse(config("[]"), 0).engines.single().errorPatterns.isEmpty())
        val maximum = List(32) { "x".repeat(160) }
        assertEquals(maximum, ConfigValidation.parse(config(JsonArray(maximum.map(::JsonPrimitive)).toString()), 0).engines.single().errorPatterns)
        // Dấu regex vẫn là literal; không bị diễn giải như biểu thức khi đọc config.
        assertEquals(listOf("[.*]"), ConfigValidation.parse(config("[\"[.*]\"]"), 0).engines.single().errorPatterns)
    }

    @Test fun invalidEnginePatternBoundsAndTypesKeepPreviousRemoteConfig() = runBlocking {
        val r = repo(); val version = bundled + 10
        reply = valid(version); assertTrue(r.refresh(true).isSuccess)
        val before = cache.readText()
        val invalid = listOf(
            JsonArray(List(33) { JsonPrimitive("valid") }).toString(),
            JsonArray(listOf(JsonPrimitive("x".repeat(161)))).toString(),
            "[\"\"]", "[\"   \"]", "[1]", "[null]", "null", "{}", "\"pattern\""
        )
        for (patterns in invalid) {
            reply = """{"configVersion":${version + 1},"engines":[{"system":"rgss","markers":[],"errorPatterns":$patterns}]}"""
            assertTrue(patterns, r.refresh(true).isFailure)
            assertEquals(version, r.current.configVersion)
            assertEquals(before, cache.readText())
        }
    }

    @Test fun refreshFailureLeavesOneOpFailButOfflineDoesNot() = runBlocking {
        Diagnostics.resetOpThrottle()
        val http500 = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(500).message("Err")
                .body(ResponseBody.create(null, "")).build()
        }.build()
        assertTrue(ConfigRepository(app, http500).refresh(true).isFailure)
        val fail = Diagnostics.list(app).filter { it.kind == "op-fail" }.single()
        assertEquals("op:tai-cau-hinh", fail.component)
        assertTrue(fail.detail.contains("bước: tai") && fail.detail.contains("HTTP 500"))
        File(app.filesDir, "diag").deleteRecursively()
        val offline = OkHttpClient.Builder().addInterceptor { throw java.net.UnknownHostException("aow.vn") }.build()
        assertTrue(ConfigRepository(app, offline).refresh(true).isFailure)
        assertTrue("mất mạng chỉ ghi vệt", Diagnostics.list(app).none { it.kind == "op-fail" })
    }

    @Test fun invalidCacheFallsBackToBundledAndRecordsHandledError() {
        cache.writeText("""{"configVersion":${bundled + 10},"feed":{"url":"http://bad.test"}}""")
        assertEquals(bundled, repo().current.configVersion)
        assertTrue(Diagnostics.list(app).any { it.component == "app:config" })
    }

    @Test fun cacheWriteFailureDoesNotChangeRunningConfigOrPreviousFile() = runBlocking {
        val r = repo(); reply = valid(bundled + 1); assertTrue(r.refresh(true).isSuccess)
        val before = cache.readText()
        File(cache.path + ".new").mkdir()
        reply = valid(bundled + 2); assertTrue(r.refresh(true).isFailure)
        assertEquals(before, cache.readText()); assertEquals(bundled + 1, r.current.configVersion)
    }

    @Test fun httpsTemplatesAndUppercaseHashesAreAcceptedAndOldOptionalFieldsStayOptional() {
        val c = ConfigValidation.parse("""{"configVersion":1,"modules":{"x":{"version":"1","url":"https://github.com/aowvn-10diem/aowvn-monika-packs/releases/download/x/{abi}.zip","sha256":"${"AB".repeat(32)}"}},"systems":[{"id":"x","name":"X","runner":"j2me"}]}""", 1)
        assertEquals(1, c.configVersion)
        assertEquals(0, ConfigValidation.parse("{}", 0).configVersion)
        ConfigValidation.parse("""{"downloadHosts":[{"name":"x","host":"pixeldrain.com","mode":"direct","pattern":"^https?://pixeldrain[.]com/(.+)","directUrl":"https://pixeldrain.com/$1"}]}""", 0)
        ConfigValidation.parse(app.assets.open(ConfigRepository.ASSET_NAME).bufferedReader().use { it.readText() }, 0)
    }

    // ---- V196: chỉ nhận URL tải từ host cho phép (chống config bị đầu độc) ----

    @Test fun downloadUrlOutsideAllowedHostsIsRejectedAndNeverRequested() = runBlocking {
        val requested = mutableListOf<String>()
        val r = ConfigRepository(app, OkHttpClient.Builder().addInterceptor { chain ->
            requested += chain.request().url.host
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(ResponseBody.create(null, reply)).build()
        }.build())
        val version = bundled + 10
        val payloads = listOf(
            """"cores":{"x":{"version":"1","url":"https://evil.example/core.zip"}}""",
            """"cores":{"x":{"version":"1","systemFiles":"https://evil.example/system.zip"}}""",
            """"cores":{"x":{"version":"1","artifacts":{"arm64-v8a":{"url":"https://evil.example/{abi}.zip","sha256":"${"a".repeat(64)}"}}}}""",
            """"modules":{"x":{"version":"1","url":"https://evil.example/{abi}.zip","sha256":"${"a".repeat(64)}"}}""",
            """"app":{"apkUrl":"https://evil.example/app.apk"}""",
            """"webPlayers":{"x":{"script":"https://evil.example/player.js"}}""",
        )
        for (payload in payloads) {
            reply = """{"configVersion":${version + 1},$payload}"""
            assertTrue(payload, r.refresh(true).isFailure)
            assertFalse("không ghi config lạ vào cache", cache.exists())
            assertEquals(bundled, r.current.configVersion)
        }
        assertTrue(requested.isNotEmpty())
        assertTrue("không có request nào tới host lạ: $requested", requested.none { it == "evil.example" })
        assertFalse(r.remoteApplied.value)
    }

    @Test fun httpDownloadUrlIsRejectedEvenOnAllowedHost() = runBlocking {
        val r = repo()
        val version = bundled + 11
        reply = """{"configVersion":$version,"cores":{"x":{"version":"1","url":"http://github.com/aowvn-10diem/x.zip"}}}"""
        assertTrue(r.refresh(true).isFailure)
        assertFalse(cache.exists())
        assertEquals(bundled, r.current.configVersion)
    }

    @Test fun allowedHostsAreAcceptedAndApplied() = runBlocking {
        val r = repo()
        val version = bundled + 12
        reply = """{"configVersion":$version,
            "cores":{"x":{"version":"1","url":"https://buildbot.libretro.com/nightly/android/latest/{abi}/x_libretro_android.so.zip"}},
            "modules":{"y":{"version":"1","url":"https://github.com/aowvn-10diem/aowvn-monika-packs/releases/download/e/y.zip","sha256":"${"b".repeat(64)}"},
                       "w":{"version":"1","url":"https://raw.githubusercontent.com/aowvn-10diem/aowvn-monika/main/w.zip","sha256":"${"c".repeat(64)}"}},
            "app":{"apkUrl":"https://github.com/aowvn-10diem/aowvn-monika/releases/download/v0/x.apk"}}"""
        assertTrue(r.refresh(true).isSuccess)
        assertEquals(version, r.current.configVersion)
        assertTrue(cache.exists())
        assertTrue(r.remoteApplied.value)
    }

    @Test fun cachedConfigWithForeignDownloadHostFallsBackToBundled() {
        cache.writeText("""{"configVersion":${bundled + 50},"cores":{"x":{"version":"1","url":"https://evil.example/core.zip"}}}""")
        val r = repo()
        assertEquals(bundled, r.current.configVersion)
        assertFalse(r.remoteApplied.value)
    }

    // ---- V196b: URL tệp trong downloadHosts[].directUrl ----

    @Test fun directUrlHttpOrForeignHostIsRejected() {
        val httpUrl = """{"downloadHosts":[{"name":"X","host":"pixeldrain.com","mode":"direct","pattern":"^https?://pixeldrain[.]com/(.+)","directUrl":"http://pixeldrain.com/api/file/$1"}]}"""
        val foreign = """{"downloadHosts":[{"name":"X","host":"pixeldrain.com","mode":"direct","pattern":"^https?://pixeldrain[.]com/(.+)","directUrl":"https://evil.example/api/file/$1"}]}"""
        for (json in listOf(httpUrl, foreign)) {
            val error = runCatching { ConfigValidation.parse(json, 0) }.exceptionOrNull()
            assertTrue("phải bị từ chối: $json -> $error", error is IllegalArgumentException)
        }
    }

    @Test fun directUrlAllowedHostIsAccepted() {
        val json = """{"downloadHosts":[{"name":"X","host":"pixeldrain.com","mode":"direct","pattern":"^https?://pixeldrain[.]com/(.+)","directUrl":"https://pixeldrain.com/api/file/$1"}]}"""
        assertEquals(
            "https://pixeldrain.com/api/file/$1",
            ConfigValidation.parse(json, 0).downloadHosts.single().directUrl,
        )
    }

    @Test fun cachedConfigWithForeignDirectUrlFallsBackToBundled() {
        cache.writeText(
            """{"configVersion":${bundled + 60},"downloadHosts":[{"name":"X","host":"pixeldrain.com","mode":"direct","pattern":"^https?://pixeldrain[.]com/(.+)","directUrl":"https://evil.example/api/file/$1"}]}"""
        )
        val r = repo()
        assertEquals(bundled, r.current.configVersion)
        assertFalse(r.remoteApplied.value)
    }

    // ---- V196: link tải/mời cài của app ngoài và plugin cũng phải qua allowlist ----

    @Test fun downloadUrlCuaAppNgoaiVaPluginPhaiThuocAllowlist() {
        val evilApp = """{"externalApps":[{"id":"x","name":"X","downloadUrl":"https://evil.example/tai.html"}]}"""
        val evilPlugin = """{"externalApps":[{"id":"x","name":"X","plugins":[{"name":"P","downloadUrl":"https://evil.example/p.html"}]}]}"""
        val httpApp = """{"externalApps":[{"id":"x","name":"X","downloadUrl":"http://www.aow.vn/p/tai.html"}]}"""
        for (json in listOf(evilApp, evilPlugin, httpApp)) {
            val error = runCatching { ConfigValidation.parse(json, 0) }.exceptionOrNull()
            assertTrue("phải bị từ chối: $json -> $error", error is IllegalArgumentException)
        }

        val emptyOk = """{"externalApps":[{"id":"x","name":"X","downloadUrl":"","plugins":[{"name":"P","downloadUrl":""}]}]}"""
        val accepted = ConfigValidation.parse(emptyOk, 0)
        assertEquals("", accepted.externalApps.single().downloadUrl)
        assertEquals("", accepted.externalApps.single().plugins.single().downloadUrl)

        val aowOk = """{"externalApps":[{"id":"x","name":"X","downloadUrl":"https://www.aow.vn/p/tai.html","plugins":[{"name":"P","downloadUrl":"https://aow.vn/p/p.html"}]}]}"""
        val ok = ConfigValidation.parse(aowOk, 0)
        assertEquals("https://www.aow.vn/p/tai.html", ok.externalApps.single().downloadUrl)
        assertEquals("https://aow.vn/p/p.html", ok.externalApps.single().plugins.single().downloadUrl)
    }

    @Test fun cacheCoDownloadUrlLaThiDungBanDongGoi() {
        cache.writeText(
            """{"configVersion":${bundled + 70},"externalApps":[{"id":"x","name":"X","downloadUrl":"https://evil.example/tai.html"}]}"""
        )
        val r = repo()
        assertEquals(bundled, r.current.configVersion)
        assertFalse(r.remoteApplied.value)
    }
}
