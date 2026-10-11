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
        assertTrue(lines.any { it.contains("mẫu mục: main.ks | bg.png") })
        assertFalse(lines.any { it.contains("scn/main.ks") || it.contains("image/bg.png") })
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
    @Test fun manualCopyUsesSameMetadataWithoutPathsOrArchiveContents() {
        val dir = tmp.newFolder("game-copy")
        Xp3Fixture.xp3(dir, listOf("scripts/main.ks"), compressed = true).renameTo(File(dir, "data.xp3"))
        Xp3Fixture.xp3(dir, listOf("startup.tjs"), compressed = false).renameTo(File(dir, "patch.xp3"))
        File(dir, "game.exe").writeBytes(ByteArray(3_072))
        val entry = File(dir, "data.xp3").path
        val copy = KirikiriDiag.reportText(entry)
        assertEquals(KirikiriDiag.describe(entry).joinToString("\n"), copy)
        assertTrue(copy.contains("xp3 data.xp3") && copy.contains("startup.tjs@gốc=false"))
        assertTrue(copy.contains("xp3 patch.xp3") && copy.contains("startup.tjs@gốc=true"))
        assertTrue(copy.contains("exe game.exe 3KB"))
        assertFalse(copy.contains(tmp.root.path) || copy.contains("scripts/main.ks"))
        assertTrue(copy.length <= 8 * 1024)
    }

    @Test fun sampleBudgetIsFiveAcrossArchivesWithOnlyBoundedBasenames() {
        val dir = tmp.newFolder("hashed")
        Xp3Fixture.xp3(dir, listOf("private/3f9a1c", "C:\\private\\77be02", "../c0ffee", "folder/" + "a".repeat(100)), compressed = true)
            .renameTo(File(dir, "main.xp3"))
        Xp3Fixture.xp3(dir, listOf("secret/a1b2c3", "secret/d4e5f6", "secret/0a0b0c"), compressed = false)
            .renameTo(File(dir, "patch.xp3"))
        val lines = KirikiriDiag.describe(File(dir, "main.xp3").path)
        val samples = lines.filter { it.startsWith("mẫu mục: ") }.flatMap { it.removePrefix("mẫu mục: ").split(" | ") }
        assertEquals(listOf("3f9a1c", "77be02", "c0ffee", "a".repeat(24), "a1b2c3"), samples)
        assertFalse(lines.any { it.contains("private") || it.contains("secret") || it.contains("/") || it.contains('\\') })
        assertTrue(lines.all { it.length <= 200 })
        val copy = KirikiriDiag.reportText(File(dir, "main.xp3").path)
        assertEquals(lines.joinToString("\n"), copy)
        assertFalse(copy.contains("d4e5f6") || copy.contains("0a0b0c") || copy.contains(tmp.root.path))
    }

    @Test fun unreadableArchiveHasNoInventedSamplesAndControlCharactersCannotAddLines() {
        val dir = tmp.newFolder("safe-samples")
        File(dir, "broken.xp3").writeBytes(ByteArray(500) { 2 })
        Xp3Fixture.xp3(dir, listOf("folder/hash\n\tvalue", "folder/"), compressed = false)
            .renameTo(File(dir, "main.xp3"))
        val lines = KirikiriDiag.describe(dir.path)
        assertTrue(lines.any { it.startsWith("xp3 broken.xp3") && it.contains("chỉ mục không đọc được") })
        assertEquals(listOf("mẫu mục: hashvalue"), lines.filter { it.startsWith("mẫu mục: ") })
        assertFalse(lines.any { it.contains('\n') || it.contains('\t') || it.contains("/") })
    }

}
