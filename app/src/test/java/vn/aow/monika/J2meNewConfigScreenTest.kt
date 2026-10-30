package vn.aow.monika

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.playsoftware.j2meloader.config.MonikaScreenDefaults
import ru.playsoftware.j2meloader.config.ProfilesManager
import java.io.File
import java.nio.file.Files

/**
 * V135: nhánh cấu hình mới sao từ profile mặc định — `loadConfig()` thấy config.json đã có
 * nên `params != null`, nhưng vẫn phải áp kích thước đoán và ghi lại để lần mở sau còn giữ.
 * Game đã có cấu hình từ trước thì không bị đè.
 */
class J2meNewConfigScreenTest {
    private val dir = Files.createTempDirectory("j2me-new-screen").toFile()

    /** Giả lập file config.json đã có sẵn (profile sao sang hoặc sếp chỉnh tay). */
    private fun writeConfig(width: Int, height: Int) {
        File(dir, "config.json").writeText("""{"Version":3,"ScreenWidth":$width,"ScreenHeight":$height}""")
    }

    private fun load() = ProfilesManager.loadConfig(dir)!!

    @After
    fun clean() {
        dir.deleteRecursively()
    }

    @Test
    fun `cau hinh moi sao tu profile van duoc ap kich thuoc va giu lai khi mo lai`() {
        writeConfig(176, 208)
        val params = load()
        // loadConfig() đã thấy params != null, nhưng đây vẫn là cấu hình mới (trước đó chưa có config.json).
        assertTrue(MonikaScreenDefaults.applyToNewConfig(true, params, 240, 320))
        assertEquals(240, params.screenWidth)
        assertEquals(320, params.screenHeight)

        // Lần mở sau đọc lại từ file: kích thước đoán được vẫn còn.
        val reopened = load()
        assertEquals(240, reopened.screenWidth)
        assertEquals(320, reopened.screenHeight)
    }

    @Test
    fun `game da co cau hinh thi giu nguyen kich thuoc`() {
        writeConfig(176, 208)
        val params = load()
        assertFalse(MonikaScreenDefaults.applyToNewConfig(false, params, 240, 320))
        assertEquals(176, params.screenWidth)

        val reopened = load()
        assertEquals(176, reopened.screenWidth)
        assertEquals(208, reopened.screenHeight)
    }

    @Test
    fun `khong doan duoc kich thuoc thi giu profile mac dinh`() {
        writeConfig(176, 208)
        val params = load()
        assertFalse(MonikaScreenDefaults.applyToNewConfig(true, params, 0, 0))
        assertEquals(176, params.screenWidth)
        assertEquals(208, load().screenHeight)
    }
}
