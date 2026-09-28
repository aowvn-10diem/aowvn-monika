package vn.aow.monika

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import vn.aow.monika.library.ArchiveExtractor
import vn.aow.monika.library.SevenZipExtractor
import java.io.File

/** 7z có mật khẩu (libarchive không giải được) → SevenZipExtractor. */
class SevenZipTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun res(name: String) = File(javaClass.getResource("/7z/$name")!!.toURI())

    private fun check(dir: File) {
        assertEquals("Xin chào AowVN", File(dir, "đọc tôi.txt").readText())
        assertEquals(220000L, File(dir, "Trò chơi/game.nds").length())
    }

    @Test fun passwordFromList() {
        val out = tmp.newFolder()
        val seen = mutableListOf<Int>()
        SevenZipExtractor.extract(listOf(res("pass.7z")), out, listOf("sai", "aowvn.org")) { seen += it }
        check(out)
        assertEquals(100, seen.last())
        assertTrue("% phải tăng dần", seen.zipWithNext().all { (a, b) -> b > a })
    }

    @Test fun encryptedHeader() {
        val out = tmp.newFolder()
        SevenZipExtractor.extract(listOf(res("pass-header.7z")), out, listOf("aowvn.org"))
        check(out)
    }

    @Test fun plainWithoutPassword() {
        val out = tmp.newFolder()
        SevenZipExtractor.extract(listOf(res("plain.7z")), out, emptyList())
        check(out)
    }

    @Test fun splitVolumes() {
        val out = tmp.newFolder()
        SevenZipExtractor.extract(listOf(res("split.7z.001"), res("split.7z.002")), out, listOf("aowvn.org"))
        check(out)
    }

    @Test fun wrongPasswordLeavesNothing() {
        val out = tmp.newFolder()
        try {
            SevenZipExtractor.extract(listOf(res("pass.7z")), out, listOf("sai-1", "sai-2"))
            fail("phải báo sai mật khẩu")
        } catch (e: ArchiveExtractor.PasswordException) {
            assertTrue(e.message!!.contains("mật khẩu"))
        }
        assertEquals(0, out.listFiles()!!.size)
    }

    @Test fun missingPassword() {
        try {
            SevenZipExtractor.extract(listOf(res("pass.7z")), tmp.newFolder(), emptyList())
            fail()
        } catch (e: ArchiveExtractor.PasswordException) {
            assertTrue(e.message!!.contains("có mật khẩu"))
        }
    }

    @Test fun detect7z() {
        assertTrue(ArchiveExtractor.is7z(File("Game.7z")))
        assertTrue(ArchiveExtractor.is7z(File("Game.7Z.001")))
        assertTrue(!ArchiveExtractor.is7z(File("Game.part1.rar")))
    }
}
