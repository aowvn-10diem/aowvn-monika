package vn.aow.monika.runner

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import vn.aow.monika.diag.Diagnostics
import java.io.File

class CoreSystemFilesTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun saveDir(root: File) = File(root, "saves/gba/Game/Tên game").apply { mkdirs() }

    @Test fun citraBaoThieuKhiHaiNoiDeuKhongCoTep() {
        val filesDir = tmp.newFolder()
        val systemDir = File(filesDir, "system").apply { mkdirs() }
        val saveDir = saveDir(filesDir)
        assertEquals("shared_font.bin", CoreSystemFiles.missing("citra", systemDir, saveDir))
        val crumb = CoreSystemFiles.crumb("citra", filesDir, systemDir, saveDir)
        assertNotNull(crumb)
        // Nêu tên tệp, thư mục lõi đọc (tương đối), và nói rõ là không chặn.
        assertTrue(crumb!!.contains("shared_font.bin"))
        assertTrue(crumb.contains("Citra/sysdata"))
        assertTrue(crumb.contains("thư mục dữ liệu app"))
        assertTrue(crumb.contains("không chặn"))
        // Không lộ đường dẫn tuyệt đối, và phải sống sót Diagnostics.scrub (Breadcrumbs.add gọi scrub).
        assertTrue(crumb.contains("saves/gba/Game/Tên game/Citra/sysdata"))
        assertFalse(crumb.contains(filesDir.absolutePath))
        assertNotEquals(filesDir.absolutePath, Diagnostics.scrub(null, "đặt vào: ${filesDir.absolutePath}"))
        assertEquals(crumb, Diagnostics.scrub(null, crumb))
        assertTrue("crumb bị Breadcrumbs cắt ở 200 ký tự: ${crumb.length}", crumb.length <= 200)
    }

    @Test fun khongBaoKhiTepNamTrongThuMucSaveCuaGame() {
        val filesDir = tmp.newFolder()
        val systemDir = File(filesDir, "system").apply { mkdirs() }
        val saveDir = saveDir(filesDir)
        File(File(saveDir, "Citra/sysdata"), "shared_font.bin").apply { parentFile!!.mkdirs() }.writeText("font")
        assertNull(CoreSystemFiles.missing("citra", systemDir, saveDir))
        assertNull(CoreSystemFiles.crumb("citra", filesDir, systemDir, saveDir))
    }

    @Test fun khongBaoKhiTepNamTrongThuMucSystemDuPhong() {
        // Nhánh "frontend không cấp save dir" của lõi: user dir = <system>/Citra.
        val filesDir = tmp.newFolder()
        val systemDir = File(filesDir, "system").apply { mkdirs() }
        File(File(systemDir, "Citra/sysdata"), "shared_font.bin").apply { parentFile!!.mkdirs() }.writeText("font")
        assertNull(CoreSystemFiles.missing("citra", systemDir, null))
        assertNull(CoreSystemFiles.crumb("citra", filesDir, systemDir, null))
        assertEquals(listOf(File(File(systemDir, "Citra"), "sysdata")), CoreSystemFiles.dirs(systemDir, null))
    }

    @Test fun loiKhacKhongBiChanOan() {
        val filesDir = tmp.newFolder()
        val systemDir = File(filesDir, "system").apply { mkdirs() }
        for (coreId in listOf("melondsds", "ppsspp", "desmume", "melonds")) {
            assertNull(coreId, CoreSystemFiles.missing(coreId, systemDir, saveDir(filesDir)))
            assertNull(coreId, CoreSystemFiles.crumb(coreId, filesDir, systemDir, null))
        }
    }
}
