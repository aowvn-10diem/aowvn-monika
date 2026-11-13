package vn.aow.monika.runner

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CoreSystemFilesTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun folder(vararg path: String): File = tmp.newFolder().let { File(it, path.joinToString("/")).apply { mkdirs() } }

    @Test fun citraBaoThieuKhiCaBaNoiDeuKhongCoTep() {
        val systemDir = folder("system")
        val saveDir = folder("saves")
        assertEquals("shared_font.bin", CoreSystemFiles.missing("citra", systemDir, saveDir))
        val crumb = CoreSystemFiles.crumb("citra", systemDir, saveDir)
        assertNotNull(crumb)
        // Nêu tên tệp cần thêm, đường dẫn thư mục cần đặt, và nói rõ là không chặn.
        assertTrue(crumb!!.contains("shared_font.bin"))
        assertTrue(crumb.contains(File(File(saveDir, "Citra"), "sysdata").absolutePath))
        assertTrue(crumb.contains("không chặn"))
    }

    @Test fun khongBaoKhiTepNamTrongThuMucSaveCuaGame() {
        val systemDir = folder("system")
        val saveDir = folder("saves")
        File(File(saveDir, "Citra/sysdata"), "shared_font.bin").apply { parentFile!!.mkdirs() }.writeText("font")
        assertNull(CoreSystemFiles.missing("citra", systemDir, saveDir))
        assertNull(CoreSystemFiles.crumb("citra", systemDir, saveDir))
    }

    @Test fun khongBaoKhiTepNamTrongThuMucSystemCuaApp() {
        // Bản lõi đặt user dir = system (save dir rỗng) hoặc bản cũ đọc thẳng `sysdata`.
        for (relative in listOf("Citra/sysdata", "sysdata")) {
            val systemDir = tmp.newFolder()
            File(File(systemDir, relative), "shared_font.bin").apply { parentFile!!.mkdirs() }.writeText("font")
            assertNull(relative, CoreSystemFiles.missing("citra", systemDir, null))
        }
    }

    @Test fun loiKhacKhongBiChanOan() {
        val systemDir = folder("system")
        for (coreId in listOf("melondsds", "ppsspp", "desmume", "melonds")) {
            assertNull(coreId, CoreSystemFiles.missing(coreId, systemDir, folder("saves")))
            assertNull(coreId, CoreSystemFiles.crumb(coreId, systemDir, null))
        }
    }
}
