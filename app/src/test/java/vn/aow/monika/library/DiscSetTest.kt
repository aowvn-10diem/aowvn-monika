package vn.aow.monika.library

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ca kiểm cho [DiscSet]: chỉ chặn khi **chắc chắn** tệp đĩa không có; nghi ngờ thì không chặn.
 * Nhóm BOM/UTF-16 khoá lỗi chặn oan `.m3u` lưu bằng Notepad Windows (đã bị bắt ở lượt duyệt hai).
 */
class DiscSetTest {
    private fun dir(): File = Files.createTempDirectory("disc-set").toFile()

    private fun write(folder: File, name: String, content: String = "x"): File =
        File(folder, name).apply { parentFile!!.mkdirs(); writeText(content) }

    private fun writeBytes(folder: File, name: String, bytes: ByteArray): File =
        File(folder, name).apply { parentFile!!.mkdirs(); writeBytes(bytes) }

    private fun utf8Bom(text: String): ByteArray =
        byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + text.toByteArray(Charsets.UTF_8)

    private fun utf16(text: String, littleEndian: Boolean): ByteArray {
        val bom = if (littleEndian) byteArrayOf(0xFF.toByte(), 0xFE.toByte()) else byteArrayOf(0xFE.toByte(), 0xFF.toByte())
        return bom + text.toByteArray(if (littleEndian) Charsets.UTF_16LE else Charsets.UTF_16BE)
    }

    private fun missing(message: String) = message.substringAfter("cần những tệp sau")

    @Test fun cueWithExistingTrackPasses() {
        val folder = dir()
        write(folder, "Game.bin")
        val cue = write(folder, "Game.cue", "FILE \"Game.bin\" BINARY\n  TRACK 01 MODE1/2352\n   INDEX 01 00:00:00\n")
        assertNull(DiscSet.inspect(cue))
    }

    @Test fun cueWithMissingTrackIsCertainAndNamesFileAndFolder() {
        val folder = dir()
        val cue = write(folder, "Game (Disc 1).cue", "FILE \"Game (Disc 1).bin\" BINARY\n")
        val result = DiscSet.inspect(cue) { "AowVN Monika/Game/Game" }!!
        assertTrue(result.certain)
        assertTrue(missing(result.message), missing(result.message).contains("Game (Disc 1).bin"))
        assertTrue(result.message, result.message.contains("AowVN Monika/Game/Game"))
        assertTrue(result.message, !result.message.contains(folder.path)) // không lộ đường dẫn tuyệt đối
    }

    @Test fun unquotedBackslashAndMultiTrackCueAreRead() {
        val folder = dir()
        write(folder, "track1.bin")
        write(folder, "sub/track 2.bin")
        val cue = write(folder, "Game.cue", "FILE track1.bin BINARY\nFILE \"sub\\track 2.bin\" BINARY\n")
        assertNull(DiscSet.inspect(cue)) // tên không ngoặc và dấu \ của bản Windows vẫn đọc đúng
    }

    @Test fun cueFileNamesAreCaseInsensitiveOnDisk() {
        val folder = dir()
        write(folder, "game.bin")
        val cue = write(folder, "Game.cue", "FILE \"GAME.BIN\" BINARY\n")
        assertNull(DiscSet.inspect(cue)) // thẻ nhớ FAT không phân biệt hoa/thường → không chặn oan
    }

    @Test fun m3uListsMissingCueAndIgnoresComments() {
        val folder = dir()
        write(folder, "Game (Disc 1).cue", "FILE \"Game (Disc 1).bin\" BINARY\n")
        write(folder, "Game (Disc 1).bin")
        val m3u = write(folder, "Game.m3u", "# đĩa 1\nGame (Disc 1).cue\nGame (Disc 2).cue\n")
        val result = DiscSet.inspect(m3u)!!
        assertTrue(result.certain)
        assertTrue(missing(result.message), missing(result.message).contains("Game (Disc 2).cue"))
        assertTrue(result.message, !missing(result.message).contains("Game (Disc 1).cue"))
        assertTrue(result.message, !result.message.contains("#"))
    }

    @Test fun m3uSavedAsUtf8WithBomIsNotBlocked() {
        val folder = dir()
        write(folder, "Game (Disc 1).cue")
        write(folder, "Game (Disc 2).cue")
        // Notepad Windows ghi BOM ở đầu tệp; một số công cụ còn để \uFEFF ở đầu dòng sau.
        val m3u = writeBytes(folder, "Game.m3u", utf8Bom("Game (Disc 1).cue\n\uFEFFGame (Disc 2).cue\n"))
        assertNull(DiscSet.inspect(m3u))
    }

    @Test fun m3uSavedAsUtf8WithoutBomStillWorks() {
        val folder = dir()
        write(folder, "Game (Disc 1).cue")
        val m3u = write(folder, "Game.m3u", "Game (Disc 1).cue\n")
        assertNull(DiscSet.inspect(m3u))
    }

    @Test fun m3uSavedAsUtf16leWithBomIsDecoded() {
        val folder = dir()
        write(folder, "Game (Disc 1).cue")
        val m3u = writeBytes(folder, "Game.m3u", utf16("Game (Disc 1).cue\n", littleEndian = true))
        assertNull(DiscSet.inspect(m3u))
    }

    @Test fun m3uSavedAsUtf16beWithBomIsDecoded() {
        val folder = dir()
        write(folder, "Game (Disc 1).cue")
        val m3u = writeBytes(folder, "Game.m3u", utf16("Game (Disc 1).cue\n", littleEndian = false))
        assertNull(DiscSet.inspect(m3u))
    }

    @Test fun m3uInUtf16WithoutBomIsNotBlocked() {
        val folder = dir()
        // Không có BOM: đọc theo UTF-8 ra ký tự NUL → tên không phân giải sạch ⇒ không chặn (để lõi tự báo lỗi).
        val bare = "Game (Disc 1).cue\n".toByteArray(Charsets.UTF_16LE)
        val m3u = writeBytes(folder, "Game.m3u", bare)
        assertNull(DiscSet.inspect(m3u))
    }

    @Test fun mixedUnreadableNameDowngradesToSuspicionOnly() {
        val folder = dir()
        // Dòng đầu sạch và thiếu thật, dòng sau có ký tự điều khiển → không đủ chắc để chặn.
        val m3u = writeBytes(folder, "Game.m3u", utf8Bom("Game (Disc 9).cue\nGa\u0000me.cue\n"))
        val result = DiscSet.inspect(m3u)!!
        assertFalse(result.certain)
        assertTrue(missing(result.message), missing(result.message).contains("Game (Disc 9).cue"))
    }

    @Test fun parentDirectoryReferenceIsCheckedLikeTheCore() {
        val folder = dir()
        write(folder, "shared/disc.bin")
        val present = write(File(folder, "game"), "Game.m3u", "../shared/disc.bin\n")
        assertNull(DiscSet.inspect(present))
        val absent = write(File(folder, "game"), "Lost.m3u", "../shared/missing.bin\n")
        val result = DiscSet.inspect(absent)!!
        assertTrue(result.certain)
        assertTrue(missing(result.message), missing(result.message).contains("../shared/missing.bin"))
    }

    @Test fun cueWithoutFileLineIsNotGuessed() {
        val cue = write(dir(), "Game.cue", "REM không có dòng FILE\n")
        assertNull(DiscSet.inspect(cue))
    }

    @Test fun otherExtensionsAreNotChecked() {
        val folder = dir()
        assertNull(DiscSet.inspect(write(folder, "Game.nds")))
        assertNull(DiscSet.inspect(write(folder, "Game.chd")))
        assertNull(DiscSet.inspect(write(folder, "Game.pbp")))
    }

    @Test fun absoluteUndecodableAndQuotedNamesDoNotBlock() {
        val folder = dir()
        // Đường dẫn tuyệt đối (Windows copy), tên hỏng khi giải mã, và tên có ngoặc kép: đều không đủ chắc.
        assertNull(DiscSet.inspect(write(folder, "Abs.cue", "FILE \"D:\\Games\\Game.bin\" BINARY\n")))
        val shiftJis = write(folder, "SJIS.cue", "")
        shiftJis.writeBytes(
            byteArrayOf(0x46, 0x49, 0x4C, 0x45, 0x20, 0x22) + // FILE "
                byteArrayOf(0x83.toByte(), 0x5A, 0x83.toByte(), 0x93.toByte()) + // tên Shift-JIS (UTF-8 không hợp lệ)
                byteArrayOf(0x2E, 0x62, 0x69, 0x6E, 0x22) // .bin"
        )
        assertNull(DiscSet.inspect(shiftJis))
        assertNull(DiscSet.inspect(write(folder, "Quote.m3u", "\"Game.cue\"\n")))
    }

    @Test fun messageIsVietnameseAndCouldNotLeakAbsolutePath() {
        val folder = dir()
        val cue = write(folder, "Game.cue", "FILE \"Game.bin\" BINARY\n")
        val result = DiscSet.inspect(cue) { "AowVN Monika/Game/Game" }!!
        assertEquals("Thiếu tệp đĩa của game.", result.message.lineSequence().first())
        assertTrue(result.message, result.message.contains("Quay lại"))
        assertTrue(result.message, !result.message.contains(folder.path))
    }
}
