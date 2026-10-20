package vn.aow.monika

import org.junit.Assert.*
import org.junit.Test
import vn.aow.monika.config.defaultSaveProfiles
import vn.aow.monika.library.SaveFormats
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SaveFormatsTest {
    private val gba = defaultSaveProfiles().getValue("gba")
    private val nds = defaultSaveProfiles().getValue("nds")
    private fun rejected(block: () -> Unit) { assertThrows(IOException::class.java, block) }
    private fun zip(vararg files: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { z -> files.forEach { (name, bytes) -> z.putNextEntry(ZipEntry(name)); z.write(bytes); z.closeEntry() } }
    }.toByteArray()

    @Test fun gbaSavSrmPreserveEverySupportedSizeAndPadOnlyWithFF() {
        gba.sizes.forEach { size -> val raw = ByteArray(size) { (it % 251).toByte() }
            assertArrayEquals(raw, SaveFormats.convert(raw, "sav", gba))
            assertArrayEquals(raw, SaveFormats.convert(raw, "SRM", gba))
        }
        val odd = ByteArray(9000) { 23 }
        val padded = SaveFormats.convert(odd, "sav", gba)
        assertEquals(32768, padded.size); assertArrayEquals(odd, padded.copyOf(9000))
        assertTrue(padded.drop(9000).all { it == 0xff.toByte() })
        rejected { SaveFormats.convert(ByteArray(511), "sav", gba) }
        rejected { SaveFormats.convert(ByteArray(131073), "sav", gba) }
        rejected { SaveFormats.convert(ByteArray(8192), "state", gba) }
        rejected { SaveFormats.convert(ByteArray(32768) { 1 }, "sav", gba.copy(targetBytes = 8192)) }
    }

    @Test fun ndsDsvRequiresWholeVerifiedFooterAndRawSavSrmKeepPayload() {
        val raw = ByteArray(8192) { (it % 239).toByte() }
        val meta = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).putInt(raw.size).putInt(raw.size).putInt(1).putInt(2).putInt(raw.size).putInt(0).array()
        val dsv = raw + SaveFormats.DSV_TEXT.toByteArray() + meta + SaveFormats.DSV_COOKIE.toByteArray()
        assertEquals(122, dsv.size - raw.size)
        assertArrayEquals(raw, SaveFormats.convert(dsv, "dsv", nds))
        assertArrayEquals(raw, SaveFormats.convert(raw, "sav", nds))
        assertArrayEquals(raw, SaveFormats.convert(raw, "srm", nds))
        rejected { SaveFormats.convert(dsv.copyOf(dsv.size - 1), "dsv", nds) }
        rejected { SaveFormats.convert(dsv.copyOf().also { it[raw.size] = 0 }, "dsv", nds) }
        rejected { SaveFormats.convert(dsv.copyOf().also { it[it.size - 19] = 1 }, "dsv", nds) }
        rejected { SaveFormats.convert(dsv.copyOf().also { it[raw.size + SaveFormats.DSV_TEXT.length + 4] = 1 }, "dsv", nds) }
    }

    @Test fun archiveRejectsTraversalAbsoluteBackslashEmptyAndBombBeforeWriting() {
        for (name in listOf("../game.sav", "/game.sav", "saves/../../rom.gba", "x\\y.sav", "x:y.sav")) {
            rejected { SaveFormats.archive(zip(name to ByteArray(512)).inputStream()) }
        }
        rejected { SaveFormats.archive(zip().inputStream()) }
        rejected { SaveFormats.read(ByteArray(20).inputStream(), 19) }
        assertEquals(setOf("Game.sav"), SaveFormats.archive(zip("Game.sav" to ByteArray(512)).inputStream()).keys)
        val many = (0..SaveFormats.MAX_FILES).map { "$it.sav" to byteArrayOf(1) }.toTypedArray()
        rejected { SaveFormats.archive(zip(*many).inputStream()) }
    }

    internal fun rms(): Map<String, ByteArray> = mapOf(
        "progress.rsh" to ByteArrayOutputStream().also { out -> DataOutputStream(out).use {
            it.writeBytes("MIDRMS"); it.write(byteArrayOf(3,0,0)); it.writeUTF("progress"); it.writeLong(0); it.writeInt(1)
            it.writeInt(0); it.writeByte(0); it.writeInt(1); it.writeInt(1)
        } }.toByteArray(),
        "progress.1.rsr" to ByteArrayOutputStream().also { out -> DataOutputStream(out).use { it.writeInt(1); it.writeInt(0); it.writeInt(3); it.write(byteArrayOf(1,2,3)) } }.toByteArray(),
    )

    @Test fun javaLoaderRmsPairsHeaderRecordAndRejectsMissingCorruptEncryptedOrRom() {
        val files = rms(); SaveFormats.validateRms(files)
        rejected { SaveFormats.validateRms(files - "progress.rsh") }
        rejected { SaveFormats.validateRms(files - "progress.1.rsr") }
        rejected { SaveFormats.validateRms(files + ("game.jar" to byteArrayOf(1))) }
        rejected { SaveFormats.validateRms(files + ("progress.rsh" to files.getValue("progress.rsh").copyOf().also { it[8] = 1 })) }
        rejected { SaveFormats.validateRms(files + ("progress.1.rsr" to byteArrayOf(1,2))) }
    }
}
