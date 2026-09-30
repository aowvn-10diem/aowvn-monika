package vn.aow.monika

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.aow.monika.config.ConfigRepository
import vn.aow.monika.config.CoreDisplay
import vn.aow.monika.config.DisplayStyle
import vn.aow.monika.runner.DisplayStyles
import vn.aow.monika.runner.LcdGrid
import java.io.File

class DisplayStyleTest {
    private val disp = CoreDisplay(
        native = listOf(240, 160), default = "lcd",
        styles = linkedMapOf("lcd" to DisplayStyle("LCD"), "sharp" to DisplayStyle("Sắc nét"), "smooth" to DisplayStyle("Mượt")),
    )

    @Test fun resolveAndCycle() {
        assertEquals("lcd", DisplayStyles.resolve(disp, null)?.first)
        assertEquals("sharp", DisplayStyles.resolve(disp, "sharp")?.first)
        assertEquals("lcd", DisplayStyles.resolve(disp, "khong-co")?.first) // lựa chọn cũ không còn trong config → mặc định
        assertEquals("sharp", DisplayStyles.next(disp, "lcd"))
        assertEquals("lcd", DisplayStyles.next(disp, "smooth")) // hết vòng
        assertEquals(null, DisplayStyles.resolve(null, "lcd"))
        assertEquals(null, DisplayStyles.resolve(CoreDisplay(), null))
    }

    @Test fun integerScale() {
        assertEquals(4, DisplayStyles.integerScale(1080, (2400 * 0.58f).toInt(), 240, 160)) // điện thoại 1080 dọc: 960 px = 89% bề ngang
        assertEquals(3, DisplayStyles.integerScale(720, (1600 * 0.58f).toInt(), 240, 160))  // 720 = đúng 3×
        assertEquals(6, DisplayStyles.integerScale(1440, 2000, 240, 160))
        assertEquals(0, DisplayStyles.integerScale(650, 900, 240, 160))   // 2× = 74% → viền đen quá to → lấp đầy kiểu thường
        assertEquals(0, DisplayStyles.integerScale(200, 300, 240, 160))   // màn nhỏ hơn hình gốc
        assertEquals(6, DisplayStyles.integerScale(2400, 1080, 240, 160)) // ngang: bị chiều cao giới hạn (960/1080 = 89%)
        assertEquals(0, DisplayStyles.integerScale(0, 1000, 240, 160))
    }

    @Test fun lcdGridTile() {
        assertTrue(LcdGrid.tile(2, 0.3f).all { it == 0 })      // 2×: lưới ăn mất nửa hình → tắt
        assertTrue(LcdGrid.tile(4, 0f).all { it == 0 })
        val t = LcdGrid.tile(4, 0.3f)
        fun a(x: Int, y: Int) = (t[y * 4 + x] ushr 24)
        assertEquals(0, a(0, 0))                                // tâm điểm ảnh trong suốt
        assertEquals(Math.round(0.3f * 255), a(3, 3))           // góc khe đậm nhất
        assertEquals(a(3, 0), a(0, 3))                          // đối xứng ngang/dọc
        assertTrue(a(2, 0) in 1 until a(3, 0))                  // vai mềm: mờ hơn khe
        assertTrue(t.all { (it and 0x00FFFFFF) == 0 })          // chỉ đen, không đổi màu
        // từ 6×: khe rộng 2 điểm ảnh
        val t6 = LcdGrid.tile(6, 0.3f)
        assertEquals(t6[0 * 6 + 4] ushr 24, t6[0 * 6 + 5] ushr 24)
    }

    @Test fun shippedGbaStyles() {
        val cfg = ConfigRepository.parse(File("../config/monika-config.json").takeIf { it.exists() }?.readText() ?: File("config/monika-config.json").readText())
        val d = cfg.cores["mgba"]?.display
        assertNotNull(d)
        assertEquals(listOf(240, 160), d!!.native)
        assertEquals("lcd", DisplayStyles.resolve(d, null)?.first)
        // Mọi kiểu đặt cùng tập khóa tùy chọn → đổi qua lại không để sót thiết lập của kiểu trước.
        val keySets = d.styles.values.map { it.options.keys }.toSet()
        assertEquals("các kiểu phải đặt cùng các khóa tùy chọn", 1, keySets.size)
        assertTrue(d.styles.getValue("lcd").grid > 0f && d.styles.getValue("lcd").integer)
        assertEquals(0f, d.styles.getValue("sharp").grid, 0f)
    }
}
