package vn.aow.monika

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import vn.aow.monika.config.SystemDef
import org.junit.Assert.assertFalse
import vn.aow.monika.config.ConfigRepository
import vn.aow.monika.download.LinkResolver
import vn.aow.monika.library.GameDetector
import java.io.File
import java.nio.file.Files

/**
 * Chạy tự động trên CI mỗi lần push. Sửa config/monika-config.json sai (thiếu lõi, gõ nhầm id...)
 * thì test đỏ và báo đúng chỗ sai, trước khi user nhận được cấu hình hỏng.
 */
class ConfigTest {
    private val cfg = ConfigRepository.parse(File("../config/monika-config.json").readText())

    private fun configLiterals(element: JsonElement, path: String = ""): List<Pair<String, JsonElement>> = when (element) {
        is JsonObject -> element.entries.flatMap { (key, value) ->
            val childPath = if (path.isEmpty()) key else "$path.$key"
            val isUrlKey = key.lowercase().let {
                it == "url" || it.endsWith("url") || it.endsWith("uri") || it.endsWith("endpoint") || it.endsWith("link")
            }
            val urlValue = if (value is JsonPrimitive && value.isString && value.content.isNotBlank() &&
                (isUrlKey || "://" in value.content)
            ) listOf(childPath to value) else emptyList()
            urlValue + configLiterals(value, childPath)
        }
        is JsonArray -> element.mapIndexed { index, value -> configLiterals(value, "$path[$index]") }.flatten()
        else -> emptyList()
    }

    private fun sha256Values(element: JsonElement, path: String = ""): List<Pair<String, JsonElement>> = when (element) {
        is JsonObject -> element.entries.flatMap { (key, value) ->
            val childPath = if (path.isEmpty()) key else "$path.$key"
            if (key.startsWith("sha256", ignoreCase = true)) leafValues(value, childPath)
            else sha256Values(value, childPath)
        }
        is JsonArray -> element.mapIndexed { index, value -> sha256Values(value, "$path[$index]") }.flatten()
        else -> emptyList()
    }

    private fun leafValues(element: JsonElement, path: String): List<Pair<String, JsonElement>> = when (element) {
        is JsonObject -> element.entries.flatMap { (key, value) -> leafValues(value, "$path.$key") }
        is JsonArray -> element.mapIndexed { index, value -> leafValues(value, "$path[$index]") }.flatten()
        else -> listOf(path to element)
    }

    @Test
    fun `url va sha256 trong config dung dinh dang`() {
        val root = Json.parseToJsonElement(File("../config/monika-config.json").readText())
        val urls = configLiterals(root)
        val hashes = sha256Values(root)
        assertTrue("Không tìm thấy URL trong config", urls.isNotEmpty())
        assertTrue("Không tìm thấy sha256 trong config", hashes.isNotEmpty())
        urls.forEach { (path, value) ->
            val url = (value as JsonPrimitive).content
            assertTrue("$path phải bắt đầu bằng https://: $url", url.startsWith("https://"))
        }
        hashes.forEach { (path, value) ->
            assertTrue(
                "$path phải là SHA-256 hex 64 ký tự",
                value is JsonPrimitive && value.isString && Regex("^[0-9a-fA-F]{64}$").matches(value.content),
            )
        }
    }

    @Test
    fun `abis cua core la tap con cua abi app build`() {
        val gradle = File("build.gradle.kts").readText()
        val include = Regex("include\\(([^)]*)\\)").find(gradle)?.groupValues?.get(1)
            ?: error("Không tìm thấy include ABI trong app/build.gradle.kts")
        val appAbis = Regex("\"([^\"]+)\"").findAll(include).map { it.groupValues[1] }.toSet()
        val invalid = cfg.cores.flatMap { (id, core) -> core.abis.filterNot { it in appAbis }.map { "cores.$id.abis chứa $it" } }
        assertTrue(invalid.joinToString("\n"), invalid.isEmpty())
    }

    @Test
    fun `runner config nam trong nhanh cua GameLauncher`() {
        val launcher = File("src/main/java/vn/aow/monika/runner/GameLauncher.kt").readText()
        val known = Regex("(?m)^\\s*\"([^\"]+)\"\\s*->").findAll(launcher).map { it.groupValues[1] }.toSet()
        assertTrue("Không tìm thấy runner nào trong GameLauncher", known.isNotEmpty())
        val invalid = cfg.systems.filterNot { it.runner in known }.map { "${it.id}: ${it.runner}" }
        assertTrue(invalid.joinToString("\n"), invalid.isEmpty())
    }

    @Test
    fun `moi he may tro toi thanh phan co that`() {
        val errors = mutableListOf<String>()
        val ids = cfg.systems.map { it.id }
        ids.groupBy { it }.filter { it.value.size > 1 }.keys.forEach { errors += "Trùng id hệ máy: $it" }
        cfg.systems.forEach { s ->
            when (s.runner) {
                "libretro" -> if (s.core == null || s.core !in cfg.cores) errors += "${s.id}: lõi '${s.core}' không có trong 'cores'"
                "web" -> if (s.webPlayer != "html5" && s.webPlayer !in cfg.webPlayers) errors += "${s.id}: webPlayer '${s.webPlayer}' không có"
                "external" -> if (cfg.externalApp(s.externalApp ?: "") == null) errors += "${s.id}: externalApp '${s.externalApp}' không có"
                "apk", "j2me" -> Unit
                else -> errors += "${s.id}: runner '${s.runner}' không hợp lệ"
            }
        }
        cfg.engines.forEach { if (it.system !in ids) errors += "engines: hệ '${it.system}' không có trong 'systems'" }
        cfg.cores.forEach { (id, c) -> if (!c.url.startsWith("https://")) errors += "cores.$id: url phải là https" }
        cfg.downloadHosts.filter { it.mode == "direct" }.forEach {
            if (it.pattern == null || it.directUrl == null) errors += "downloadHosts ${it.host}: mode direct cần pattern + directUrl"
        }
        assertTrue(errors.joinToString("\n"), errors.isEmpty())
    }

    @Test
    fun `goi kirikiri nam dung trong modules co url sha va chi arm64`() {
        val m = cfg.modules["kirikiri"] ?: error("modules.kirikiri thiếu (có thể bị chèn nhầm sang mục khác)")
        assertTrue(m.url.startsWith("https://") && m.url.endsWith("kirikiri-arm64.zip"))
        assertEquals(64, m.sha256.length)
        assertTrue(m.size > 10_000_000)
        assertEquals(listOf("arm64-v8a"), m.abis)
        // Hệ Kirikiri trỏ đúng engine để tải trước + chạy nhúng.
        assertEquals("kirikiri", cfg.system("kirikiri")?.engine)
    }

    @Test
    fun `rgss ghim goi cong khai va van giu Joiplay du phong`() {
        assertEquals(38, cfg.configVersion)
        val module = cfg.modules["rgss"] ?: error("modules.rgss thiếu")
        assertEquals("engines-rgss-6", module.version)
        assertEquals("https://github.com/aowvn-10diem/aowvn-monika/releases/download/engines-rgss-6/rgss-arm64-v8a.zip", module.url)
        assertEquals("3135ef8ae95281051178cb343f44bac79a0462323083bd17798faed65f00864b", module.sha256)
        assertEquals(7_922_296L, module.size)
        assertEquals(listOf("arm64-v8a"), module.abis)
        val system = cfg.system("rgss") ?: error("systems.rgss thiếu")
        assertEquals("rgss", system.engine)
        assertEquals("rpg", system.pad)
        assertTrue(system.allowExternalApp)
        assertEquals("joiplay", system.externalApp)
    }

    @Test
    fun `GBA tat color correction o mac dinh goc va moi style`() {
        val core = cfg.cores.getValue("mgba")
        val display = requireNotNull(core.display)
        assertEquals(38, cfg.configVersion)
        assertEquals("OFF", core.options["mgba_color_correction"])
        assertEquals("lcd", display.default)
        assertTrue(display.styles.isNotEmpty())
        display.styles.forEach { (id, style) ->
            assertEquals(id, "OFF", style.options["mgba_color_correction"])
        }
    }

    @Test
    fun `co thu nghiem config-first va config cu giu mac dinh false`() {
        assertTrue(cfg.systems.single { it.id == "kirikiri" }.experimental)
        assertTrue(cfg.systems.filter { it.id != "kirikiri" }.none { it.experimental })
        val old = Json.decodeFromString(SystemDef.serializer(), """{"id":"old","name":"Hệ cũ","runner":"external"}""")
        assertFalse(old.experimental)
    }

    @Test
    fun `allowExternalApp mac dinh true va doc duoc false`() {
        assertTrue(cfg.systems.all { it.allowExternalApp }) // config hiện tại chưa tắt hệ nào
        val c = ConfigRepository.parse("""{"configVersion":1,"systems":[{"id":"x","name":"X","runner":"external","allowExternalApp":false},{"id":"y","name":"Y","runner":"external"}]}""")
        assertEquals(false, c.systems.first { it.id == "x" }.allowExternalApp)
        assertEquals(true, c.systems.first { it.id == "y" }.allowExternalApp)
    }

    @Test
    fun `tach link tai tu bai viet`() {
        val html = """<a href="https://pixeldrain.com/u/abc123">x</a> <a href="https://www.mediafire.com/file/q">y</a>
            <a href="https://www.aowvn.org/p/tai-trinh-gia-lap-gba.html">z</a>"""
        val links = LinkResolver.extract(html, cfg.downloadHosts)
        assertEquals(2, links.size)
        assertEquals("https://pixeldrain.com/api/file/abc123?download", links[0].directUrl)
        assertNull(links[1].directUrl)
    }

    @Test
    fun `lay link pixeldrain dang thu muc va chu tren nut`() {
        val html = """<li><a class="download" href="https://pixeldrain.com/d/yd6J5KeC" rel="nofollow">TẢI VỀ</a></li>"""
        val link = LinkResolver.extract(html, cfg.downloadHosts).single()
        assertEquals("https://pixeldrain.com/api/filesystem/yd6J5KeC?attach", link.directUrl)
        assertEquals("TẢI VỀ", link.label)
        assertTrue(LinkResolver.isBlogPage("https://www.aow.vn/p/tai-gia-lap-joiplay.html"))
        assertTrue(!LinkResolver.isBlogPage("https://www.aow.vn/2026/02/game.html"))
        assertTrue("aowvn.org" in cfg.archivePasswords)
    }

    @Test
    fun `nhan dien file nen chia nhieu phan`() {
        val mp = vn.aow.monika.library.MultiPart
        assertEquals(vn.aow.monika.library.MultiPart.Piece("game.rar", 1), mp.parse("Game.part1.rar"))
        assertEquals(vn.aow.monika.library.MultiPart.Piece("game.rar", 12), mp.parse("Game.part012.rar"))
        assertEquals(vn.aow.monika.library.MultiPart.Piece("pokemon.7z", 2), mp.parse("Pokemon.7z.002"))
        assertEquals(vn.aow.monika.library.MultiPart.Piece("old.rar", 1), mp.parse("old.r00"))
        assertNull(mp.parse("Game.rar"))
        assertNull(mp.parse("pokemon.gba"))
        val dir = Files.createTempDirectory("parts").toFile()
        listOf("G.part2.rar", "G.part1.rar", "G.part3.rar").forEach { File(dir, it).writeText("") }
        val parts = mp.siblings(dir, mp.parse("G.part1.rar")!!)
        assertEquals(listOf("G.part1.rar", "G.part2.rar", "G.part3.rar"), parts.map { it.name })
        assertTrue(mp.looksContiguous(parts))
        assertTrue(!mp.looksContiguous(parts.filter { it.name != "G.part2.rar" }))
    }

    @Test
    fun `loi thay the phai co trong cores`() {
        cfg.systems.forEach { s -> s.altCores.forEach { assertTrue("${s.id}: lõi '$it' không có trong cores", it in cfg.cores) } }
    }

    @Test
    fun `rut gon ten game tu tieu de bai`() {
        fun clean(t: String) = vn.aow.monika.library.GameMeta.cleanTitle(t)
        assertEquals("Pokemon Fire Red: Rocket Edition", clean("[MỚI] Game Pokemon Fire Red: Rocket Edition Việt Hóa | GBA Android PC IOS - Giả lập Rocket"))
        assertEquals("Dragon Ball: Advanced Adventure", clean("[GBA] Game Dragon Ball: Advanced Adventure Việt Hoá | Android"))
        assertEquals("Pokemon Black 2 Kaizo", clean("[NDS] Fan game Pokemon Black 2 Kaizo Việt Hóa"))
    }

    @Test
    fun `nhan dien game theo file danh dau va duoi file`() {
        fun gameWith(vararg files: String): File {
            val dir = Files.createTempDirectory("game").toFile()
            files.forEach { File(dir, it).apply { parentFile!!.mkdirs(); writeText("") } }
            return dir
        }
        assertEquals("kirikiri", GameDetector.detect(gameWith("Fate/data.xp3"), cfg).system?.id)
        assertEquals("rpgmv", GameDetector.detect(gameWith("Game/www/js/rpg_core.js", "Game/www/index.html"), cfg).system?.id)
        assertEquals("gba", GameDetector.detect(gameWith("pokemon.gba"), cfg).system?.id)
        assertEquals("psp", GameDetector.detect(gameWith("game.iso"), cfg).system?.id)
        val rar = GameDetector.detect(gameWith("game.rar"), cfg)
        assertNull(rar.system)
        assertTrue(rar.needsExtract)
        assertNotNull(GameDetector.detect(gameWith("a.jar"), cfg).entry)
    }
}

class ThreeDsConfigTest {
    private val cfg = vn.aow.monika.config.ConfigRepository.parse(java.io.File("../config/monika-config.json").readText())
    @org.junit.Test fun threeDsIsWiredAndArm64Only() {
        val s = cfg.system("3ds")!!
        org.junit.Assert.assertEquals("citra", s.core)
        org.junit.Assert.assertEquals(listOf("arm64-v8a"), cfg.cores["citra"]!!.abis)
        org.junit.Assert.assertEquals(vn.aow.monika.runner.PadLayout.N3DS, vn.aow.monika.runner.padFor("citra", s.pad))
        org.junit.Assert.assertTrue("3ds" in s.extensions)
    }
}
