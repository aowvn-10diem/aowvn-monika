package vn.aow.monika

import org.junit.Assert.*
import org.junit.Test
import vn.aow.monika.config.ConfigRepository
import vn.aow.monika.runner.DsScreen
import java.io.File

/** V78c: mục "Màn hình" (NDS) — config khớp giá trị thật của lõi melonDS DS và logic đọc/ghi lựa chọn. */
class DsScreenTest {
    private val cfg = ConfigRepository.parse(File("../config/monika-config.json").readText())
    private val core = cfg.cores.getValue("melondsds")
    private val screen = core.screen!!

    // Giá trị lõi khai (đọc từ bảng tùy chọn trong lõi melondsds arm64, 10/10/2026): khóa → giá trị hợp lệ.
    private val coreLayouts = setOf("top-bottom", "bottom-top", "left-right", "right-left", "top", "bottom", "largescreen-top",
        "largescreen-bottom", "flipped-largescreen-top", "flipped-largescreen-bottom", "hybrid-top", "hybrid-bottom",
        "flipped-hybrid-top", "flipped-hybrid-bottom", "rotate-left", "rotate-right", "rotate-180")
    private val coreRatios = setOf("2", "3")
    private val coreGapRange = 0..126

    @Test fun configValuesExistInTheCore() {
        assertEquals("melonds_screen_layout1", screen.layoutKey)
        assertTrue(screen.layouts.all { it.value in coreLayouts })
        assertTrue(screen.ratios.all { it.value in coreRatios })
        assertTrue(screen.gapMax in coreGapRange)
        assertEquals(screen.layouts.size, screen.layouts.map { it.id }.toSet().size)
        // Mặc định trong options khớp mặc định của mục "Màn hình".
        assertEquals(screen.layouts.single { it.id == screen.defaultLayout }.value, core.options[screen.layoutKey])
        assertEquals(screen.ratios.single { it.id == screen.defaultRatio }.value, core.options[screen.ratioKey])
        assertEquals(screen.defaultGap.toString(), core.options[screen.gapKey])
        // Chỉ dùng một bố cục: không luân phiên sang bố cục #2 của lõi.
        assertEquals("1", core.options["melonds_number_of_screen_layouts"])
        // Ba bố cục sếp yêu cầu.
        assertEquals(listOf("Trên – dưới", "Cạnh nhau", "Một màn lớn"), screen.layouts.map { it.label })
    }

    @Test fun defaultsComeFromConfig() {
        val s = DsScreen.current(screen, core.options)
        assertEquals("stacked", s.layout.id)
        assertEquals("2", s.ratio?.id)
        assertEquals(0, s.gap)
        assertFalse(DsScreen.showsRatio(screen, s))
        assertTrue(DsScreen.showsGap(screen))
    }

    @Test fun savedChoiceWinsAndRatioOnlyForFocusLayout() {
        val side = DsScreen.current(screen, core.options + ("melonds_screen_layout1" to "left-right"))
        assertEquals("side", side.layout.id)
        assertFalse(DsScreen.showsRatio(screen, side))
        val focus = DsScreen.current(screen, core.options + mapOf("melonds_screen_layout1" to "hybrid-bottom", "melonds_hybrid_ratio" to "3", "melonds_screen_gap" to "12"))
        assertEquals("focus", focus.layout.id)
        assertEquals("3", focus.ratio?.id)
        assertEquals(12, focus.gap)
        assertTrue(DsScreen.showsRatio(screen, focus))
    }

    @Test fun unknownOrOutOfRangeValuesFallBack() {
        val s = DsScreen.current(screen, mapOf("melonds_screen_layout1" to "no-such", "melonds_hybrid_ratio" to "9", "melonds_screen_gap" to "9999"))
        assertEquals("stacked", s.layout.id)
        assertEquals("2", s.ratio?.id)
        assertEquals(screen.gapMax, s.gap)
        assertEquals(0, DsScreen.current(screen, mapOf("melonds_screen_gap" to "abc")).gap)
    }

    @Test fun writesTheRealLibretroKeys() {
        val side = screen.layouts.single { it.id == "side" }
        assertEquals("melonds_screen_layout1" to "left-right", DsScreen.valueOf(screen.layoutKey, side))
        assertEquals("melonds_screen_gap" to "32", DsScreen.gapValue(screen, 500))
        assertEquals("melonds_screen_gap" to "0", DsScreen.gapValue(screen, -5))
        assertEquals(listOf("melonds_screen_layout1", "melonds_hybrid_ratio", "melonds_screen_gap"), DsScreen.keys(screen))
    }

    @Test fun hugeGapMaxFromConfigIsCapped() {
        val wild = screen.copy(gapMax = 1_000_000)
        assertEquals(DsScreen.GAP_LIMIT, DsScreen.gapMax(wild))
        assertEquals(DsScreen.GAP_LIMIT, DsScreen.current(wild, mapOf("melonds_screen_gap" to "999999")).gap)
        assertFalse(DsScreen.showsGap(screen.copy(gapMax = 0)))
        assertFalse(DsScreen.showsGap(screen.copy(gapKey = "")))
    }

    @Test fun otherCoresHaveNoScreenSection() {
        assertTrue(cfg.cores.filterKeys { it != "melondsds" }.values.all { it.screen == null })
    }
}
