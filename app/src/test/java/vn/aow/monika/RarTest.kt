package vn.aow.monika

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import vn.aow.monika.library.ArchiveExtractor
import vn.aow.monika.library.SevenZipNative
import java.io.File

/** RAR có mật khẩu (lỗi thật trên máy: "kiểu mã hóa chưa hỗ trợ") → 7-Zip native. */
class RarTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun res(name: String) = File(javaClass.getResource("/rar/$name")!!.toURI())

    private fun check(dir: File) {
        assertEquals("Xin chào AowVN", File(dir, "đọc tôi.txt").readText())
        assertEquals(220000L, File(dir, "Trò chơi/game.nds").length())
    }

    @Test fun rar5Password() {
        val out = tmp.newFolder()
        val seen = mutableListOf<Int>()
        SevenZipNative.extract(listOf(res("pass5.rar")), out, listOf("sai", "aowvn.org")) { seen += it }
        check(out)
        assertEquals(100, seen.last())
    }

    @Test fun encryptedHeaders() {
        val out = tmp.newFolder()
        SevenZipNative.extract(listOf(res("header.rar")), out, listOf("aowvn.org"))
        check(out)
    }

    @Test fun multiVolume() {
        val out = tmp.newFolder()
        SevenZipNative.extract(listOf("multi.part1.rar", "multi.part2.rar", "multi.part3.rar").map(::res), out, listOf("aowvn.org"))
        check(out)
    }

    @Test fun plain() {
        val out = tmp.newFolder()
        SevenZipNative.extract(listOf(res("plain.rar")), out, emptyList())
        check(out)
    }

    @Test fun wrongPassword() {
        val out = tmp.newFolder()
        try {
            SevenZipNative.extract(listOf(res("pass5.rar")), out, listOf("sai-1"))
            fail()
        } catch (e: ArchiveExtractor.PasswordException) {
            assertTrue(e.message, e.message!!.contains("mật khẩu"))
        }
        assertEquals(0, out.listFiles()!!.size)
    }

    @Test fun headerWithoutPassword() {
        try {
            SevenZipNative.extract(listOf(res("header.rar")), tmp.newFolder(), emptyList())
            fail()
        } catch (e: ArchiveExtractor.PasswordException) {
            assertTrue(e.message!!.contains("có mật khẩu"))
        }
    }
}
