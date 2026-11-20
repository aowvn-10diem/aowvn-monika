package vn.aow.monika.library

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ca kiểm cho [NdsContent]: mỗi điều kiện chặn phải trùng đúng điều kiện làm lõi `melondsds` trả
 * `retro_load_game() == false` (issue #567), và ROM hợp lệ **không** được chặn oan.
 *
 * Header dựng tay: 4 KiB, logo CRC 0xCF56, ARM9 vào 0x02000800, ARM9/ARM7 nằm trong tệp — giống ROM retail thật.
 */
class NdsContentTest {
    private fun dir(): File = Files.createTempDirectory("nds-content").toFile()

    private fun rom(
        folder: File,
        name: String = "game.nds",
        size: Int = 0x20_000,
        gameCode: String = "AZWE",
        arm9RomOffset: Long = 0x4000,
        arm9Entry: Long = 0x0200_0800,
        arm9Size: Long = 0x1000,
        arm7RomOffset: Long = 0x8000,
        arm7Size: Long = 0x800,
        logoCrc: Int = 0xCF56,
    ): File {
        val bytes = ByteArray(size)
        // Tệp rỗng/quá nhỏ thì không dựng header được (dùng để kiểm đúng nhánh "tệp rỗng").
        if (size >= 4096) {
            put(bytes, 0x0C, gameCode.toByteArray(Charsets.US_ASCII))
            putU32(bytes, 0x20, arm9RomOffset)
            putU32(bytes, 0x24, arm9Entry)
            putU32(bytes, 0x2C, arm9Size)
            putU32(bytes, 0x30, arm7RomOffset)
            putU32(bytes, 0x3C, arm7Size)
            putU16(bytes, 0x15C, logoCrc)
        }
        return File(folder, name).apply { parentFile!!.mkdirs(); writeBytes(bytes) }
    }

    private fun put(target: ByteArray, at: Int, value: ByteArray) {
        value.copyInto(target, at)
    }

    private fun putU32(target: ByteArray, at: Int, value: Long) {
        for (i in 0 until 4) target[at + i] = ((value shr (8 * i)) and 0xFF).toByte()
    }

    private fun putU16(target: ByteArray, at: Int, value: Int) {
        target[at] = (value and 0xFF).toByte()
        target[at + 1] = ((value shr 8) and 0xFF).toByte()
    }

    /** ROM hợp lệ ⇒ không chặn (chặn oan là lỗi nặng hơn bỏ sót ở đây). */
    @Test fun validRomPasses() {
        assertNull(NdsContent.inspect(rom(dir())))
    }

    /** Chỉ áp cho `melondsds`; lõi `melonds` cũ có bộ nạp khác nên không chặn theo bảng này. */
    @Test fun appliesOnlyToMelondsDs() {
        assertTrue(NdsContent.appliesTo("melondsds"))
        assertFalse(NdsContent.appliesTo("melonds"))
        assertFalse(NdsContent.appliesTo("desmume"))
    }

    /** Mở nhầm tệp save (core.cpp:926). */
    @Test fun saveFileIsRejected() {
        val f = rom(dir(), name = "game.sav")
        val p = NdsContent.inspect(f)
        assertNotNull(p)
        assertTrue(p!!.message, p.message.contains("tệp save"))
        assertTrue(p.certain)
    }

    /** Tệp rỗng (core.cpp:930). */
    @Test fun emptyFileIsRejected() {
        val p = NdsContent.inspect(rom(dir(), size = 0))
        assertNotNull(p)
        assertTrue(p!!.message, p.message.contains("rỗng"))
    }

    /** Nhỏ hơn sizeof(NDSHeader) = 4096 (console.cpp:676, NDSCart.cpp:394). */
    @Test fun tinyFileIsRejected() {
        val p = NdsContent.inspect(rom(dir(), size = 512))
        assertNotNull(p)
        assertTrue(p!!.message, p.message.contains("4 KB"))
    }

    /** Đúng 4096 byte nhưng header vô nghĩa ⇒ vẫn bị chặn vì logo/địa chỉ sai. */
    @Test fun headerSizedButGarbageIsRejected() {
        val folder = dir()
        val garbage = File(folder, "garbage.nds").apply { writeBytes(ByteArray(4096) { 0x41 }) }
        val p = NdsContent.inspect(garbage)
        assertNotNull(p)
        assertTrue(p!!.certain)
    }

    /** Địa chỉ vào ARM9 ngoài [0x02000000, 0x023BFE00] (console.cpp:684). */
    @Test fun badArm9EntryIsRejected() {
        val p = NdsContent.inspect(rom(dir(), arm9Entry = 0x0100_0000))
        assertNotNull(p)
        assertTrue(p!!.message, p.message.contains("ARM9"))
    }

    /** Logo Nintendo sai và không phải homebrew (console.cpp:689). */
    @Test fun badLogoIsRejected() {
        val p = NdsContent.inspect(rom(dir(), logoCrc = 0x1234))
        assertNotNull(p)
        assertTrue(p!!.message, p.message.contains("logo Nintendo"))
    }

    /** Homebrew: logo sai nhưng ARM9ROMOffset < 0x4000 ⇒ lõi chấp nhận, app không được chặn. */
    @Test fun homebrewWithoutLogoPasses() {
        assertNull(NdsContent.inspect(rom(dir(), arm9RomOffset = 0x200, logoCrc = 0x0000)))
    }

    /** Homebrew nhận diện bằng game code "####" cũng không bị chặn vì logo. */
    @Test fun hashGameCodeWithoutLogoPasses() {
        assertNull(NdsContent.inspect(rom(dir(), gameCode = "####", arm9RomOffset = 0x8000, logoCrc = 0x0000)))
    }

    /** Tệp bị cắt cụt: ARM9 trỏ ra ngoài kích thước đã làm tròn luỹ thừa 2 (NDSCart.cpp:365-379). */
    @Test fun truncatedRomIsRejected() {
        // 0x20_000 byte ⇒ làm tròn 0x20_000; ARM9 ở 0x4000 dài 0x1F_0000 ⇒ vượt.
        val p = NdsContent.inspect(rom(dir(), arm9Size = 0x1F_0000))
        assertNotNull(p)
        assertTrue(p!!.message, p.message.contains("cắt cụt"))
    }

    /** ARM9ROMOffset < 0x200 (NDSCart.cpp:369) — header rác, không phải ROM. */
    @Test fun arm9OffsetBelowHeaderIsRejected() {
        val p = NdsContent.inspect(rom(dir(), arm9RomOffset = 0x100))
        assertNotNull(p)
        assertTrue(p!!.certain)
    }

    /** Lớn hơn 512 MB (NDSCart.cpp:400). */
    @Test fun oversizeFileIsRejected() {
        val folder = dir()
        val big = File(folder, "big.nds")
        java.io.RandomAccessFile(big, "rw").use { it.setLength(512L * 1024 * 1024 + 1) }
        val p = NdsContent.inspect(big)
        assertNotNull(p)
        assertTrue(p!!.message, p.message.contains("512 MB"))
    }

    /** Không đọc được tệp (không tồn tại) ⇒ trả null, để lõi tự báo lỗi, không kết luận oan. */
    @Test fun unreadableFileIsNotJudged() {
        assertNull(NdsContent.inspect(File(dir(), "khong-co.nds")))
    }

    /** Tệp 4 KB đúng chuẩn header nhưng không đủ chỗ cho ARM9 ⇒ chặn (lõi cũng trả false). */
    @Test fun cartSmallerThanArm9PayloadIsRejected() {
        val p = NdsContent.inspect(rom(dir(), size = 4096, arm9RomOffset = 0x200, arm9Size = 0x2000))
        assertNotNull(p)
        assertEquals(true, p!!.certain)
    }
}
