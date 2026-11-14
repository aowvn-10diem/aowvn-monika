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
        // Không có khoá trong config, không người chơi chỉnh → lõi dùng mặc định "LibRetro Default".
        for (option in listOf(null, CoreSystemFiles.LIBRETRO_SAVE_PATH)) {
            assertEquals(option, "shared_font.bin", CoreSystemFiles.missing("citra", systemDir, saveDir, option))
        }
        val crumb = CoreSystemFiles.crumb("citra", filesDir, systemDir, saveDir, null)
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
        assertNull(CoreSystemFiles.missing("citra", systemDir, saveDir, null))
        assertNull(CoreSystemFiles.crumb("citra", filesDir, systemDir, saveDir, null))
    }

    @Test fun khongBaoOanKhiLoiDungDuongSaveRiengCuaCitra() {
        // "Citra Default" = lõi giữ user dir riêng, KHÔNG phải <save>/Citra → app không biết chỗ đọc,
        // nên dù không thấy tệp cũng không được báo thiếu.
        val filesDir = tmp.newFolder()
        val systemDir = File(filesDir, "system").apply { mkdirs() }
        assertFalse(CoreSystemFiles.usesGameSaveDir("Citra Default"))
        assertTrue(CoreSystemFiles.usesGameSaveDir(null))
        assertTrue(CoreSystemFiles.usesGameSaveDir(CoreSystemFiles.LIBRETRO_SAVE_PATH))
        assertNull(CoreSystemFiles.missing("citra", systemDir, saveDir(filesDir), "Citra Default"))
        assertNull(CoreSystemFiles.crumb("citra", filesDir, systemDir, saveDir(filesDir), "Citra Default"))
    }

    @Test fun khongBaoKhiTepNamTrongThuMucSystemDuPhong() {
        // Nhánh "frontend không cấp save dir" của lõi (vẫn là "LibRetro Default"): user dir = <system>/Citra.
        val filesDir = tmp.newFolder()
        val systemDir = File(filesDir, "system").apply { mkdirs() }
        File(File(systemDir, "Citra/sysdata"), "shared_font.bin").apply { parentFile!!.mkdirs() }.writeText("font")
        assertNull(CoreSystemFiles.missing("citra", systemDir, null, null))
        assertNull(CoreSystemFiles.crumb("citra", filesDir, systemDir, null, null))
        assertEquals(listOf(File(File(systemDir, "Citra"), "sysdata")), CoreSystemFiles.dirs(systemDir, null))
    }

    @Test fun loiKhacKhongBiChanOan() {
        val filesDir = tmp.newFolder()
        val systemDir = File(filesDir, "system").apply { mkdirs() }
        for (coreId in listOf("melondsds", "ppsspp", "desmume", "melonds")) {
            assertNull(coreId, CoreSystemFiles.missing(coreId, systemDir, saveDir(filesDir), null))
            assertNull(coreId, CoreSystemFiles.crumb(coreId, filesDir, systemDir, null, null))
        }
    }
}
