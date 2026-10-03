package vn.aow.monika.library

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import vn.aow.monika.config.ConfigRepository

/** V26: chọn đúng lối vào Kirikiri. Cây thư mục dựng y hệt bản Android của Kara no Shoujo (tên thư mục có dấu cách, kho chính ≠ data.xp3, 5 bản vá). */
class KirikiriEntryTest {
    @get:Rule val tmp = TemporaryFolder()
    private val cfg = ConfigRepository.parse(File("../config/monika-config.json").readText())

    private fun file(dir: File, name: String, size: Int = 10, bytes: ByteArray? = null): File =
        File(dir, name).also { it.parentFile?.mkdirs(); it.writeBytes(bytes ?: ByteArray(size) { 1 }) }

    @Test fun karaNoShoujo_layKhoChinhKhongLayBanVa() {
        val dir = tmp.newFolder("Kara no Shoujo - AowVN.org")
        // Thứ tự tạo file cố ý để patch*.xp3 xuất hiện trước trong thư mục.
        file(dir, "patch.xp3", 50); (2..5).forEach { file(dir, "patch$it.xp3", 50) }
        file(dir, "karanoshojo.xp3", 5000)
        file(dir, "karanoshojo.exe", 300)
        file(dir, "plugin/layerEx.dll", 20); file(dir, "KnS.ico", 5)
        val g = GameDetector.detect(dir, cfg)
        assertEquals("kirikiri", g.system?.id)
        assertEquals("karanoshojo.xp3", g.entry?.name)
    }

    @Test fun coDataXp3ThiUuTienDataXp3() {
        val dir = tmp.newFolder("game")
        file(dir, "data.xp3", 100); file(dir, "patch.xp3", 900); file(dir, "x.xp3", 5000)
        assertEquals("data.xp3", GameDetector.detect(dir, cfg).entry?.name)
    }

    @Test fun chiCoPatchVaKhoLonHonThiLayKhoLon() {
        val dir = tmp.newFolder("g2")
        file(dir, "patch.xp3", 9000); file(dir, "main.xp3", 4000)
        assertEquals("main.xp3", GameDetector.detect(dir, cfg).entry?.name)
    }

    @Test fun chiCoExeNhungXp3GanTrongThiNhanExe() {
        val dir = tmp.newFolder("exe-only")
        val magic = byteArrayOf(0x58, 0x50, 0x33, 0x0d, 0x0a, 0x20, 0x0a, 0x1a, 0x8b.toByte(), 0x67, 0x01)
        file(dir, "game.exe", bytes = ByteArray(1000) { 7 } + magic + ByteArray(500))
        val g = GameDetector.detect(dir, cfg)
        assertEquals("kirikiri", g.system?.id)
        assertEquals("game.exe", g.entry?.name)
    }

    @Test fun exeThuongKhongPhaiKirikiri() {
        val dir = tmp.newFolder("plain-exe")
        file(dir, "setup.exe", bytes = ByteArray(2000) { 3 })
        assertNull(GameDetector.detect(dir, cfg).system)
    }
}
