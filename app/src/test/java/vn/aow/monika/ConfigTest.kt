package vn.aow.monika

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
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
