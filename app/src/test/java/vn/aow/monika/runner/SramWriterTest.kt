package vn.aow.monika.runner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** V202 PR3: ghi SRAM nguyên tử; lỗi giữa chừng không làm hỏng tệp save cũ. */
class SramWriterTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun writesNewContentAndLeavesNoTempFile() {
        val dir = tmp.newFolder("sram")
        val file = File(dir, "game.srm").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertTrue(SramWriter.writeAtomic(file, byteArrayOf(9, 9)))
        assertEquals(listOf<Byte>(9, 9), file.readBytes().toList())
        assertFalse(File(dir, "game.srm.tmp").exists())
    }

    @Test fun failureKeepsOldSave() {
        val dir = tmp.newFolder("sram-bad")
        val file = File(dir, "game.srm").apply { writeBytes(byteArrayOf(7, 7, 7)) }
        // Tạo sẵn ".tmp" là thư mục → FileOutputStream không mở được, mô phỏng lỗi giữa chừng.
        File(dir, "game.srm.tmp").mkdirs()
        assertFalse(SramWriter.writeAtomic(file, byteArrayOf(1)))
        assertEquals(listOf<Byte>(7, 7, 7), file.readBytes().toList())
    }
}
