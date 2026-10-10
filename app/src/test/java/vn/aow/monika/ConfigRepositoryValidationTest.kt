package vn.aow.monika

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.*
import org.junit.Assert.*
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
    private fun repo() = ConfigRepository(app, OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(ResponseBody.create(null, reply)).build()
    }.build())
    private fun valid(version: Int) = """{"configVersion":$version,"feed":{"url":"https://example.test/feed"}}"""

    @Before fun clean() {
        listOf(cache, File(cache.path + ".new"), File(cache.path + ".bak"), File(app.filesDir, "config.checked"), File(app.filesDir, "diag"))
            .forEach { it.deleteRecursively() }
    }

    @Test fun oldCacheWithoutExperimentalFlagFallsBackToBundledWarning() {
        assertEquals(39, bundled)
        cache.writeText(valid(38))
        val current = repo().current
        assertEquals(39, current.configVersion)
        assertTrue(current.systems.single { it.id == "kirikiri" }.experimental)
    }

    @Test fun downgradeFromRunningVersionIsRejectedAndOldBytesAreKept() = runBlocking {
        val r = repo(); val high = bundled + 10
        reply = valid(high); assertTrue(r.refresh(true).isSuccess)
        val before = cache.readText()
        reply = valid(high - 1); assertTrue(r.refresh(true).isFailure)
        assertEquals(high, r.current.configVersion); assertEquals(before, cache.readText())
        assertEquals("app:config", Diagnostics.list(app).first().component)
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
            """"modules":{"x":{"version":"1","url":"https://example.test/{abi}.zip","sha256":"bad"}}""",
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
        val c = ConfigValidation.parse("""{"configVersion":1,"modules":{"x":{"version":"1","url":"https://example.test/{abi}.zip","sha256":"${"AB".repeat(32)}"}},"systems":[{"id":"x","name":"X","runner":"j2me"}]}""", 1)
        assertEquals(1, c.configVersion)
        assertEquals(0, ConfigValidation.parse("{}", 0).configVersion)
        ConfigValidation.parse("""{"downloadHosts":[{"name":"x","host":"x.test","mode":"direct","pattern":"^https?://x[.]test/(.+)","directUrl":"https://x.test/$1"}]}""", 0)
        ConfigValidation.parse(app.assets.open(ConfigRepository.ASSET_NAME).bufferedReader().use { it.readText() }, 0)
    }
}
