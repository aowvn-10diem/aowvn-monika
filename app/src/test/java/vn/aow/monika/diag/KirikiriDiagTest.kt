package vn.aow.monika.diag

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import vn.aow.monika.library.Xp3Fixture

/** V26: vệt lối vào Kirikiri có đủ dữ kiện chẩn đoán và không chứa nội dung game. */
class KirikiriDiagTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun karaNoShoujo_ghiXp3NaoCoStartupTjs() {
        val dir = tmp.newFolder("Kara no Shoujo - AowVN.org")
        Xp3Fixture.xp3(dir, listOf("scn/main.ks", "image/bg.png"), compressed = true).renameTo(File(dir, "karanoshojo.xp3"))
        Xp3Fixture.xp3(dir, listOf("startup.tjs"), compressed = false).renameTo(File(dir, "patch.xp3"))
        File(dir, "karanoshojo.exe").writeBytes(ByteArray(300) { 1 })
        File(dir, "Uninstall.exe").writeBytes(ByteArray(200) { 1 })
        val lines = KirikiriDiag.describe(File(dir, "karanoshojo.xp3").path)

        assertTrue(lines.first().startsWith("truyền karanoshojo.xp3 thư mục=Kara no Shoujo - AowVN.org "))
        assertFalse(lines.any { it.contains(tmp.root.path) || it.contains("/") }) // chỉ tên, không có đường dẫn đầy đủ
        assertTrue(lines.any { it.contains("2 xp3, 2 exe") && it.contains("startup.tjs rời=false") })
        assertTrue(lines.any { it.startsWith("xp3 karanoshojo.xp3") && it.contains("startup.tjs@gốc=false") })
        assertTrue(lines.any { it.startsWith("xp3 patch.xp3") && it.contains("startup.tjs@gốc=true") })
        assertTrue(lines.any { it.startsWith("exe karanoshojo.exe") })
        assertTrue(lines.all { it.length <= 200 })
        assertFalse(lines.any { it.contains("main.ks") || it.contains("bg.png") }) // không lộ tên mục trong kho
    }

    @Test fun xp3HongHoacKhongPhaiXp3_ghiLyDoKhongVang() {
        val dir = tmp.newFolder("g")
        File(dir, "data.xp3").writeBytes(ByteArray(500) { 2 })
        val lines = KirikiriDiag.describe(File(dir, "data.xp3").path)
        assertTrue(lines.any { it.startsWith("xp3 data.xp3") && it.contains("chỉ mục không đọc được") })
    }

    @Test fun thuMucCoStartupRoi_vaKhongCoDuongDan() {
        val dir = tmp.newFolder("loose")
        File(dir, "startup.tjs").writeText("//")
        val lines = KirikiriDiag.describe(dir.path)
        assertTrue(lines.any { it.contains("startup.tjs rời=true") })
        assertEquals(1, KirikiriDiag.describe(null).size)
    }
}
