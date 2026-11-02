package vn.aow.monika.runner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.aow.monika.config.CoreOptionText

/**
 * V188: tùy chọn NDS xếp 3 nhóm cố định (Màn hình · Bố cục · Hiệu năng), nhãn tiếng Việt theo khoá;
 * khoá/tùy chọn lạ giữ nguyên tên gốc.
 */
class NdsSettingsTest {
    private fun option(key: String, original: String, value: String = "a") =
        CoreOptions.parse(key, "$original; $value|b", value, CoreOptionText())!!

    @Test fun sectionsAreThreeGroupsInFixedOrderAndUnknownLast() {
        val options = listOf(
            option("melonds_jit_enable", "JIT Enable (Restart)"),
            option("melonds_internal_resolution", "Internal Resolution"),
            option("melonds_screen_layout1", "Screen Layout"),
            option("melonds_pointer_type", "Pointer Type"),
            option("melonds_screen_gap", "Screen Gap"),
        )
        val sections = NdsSettings.sections(options)
        assertEquals(
            listOf("Màn hình", "Bố cục", "Hiệu năng", "Khác"),
            sections.map { it.first?.title ?: "Khác" },
        )
        // Trong nhóm giữ nguyên thứ tự đầu vào.
        assertEquals(listOf("melonds_internal_resolution"), sections[0].second.map { it.key })
        assertEquals(listOf("melonds_screen_layout1", "melonds_screen_gap"), sections[1].second.map { it.key })
        assertEquals(listOf("melonds_jit_enable"), sections[2].second.map { it.key })
        // Tùy chọn chưa xếp nhóm vẫn được giữ, không ẩn.
        assertEquals(listOf("melonds_pointer_type"), sections[3].second.map { it.key })
    }

    @Test fun emptyGroupIsDropped() {
        val sections = NdsSettings.sections(listOf(option("melonds_screen_layout1", "Screen Layout")))
        assertEquals(listOf("Bố cục"), sections.map { it.first?.title })
    }

    @Test fun knownKeysGetVietnameseLabels() {
        val text = CoreOptionText()
        assertEquals("Bố cục 2 màn hình", NdsSettings.label("melonds_screen_layout1", "Screen Layout", text))
        assertEquals("Khoảng cách giữa 2 màn hình", NdsSettings.label("melonds_screen_gap", "Screen Gap", text))
        assertEquals("Bật JIT – tăng tốc (mở lại game)", NdsSettings.label("melonds_jit_enable", "JIT Enable (Restart)", text))
        // Áp dụng cả khi đọc tùy chọn từ lõi (CoreOptions.parse).
        assertEquals("Số bố cục màn hình đang dùng",
            CoreOptions.parse("melonds_number_of_screen_layouts", "Number of Screen Layouts; 1|2|3", "2", text)!!.label)
    }

    @Test fun configTranslationWinsOverBuiltIn() {
        val text = CoreOptionText(labels = mapOf("Screen Layout" to "Bố cục (bản dịch config)"))
        assertEquals("Bố cục (bản dịch config)", NdsSettings.label("melonds_screen_layout1", "Screen Layout", text))
    }

    @Test fun unknownKeyKeepsOriginalName() {
        assertEquals("Mystery Thing", NdsSettings.label("melonds_mystery", "Mystery Thing", CoreOptionText()))
        // Khoá lạ nhưng tên gốc đã biết → vẫn dịch đúng nghĩa.
        assertEquals("Số bố cục màn hình đang dùng",
            NdsSettings.label("melonds_screen_layout_count", "Number of Screen Layouts", CoreOptionText()))
        assertEquals("Weird Option", CoreOptions.parse("gba_weird", "Weird Option; a|b", "a", CoreOptionText())!!.label)
        assertNull(NdsSettings.groupOf(option("melonds_mystery", "Mystery Thing")))
    }

    @Test fun valuesFallBackToVietnameseAndConfigWins() {
        assertEquals("Phần mềm", NdsSettings.valueText(CoreOptionText())["software"])
        assertEquals("Tệp ngoài", NdsSettings.valueText(CoreOptionText())["external"])
        assertEquals("Giá trị config", NdsSettings.valueText(CoreOptionText(values = mapOf("software" to "Giá trị config")))["software"])
        // Tùy chọn NDS hiện giá trị đã dịch; lõi khác giữ nguyên bản dịch giá trị trong config.
        val nds = CoreOptions.parse("melonds_render_mode", "Render Mode; software|opengl", "software", CoreOptionText())!!
        assertEquals("Phần mềm", nds.display())
        val other = CoreOptions.parse("gba_render", "Render; software|opengl", "software", CoreOptionText())!!
        assertEquals("software", other.display())
    }

    @Test fun sameChoiceMatchesConfigAndCoreSpellings() {
        assertTrue(NdsSettings.sameChoice("left-right", "Left/Right"))
        assertTrue(NdsSettings.sameChoice("hybrid-bottom", "Hybrid Bottom"))
        assertTrue(NdsSettings.sameChoice("top-bottom", "Top/Bottom"))
        assertFalse(NdsSettings.sameChoice("top-bottom", "Left/Right"))
    }

    @Test fun detectsNdsByCoreIdOrKeys() {
        assertTrue(NdsSettings.isNds("melondsds", listOf(option("melonds_screen_gap", "Screen Gap"))))
        assertTrue(NdsSettings.isNds("", listOf(option("melonds_screen_gap", "Screen Gap"))))
        assertFalse(NdsSettings.isNds("mgba", listOf(option("gba_color", "Color"))))
    }
}
