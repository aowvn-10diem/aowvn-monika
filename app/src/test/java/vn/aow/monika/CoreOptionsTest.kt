package vn.aow.monika

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vn.aow.monika.runner.CoreOptions

class CoreOptionsTest {
    @Test fun parseLibretroDescription() {
        val o = CoreOptions.parse("melonds_screen_layout", "Screen Layout; Top/Bottom|Left/Right|Hybrid", null)!!
        assertEquals("Screen Layout", o.label)
        assertEquals(listOf("Top/Bottom", "Left/Right", "Hybrid"), o.values)
        assertEquals("Top/Bottom", o.value) // chưa có giá trị → lấy giá trị đầu
        assertEquals("Left/Right", o.next())
        assertEquals("Top/Bottom", o.copy(value = "Hybrid").next()) // xoay vòng
    }

    @Test fun keepsCurrentValue() {
        assertEquals("2x", CoreOptions.parse("k", "Res; 1x|2x|3x", "2x")!!.value)
        assertEquals("1x", CoreOptions.parse("k", "Res; 1x|2x|3x", "lạ")!!.value)
    }

    @Test fun rejectsBadDescriptions() {
        assertNull(CoreOptions.parse("k", null, null))
        assertNull(CoreOptions.parse("k", "Không có dấu chấm phẩy", null))
        assertNull(CoreOptions.parse("k", "Chỉ 1 giá trị; on", null))
    }
}

class CoreOptionTextTest {
    @org.junit.Test fun translatedRendererKeepsWireValuesAndUnknownFallback() {
        val cfg = vn.aow.monika.config.ConfigRepository.parse(java.io.File("../config/monika-config.json").readText())
        val o = CoreOptions.parse("renderer", "Threaded software renderer; disabled|enabled", "enabled", cfg.coreOptionText)!!
        assertEquals("Vẽ 3D đa luồng (mượt hơn)", o.label)
        assertEquals("Bật", o.display()); assertEquals("disabled", o.next())
        val unknown = CoreOptions.parse("unknown", "Unknown option; custom|other", "custom", cfg.coreOptionText)!!
        assertEquals("Unknown option", unknown.label); assertEquals("custom", unknown.display())
    }

    @org.junit.Test fun translatesFromBundledConfig() {
        val cfg = vn.aow.monika.config.ConfigRepository.parse(java.io.File("../config/monika-config.json").readText())
        val o = vn.aow.monika.runner.CoreOptions.parse("desmume_screens_layout", "Screen Layout; top/bottom|left/right|hybrid/top", "left/right", cfg.coreOptionText)!!
        org.junit.Assert.assertEquals("Bố cục 2 màn hình", o.label)
        org.junit.Assert.assertEquals("left/right", o.value) // giá trị gửi cho lõi giữ nguyên
        org.junit.Assert.assertEquals("Trái / Phải", o.display())
        org.junit.Assert.assertEquals("Bật", o.copy(value = "enabled").display())
    }
}
