package vn.aow.monika.library

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ca kiểm cho [DiscSet]: chỉ chặn khi tệp danh sách trỏ tới tệp đĩa chắc chắn không có. */
class DiscSetTest {
    private fun dir(): File = Files.createTempDirectory("disc-set").toFile()

    private fun write(folder: File, name: String, content: String = "x"): File =
        File(folder, name).apply { parentFile!!.mkdirs(); writeText(content) }

    @Test fun cueWithExistingTrackPasses() {
        val folder = dir()
        write(folder, "Game.bin")
        val cue = write(folder, "Game.cue", "FILE \"Game.bin\" BINARY\n  TRACK 01 MODE1/2352\n   INDEX 01 00:00:00\n")
        assertNull(DiscSet.problem(cue))
    }

    @Test fun cueWithMissingTrackNamesFileAndFolder() {
        val folder = dir()
        val cue = write(folder, "Game (Disc 1).cue", "FILE \"Game (Disc 1).bin\" BINARY\n")
        val message = DiscSet.problem(cue, "AowVN Monika/Game/Game")!!
        assertTrue(message, message.contains("Game (Disc 1).bin"))
        assertTrue(message, message.contains("AowVN Monika/Game/Game"))
        assertTrue(message, !message.contains(folder.path)) // không lộ đường dẫn tuyệt đối
    }

    @Test fun unquotedBackslashAndMultiTrackCueAreRead() {
        val folder = dir()
        write(folder, "track1.bin")
        write(folder, "sub/track 2.bin")
        val cue = write(folder, "Game.cue", "FILE track1.bin BINARY\nFILE \"sub\\track 2.bin\" BINARY\n")
        assertNull(DiscSet.problem(cue)) // tên không ngoặc và dấu \ của bản Windows vẫn đọc đúng
    }

    @Test fun cueFileNamesAreCaseInsensitiveOnDisk() {
        val folder = dir()
        write(folder, "game.bin")
        val cue = write(folder, "Game.cue", "FILE \"GAME.BIN\" BINARY\n")
        assertNull(DiscSet.problem(cue)) // thẻ nhớ FAT không phân biệt hoa/thường → không chặn oan
    }

    @Test fun m3uListsMissingCueAndIgnoresComments() {
        val folder = dir()
        write(folder, "Game (Disc 1).cue", "FILE \"Game (Disc 1).bin\" BINARY\n")
        write(folder, "Game (Disc 1).bin")
        val m3u = write(folder, "Game.m3u", "# đĩa 1\nGame (Disc 1).cue\nGame (Disc 2).cue\n")
        val message = DiscSet.problem(m3u)!!
        assertTrue(message, message.contains("Game (Disc 2).cue"))
        assertTrue(message, !message.contains("Game (Disc 1).cue"))
        assertTrue(message, !message.contains("#"))
    }

    @Test fun cueWithoutFileLineIsNotGuessed() {
        val cue = write(dir(), "Game.cue", "REM không có dòng FILE\n")
        assertNull(DiscSet.problem(cue))
    }

    @Test fun otherExtensionsAreNotChecked() {
        val folder = dir()
        assertNull(DiscSet.problem(write(folder, "Game.nds")))
        assertNull(DiscSet.problem(write(folder, "Game.chd")))
        assertNull(DiscSet.problem(write(folder, "Game.pbp")))
    }

    @Test fun absoluteAndUndecodableNamesDoNotBlock() {
        val folder = dir()
        // Windows copy trỏ ổ đĩa tuyệt đối, và tên bị hỏng khi giải mã (cue Shift-JIS) → không kết luận.
        val absolute = write(folder, "Abs.cue", "FILE \"D:\\Games\\Game.bin\" BINARY\n")
        val broken = write(folder, "Broken.cue", "FILE \"Game\uFFFD.bin\" BINARY\n")
        assertNull(DiscSet.problem(absolute))
        assertNull(DiscSet.problem(broken))
    }
}
