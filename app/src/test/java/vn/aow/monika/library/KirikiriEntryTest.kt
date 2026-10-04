package vn.aow.monika.library

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
        file(dir, "karanoshojo.exe", 300); file(dir, "Uninstall.exe", 200)
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

    @Test fun exeCoXp3NoiSauPhanPE() {
        // PE tối thiểu: "MZ", e_lfanew=0x80, "PE\0\0", 1 section có rawPtr=0x200 rawSize=0x100 → overlay bắt đầu tại 0x300.
        val b = ByteArray(0x300 + 64)
        b[0] = 'M'.code.toByte(); b[1] = 'Z'.code.toByte(); b[0x3C] = 0x80.toByte()
        val pe = 0x80
        b[pe] = 'P'.code.toByte(); b[pe + 1] = 'E'.code.toByte()
        b[pe + 6] = 1                                   // NumberOfSections
        b[pe + 20] = 0xE0.toByte()                      // SizeOfOptionalHeader = 0xE0
        val sec = pe + 24 + 0xE0
        b[sec + 16] = 0x00; b[sec + 17] = 0x01           // SizeOfRawData = 0x100
        b[sec + 20] = 0x00; b[sec + 21] = 0x02           // PointerToRawData = 0x200
        val sig = byteArrayOf(0x58, 0x50, 0x33, 0x0d, 0x0a, 0x20, 0x0a, 0x1a, 0x8b.toByte(), 0x67, 0x01)
        System.arraycopy(sig, 0, b, 0x300, sig.size)
        val dir = tmp.newFolder("pe-overlay")
        file(dir, "game.exe", bytes = b)
        assertEquals("game.exe", GameDetector.detect(dir, cfg).entry?.name)
    }

    @Test fun thuMucRpgMakerKhongBiNhanLaKirikiri() {
        val dir = tmp.newFolder("rpg-maker")
        file(dir, "Game.exe", 1000)
        file(dir, "Game.ini", 100)
        file(dir, "Data/System.rvdata2", 100)

        assertNotEquals("kirikiri", GameDetector.detect(dir, cfg).system?.id)
    }

    @Test fun thuMucRenPyKhongBiNhanLaKirikiri() {
        val dir = tmp.newFolder("renpy-game")
        file(dir, "renpy/renpy.exe", 1000)
        file(dir, "game/script.rpyc", 100)
        file(dir, "Game.exe", 1000)

        assertNotEquals("kirikiri", GameDetector.detect(dir, cfg).system?.id)
    }

    @Test fun chiCoPatchXp3ThiKhongCoLoiVao() {
        val dir = tmp.newFolder("patch-only")
        file(dir, "patch.xp3", 1000)
        file(dir, "patch2.xp3", 1200)

        assertNull(GameDetector.detect(dir, cfg).entry)
    }

    @Test fun pairedExe_traVeExeDiKemKhoChinh() {
        val dir = tmp.newFolder("kara-exe")
        file(dir, "patch.xp3", 50); file(dir, "karanoshojo.xp3", 5000); file(dir, "karanoshojo.exe", 300)
        val sys = cfg.systems.first { it.id == "kirikiri" }.copy(entryPick = "pairedExe")
        val picked = EntryPick.pick(dir.listFiles().orEmpty().filter { EntryPick.acceptable(it, sys) }, sys)
        assertEquals("karanoshojo.exe", picked?.name)
    }
}
