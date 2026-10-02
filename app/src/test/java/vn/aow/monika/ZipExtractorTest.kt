package vn.aow.monika

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import vn.aow.monika.library.ArchiveExtractor
import vn.aow.monika.library.ZipExtractor
import java.io.File

class ZipExtractorTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun makeZip(method: EncryptionMethod?, password: String?): File {
        val src = tmp.newFolder().also { File(it, "sub").mkdirs() }
        File(src, "game.nds").writeText("rom-bytes")
        File(src, "sub/Tiếng Việt.txt").writeText("xin chào")
        val zip = File(tmp.newFolder(), "g.zip")
        val zf = if (password != null) ZipFile(zip, password.toCharArray()) else ZipFile(zip)
        val p = ZipParameters().apply {
            compressionMethod = CompressionMethod.DEFLATE
            if (method != null) { isEncryptFiles = true; encryptionMethod = method; if (method == EncryptionMethod.AES) aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256 }
        }
        zf.addFolder(File(src, "sub"), p)
        zf.addFile(File(src, "game.nds"), p)
        return zip
    }

    private fun check(out: File) {
        assertEquals("rom-bytes", File(out, "game.nds").readText())
        assertEquals("xin chào", File(out, "sub/Tiếng Việt.txt").readText())
    }

    @Test fun plain() { val out = tmp.newFolder(); val seen = mutableListOf<Int>(); ZipExtractor.extract(makeZip(null, null), out, emptyList()) { seen += it }; check(out); assertEquals(100, seen.last()) }
    @Test fun zipCryptoPasswordFromList() { val out = tmp.newFolder(); ZipExtractor.extract(makeZip(EncryptionMethod.ZIP_STANDARD, "aowvn.org"), out, listOf("sai", "aowvn.org")); check(out) }
    @Test fun aesPasswordFromList() { val out = tmp.newFolder(); ZipExtractor.extract(makeZip(EncryptionMethod.AES, "1"), out, listOf("x", "1")); check(out) }

    @Test fun needsPassword() {
        val out = tmp.newFolder()
        try { ZipExtractor.extract(makeZip(EncryptionMethod.AES, "1"), out, emptyList()); fail() }
        catch (e: ArchiveExtractor.PasswordException) { assertTrue(e.message, e.message!!.contains("có mật khẩu")) }
    }

    @Test fun wrongPasswordLeavesNothing() {
        val out = tmp.newFolder()
        try { ZipExtractor.extract(makeZip(EncryptionMethod.ZIP_STANDARD, "đúng"), out, listOf("sai-1")); fail() }
        catch (e: ArchiveExtractor.PasswordException) { assertTrue(e.message, e.message!!.contains("mật khẩu")) }
        assertEquals(0, out.listFiles()!!.size)
    }

    @Test fun viaArchiveExtractor() { val out = tmp.newFolder(); ArchiveExtractor.extract(makeZip(null, null), out, emptyList()); check(out) }
}
