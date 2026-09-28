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
                "apk" -> Unit
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
