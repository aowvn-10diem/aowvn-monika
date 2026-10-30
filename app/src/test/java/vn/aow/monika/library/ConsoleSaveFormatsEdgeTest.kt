package vn.aow.monika.library

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/**
 * Ca biên cho ConsoleSaveFormats: ps1, sfo, title3ds, pspDisc.
 * SaveTransferException kế thừa java.io.IOException nên mọi "ném" đều assertThrows(IOException::class.java).
 */
class ConsoleSaveFormatsEdgeTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun put32(b: ByteArray, at: Int, value: Int) {
        b[at] = value.toByte()
        b[at + 1] = (value shr 8).toByte()
        b[at + 2] = (value shr 16).toByte()
        b[at + 3] = (value shr 24).toByte()
    }

    // raw.take(127).fold(0) { sum, b -> sum xor (b and 255) } == raw[127]
    private fun fixChecksum(raw: ByteArray) {
        var sum = 0
        for (i in 0 until 127) sum = sum xor (raw[i].toInt() and 255)
        raw[127] = sum.toByte()
    }

    /** Thẻ 128 KiB hợp lệ tối thiểu: "MC" + checksum XOR 127 byte đầu. */
    private fun card(): ByteArray = ByteArray(131072).also {
        it[0] = 'M'.code.toByte()
        it[1] = 'C'.code.toByte()
        fixChecksum(it)
    }

    /** PARAM.SFO little-endian: header 20 byte + bảng entry 16 byte/khóa + khóa + dữ liệu. */
    private fun sfo(vararg entries: Pair<String, String>): ByteArray {
        val keyBlob = entries.joinToString("") { it.first + "\u0000" }.toByteArray(Charsets.US_ASCII)
        val dataBlob = entries.joinToString("") { it.second + "\u0000" }.toByteArray(Charsets.US_ASCII)
        val keysAt = 20 + entries.size * 16
        val dataAt = keysAt + keyBlob.size
        val b = ByteArray(dataAt + dataBlob.size)
        byteArrayOf(0, 80, 83, 70).copyInto(b)
        put32(b, 4, 0x101)
        put32(b, 8, keysAt)
        put32(b, 12, dataAt)
        put32(b, 16, entries.size)
        var keyOff = 0
        var dataOff = 0
        entries.forEachIndexed { i, e ->
            val at = 20 + i * 16
            b[at] = keyOff.toByte()
            b[at + 1] = (keyOff shr 8).toByte()
            b[at + 2] = 4
            b[at + 3] = 2
            val size = e.second.toByteArray(Charsets.US_ASCII).size + 1
            put32(b, at + 4, size)
            put32(b, at + 8, size)
            put32(b, at + 12, dataOff)
            keyOff += e.first.length + 1
            dataOff += size
        }
        keyBlob.copyInto(b, keysAt)
        dataBlob.copyInto(b, dataAt)
        return b
    }

    // check(bytes.size == 131072 + 3904, "Thẻ DexDrive GME không đúng dung lượng.") — gme = 11 byte đầu == "123-456-STD"
    @Test fun ps1DexDriveWithWrongTotalSizeThrows() {
        val almost = ByteArray(131072 + 3904 - 1)
        "123-456-STD".toByteArray(Charsets.US_ASCII).copyInto(almost)
        assertThrows(IOException::class.java) { ConsoleSaveFormats.ps1(almost) }
    }

    // bytes.copyOfRange(3904, bytes.size) — dữ liệu sau header 3904 byte giữ nguyên, không lẫn header
    @Test fun ps1DexDriveKeepsPayloadAfter3904ByteHeader() {
        val raw = card()
        raw[500] = 0x5A
        val gme = ByteArray(3904 + raw.size)
        "123-456-STD".toByteArray(Charsets.US_ASCII).copyInto(gme)
        raw.copyInto(gme, 3904)
        assertArrayEquals(raw, ConsoleSaveFormats.ps1(gme))
    }

    // raw[0] == 'M' && raw[1] == 'C' phải đúng trước khi xét checksum
    @Test fun ps1CardWithoutMcMagicThrows() {
        val raw = card()
        raw[0] = 'X'.code.toByte()
        fixChecksum(raw)
        assertThrows(IOException::class.java) { ConsoleSaveFormats.ps1(raw) }
    }

    // checksum = XOR 127 byte đầu phải bằng raw[127]
    @Test fun ps1CardWithWrongChecksumThrows() {
        val raw = card()
        raw[127] = (raw[127].toInt() + 1).toByte()
        assertThrows(IOException::class.java) { ConsoleSaveFormats.ps1(raw) }
    }

    // bytes.copyOfRange(0, 4).contentEquals(byteArrayOf(0, 80, 83, 70)) — magic "\0PSF"
    @Test fun sfoWrongMagicThrows() {
        val b = sfo("DISC_ID" to "ULUS12345")
        b[0] = 1
        assertThrows(IOException::class.java) { ConsoleSaveFormats.sfo(b, "DISC_ID") }
    }

    // u32(bytes, 4) == 0x101L — phiên bản SFO phải đúng 0x101
    @Test fun sfoWrongVersionThrows() {
        val b = sfo("DISC_ID" to "ULUS12345")
        put32(b, 4, 0x100)
        assertThrows(IOException::class.java) { ConsoleSaveFormats.sfo(b, "DISC_ID") }
    }

    // result ?: throw SaveTransferException("PARAM.SFO thiếu mã game/thư mục save.")
    @Test fun sfoMissingRequestedKeyThrows() {
        val b = sfo("TITLE_ID" to "ULUS12345")
        assertThrows(IOException::class.java) { ConsoleSaveFormats.sfo(b, "DISC_ID") }
    }

    // check(result == null && ...) — khóa xuất hiện lần thứ hai là lỗi "trùng"
    @Test fun sfoDuplicateKeyThrows() {
        val b = sfo("DISC_ID" to "ULUS12345", "DISC_ID" to "ULUS99999")
        assertThrows(IOException::class.java) { ConsoleSaveFormats.sfo(b, "DISC_ID") }
    }

    // bytes[at + 2] == 4.toByte() && bytes[at + 3] == 2.toByte() — UTF8 kiểu chuỗi, entry tại at = 20
    @Test fun sfoWrongValueTypeThrows() {
        val badSecondByte = sfo("DISC_ID" to "ULUS12345")
        badSecondByte[22] = 0
        assertThrows(IOException::class.java) { ConsoleSaveFormats.sfo(badSecondByte, "DISC_ID") }
        val badThirdByte = sfo("DISC_ID" to "ULUS12345")
        badThirdByte[23] = 0
        assertThrows(IOException::class.java) { ConsoleSaveFormats.sfo(badThirdByte, "DISC_ID") }
    }

    // offset = data + u32(bytes, at + 12); value = bytes.copyOfRange(offset, offset + size) — nhiều khóa, lấy đúng từng giá trị
    @Test fun sfoReadsCorrectValueAmongManyKeys() {
        val b = sfo("TITLE" to "Fixture", "DISC_ID" to "ULUS12345", "CATEGORY" to "UG")
        assertEquals("ULUS12345", ConsoleSaveFormats.sfo(b, "DISC_ID"))
        assertEquals("Fixture", ConsoleSaveFormats.sfo(b, "TITLE"))
        assertEquals("UG", ConsoleSaveFormats.sfo(b, "CATEGORY"))
    }

    /**
     * ISO9660 tối thiểu: PVD ở sector 16, root dir ở sector 20 (record gốc tại pvd+156 -> u32(pvd,158)=20,
     * u32(pvd,166)=2048), tùy chọn thư mục PSP_GAME sector 21 và PARAM.SFO sector 22.
     */
    private fun iso(discId: String, pspGame: Boolean = true, corruptPvdMagic: Boolean = false): ByteArray {
        val disc = sfo("DISC_ID" to discId)
        val b = ByteArray(24 * 2048)
        fun record(at: Int, sector: Int, size: Int, name: ByteArray, dir: Boolean) {
            b[at] = (33 + name.size + (if (name.size % 2 == 0) 1 else 0)).toByte()
            put32(b, at + 2, sector)
            put32(b, at + 10, size)
            b[at + 25] = if (dir) 2 else 0
            b[at + 32] = name.size.toByte()
            name.copyInto(b, at + 33)
        }
        val pvd = 16 * 2048
        b[pvd] = 1
        "CD001".toByteArray(Charsets.US_ASCII).copyInto(b, pvd + 1)
        b[pvd + 6] = 1
        if (corruptPvdMagic) b[pvd + 2] = 'X'.code.toByte()
        record(pvd + 156, 20, 2048, byteArrayOf(0), true)
        if (pspGame) {
            record(20 * 2048, 21, 2048, "PSP_GAME".toByteArray(Charsets.US_ASCII), true)
            record(21 * 2048, 22, disc.size, "PARAM.SFO;1".toByteArray(Charsets.US_ASCII), false)
            disc.copyInto(b, 22 * 2048)
        }
        return b
    }

    @Test fun title3dsRejectsFileShorterThanHeader() {
        // check(offset >= 0 && offset + size <= input.length()) trong read(0, 512)
        val file = tmp.newFile("short.cxi")
        file.writeBytes(ByteArray(200))
        assertThrows(IOException::class.java) { ConsoleSaveFormats.title3ds(file) }
    }

    @Test fun title3dsRejectsNcsdWhosePartitionIsNotNcch() {
        // magic khối 256 = "NCSD" nên đọc phân vùng tại u32(top, 0x120) * 512 = 0; check(ncch magic == "NCCH") ném
        val container = ByteArray(1024)
        "NCSD".toByteArray(Charsets.US_ASCII).copyInto(container, 256)
        put32(container, 0x120, 0)
        val file = tmp.newFile("ncsd.cxi")
        file.writeBytes(container)
        assertThrows(IOException::class.java) { ConsoleSaveFormats.title3ds(file) }
    }

    @Test fun title3dsRejectsZeroTitleId() {
        // check(text.startsWith("00040000") && id != 0L), id = 0 tại 0x118 nên text = "0000000000000000"
        val ncch = ByteArray(512)
        "NCCH".toByteArray(Charsets.US_ASCII).copyInto(ncch, 256)
        val file = tmp.newFile("zero.cxi")
        file.writeBytes(ncch)
        assertThrows(IOException::class.java) { ConsoleSaveFormats.title3ds(file) }
    }

    @Test fun pspDiscRejectsPvdWithoutCd001() {
        // check(pvd[0] == 1.toByte() && pvd.copyOfRange(1, 6) == "CD001" && pvd[6] == 1.toByte()), pvd[2] = 'X'
        val file = File(tmp.root, "badpvd.iso")
        file.writeBytes(iso("ULUS12345", corruptPvdMagic = true))
        assertThrows(IOException::class.java) { ConsoleSaveFormats.pspDisc(file) }
    }

    @Test fun pspDiscRejectsLowercaseDiscId() {
        // check(disc.matches(Regex("[A-Z]{4}[0-9]{5}"))), "ulus12345" hỏng A-Z
        val file = File(tmp.root, "lower.iso")
        file.writeBytes(iso("ulus12345"))
        assertThrows(IOException::class.java) { ConsoleSaveFormats.pspDisc(file) }
    }

    @Test fun pspDiscRejectsDiscIdWithTooFewDigits() {
        // check(disc.matches(Regex("[A-Z]{4}[0-9]{5}"))), "ULUS1234" chỉ có 4 chữ số
        val file = File(tmp.root, "shortid.iso")
        file.writeBytes(iso("ULUS1234"))
        assertThrows(IOException::class.java) { ConsoleSaveFormats.pspDisc(file) }
    }

    @Test fun pspDiscRejectsIsoWithoutPspGameDirectory() {
        // find(root, "PSP_GAME", true) duyệt record rỗng (len = 0) hết sector rồi ném SaveTransferException
        val file = File(tmp.root, "nopspgame.iso")
        file.writeBytes(iso("ULUS12345", pspGame = false))
        assertThrows(IOException::class.java) { ConsoleSaveFormats.pspDisc(file) }
    }
}
