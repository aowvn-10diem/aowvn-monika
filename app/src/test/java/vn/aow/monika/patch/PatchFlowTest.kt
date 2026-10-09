package vn.aow.monika.patch

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.library.GameMeta
import vn.aow.monika.library.GameStorage
import java.io.File
import java.io.RandomAccessFile

/** Luồng vá Việt hóa: ghi bản đã vá vào thư mục riêng cạnh ROM gốc, đặt tên không đè, báo lỗi bằng tiếng Việt. Bản vá IPS tự sinh. */
@RunWith(RobolectricTestRunner::class)
@Config(application = vn.aow.monika.ui.TestApp::class, sdk = [34])
class PatchFlowTest {
    @get:Rule val tmp = TemporaryFolder()
    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    @Before fun cleanLibrary() {
        GameStorage.games(context).listFiles().orEmpty().forEach { it.deleteRecursively() }
    }

    /** IPS tối thiểu: "PATCH" + (offset 3 byte, độ dài 2 byte, dữ liệu) + "EOF". */
    private fun ips(offset: Int, data: ByteArray): ByteArray =
        "PATCH".toByteArray() +
            byteArrayOf((offset shr 16).toByte(), (offset shr 8).toByte(), offset.toByte()) +
            byteArrayOf((data.size shr 8).toByte(), data.size.toByte()) + data + "EOF".toByteArray()

    private fun rom(folderName: String = "Pokemon"): File {
        val dir = File(GameStorage.games(context), folderName).apply { mkdirs() }
        GameMeta.write(dir, GameMeta(title = folderName, postUrl = "https://aow.vn/p/x"))
        return File(dir, "game.gba").apply { writeBytes(ByteArray(16) { it.toByte() }) }
    }

    private fun patchFile(bytes: ByteArray): Uri = Uri.fromFile(File(tmp.newFolder(), "fix.bin").apply { writeBytes(bytes) })

    private fun run(rom: File, patch: Uri) = runBlocking { PatchFlow.run(context, rom, patch) }

    @Test fun patchedCopyGoesToItsOwnFolderAndOriginalRomIsUntouched() {
        val rom = rom()
        val original = rom.readBytes()

        val message = run(rom, patchFile(ips(2, byteArrayOf(0x7F, 0x7E))))

        assertTrue(message, message.startsWith("Đã vá (IPS)"))
        assertTrue(message.contains("\"Pokemon (Việt hóa)\""))
        val patched = File(GameStorage.games(context), "Pokemon (Việt hóa)/game.gba")
        val expected = original.copyOf().also { it[2] = 0x7F; it[3] = 0x7E }
        assertArrayEquals(expected, patched.readBytes())
        assertArrayEquals(original, rom.readBytes())
        assertFalse(File(patched.parentFile, "game.gba.part").exists())
        // Tên hiển thị của game đã vá kế thừa từ bài viết gốc + hậu tố.
        assertEquals("Pokemon (Việt hóa)", GameMeta.read(patched.parentFile!!)?.title)
    }

    @Test fun secondPatchGetsNumberedFolderInsteadOfOverwriting() {
        val rom = rom()
        val patch = patchFile(ips(0, byteArrayOf(9)))

        run(rom, patch)
        val message = run(rom, patch)

        assertTrue(message, message.contains("\"Pokemon (Việt hóa 2)\""))
        assertTrue(File(GameStorage.games(context), "Pokemon (Việt hóa)/game.gba").isFile)
        assertTrue(File(GameStorage.games(context), "Pokemon (Việt hóa 2)/game.gba").isFile)
    }

    @Test fun unknownPatchFormatIsRejectedWithoutCreatingFolders() {
        val rom = rom()

        val message = run(rom, patchFile("không phải bản vá".toByteArray()))

        assertEquals("Không nhận ra định dạng bản vá (hỗ trợ IPS, BPS, UPS).", message)
        assertEquals(listOf("Pokemon"), GameStorage.games(context).list().orEmpty().toList())
    }

    @Test fun truncatedIpsReportsMissingEofAndWritesNothing() {
        val rom = rom()
        val broken = "PATCH".toByteArray() + byteArrayOf(0, 0, 1, 0, 1, 5) // thiếu EOF

        val message = run(rom, patchFile(broken))

        assertTrue(message, message.contains("EOF") || message.contains("cắt cụt"))
        assertEquals(listOf("Pokemon"), GameStorage.games(context).list().orEmpty().toList())
    }

    @Test fun missingPatchFileIsReportedAsFailure() {
        val rom = rom()

        val message = run(rom, Uri.fromFile(File(tmp.root, "khong-ton-tai.ips")))

        assertTrue(message, message.startsWith("Không vá được:") || message == "Không đọc được file bản vá.")
        assertEquals(listOf("Pokemon"), GameStorage.games(context).list().orEmpty().toList())
    }

    @Test fun hugeRomIsRefusedBeforeAnythingIsRead() {
        val rom = rom()
        RandomAccessFile(rom, "rw").use { it.setLength(257L * 1024 * 1024) } // thưa, không tốn dung lượng thật

        val message = run(rom, patchFile(ips(0, byteArrayOf(1))))

        assertEquals("File game quá lớn để vá trong app (257 MB).", message)
        assertEquals(listOf("Pokemon"), GameStorage.games(context).list().orEmpty().toList())
    }
}
