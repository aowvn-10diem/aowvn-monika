package vn.aow.monika

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Bản dịch menu Kirikiri phải đủ mọi id của bản gốc (thiếu id → menu hiện chuỗi rỗng/tiếng Anh). */
class KirikiriLocaleTest {
    private fun root(): File = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "kirikiri/reference/en_us.xml").isFile }
    private fun ids(f: File) = Regex("""<Item id="([^"]+)"""").findAll(f.readText()).map { it.groupValues[1] }.toList()

    @Test fun viCoDuMoiId() {
        val en = ids(File(root(), "kirikiri/reference/en_us.xml"))
        val vi = ids(File(root(), "kirikiri/overlay/assets/locale/vi_vn.xml"))
        assertTrue(en.size > 100)
        assertEquals(en.sorted(), vi.sorted())
    }

    @Test fun viCoDauTiengViet() {
        assertTrue(File(root(), "kirikiri/overlay/assets/locale/vi_vn.xml").readText().contains("Cài đặt"))
    }
}
