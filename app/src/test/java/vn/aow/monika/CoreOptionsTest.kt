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
