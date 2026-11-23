package vn.aow.monika.runner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ca kiểm cho phần "đọc được" của `core-error`: thông điệp nêu đúng hệ/thiếu gì, và báo cáo mang đủ
 * mã + nghĩa · lõi · hệ suy từ đuôi ROM · bước đang chạy.
 */
class CoreLoadErrorDiagTest {

    private fun facts(
        code: Int,
        systemId: String = "",
        systemName: String = "",
        rom: String = "",
        missingBios: List<String> = emptyList(),
        coreInstalled: Boolean = false,
        extensions: List<String> = emptyList(),
        step: String = "",
        biosMode: String = "",
        unreadable: Boolean = false,
    ) = CoreLoadError.Facts(
        code = code, coreId = "melondsds", step = step, romName = rom, romSizeBytes = 8_000_000,
        systemId = systemId, systemName = systemName, coreExtensions = extensions,
        coreInstalled = coreInstalled, missingBios = missingBios,
        systemFilesUnreadable = unreadable, biosMode = biosMode,
    )

    /** (a) Mã 1 + thiếu BIOS/Firmware ⇒ thông điệp nêu đúng hệ và đúng tệp còn thiếu. */
    @Test fun code1WithMissingBiosNamesSystemAndFiles() {
        val d = CoreLoadError.diagnose(facts(
            code = 1, systemId = "nds", systemName = "Nintendo DS", rom = "game.nds",
            missingBios = listOf("bios7.bin", "bios9.bin", "firmware.bin"),
            extensions = listOf("nds"), step = "core-load-game", biosMode = "external",
        ))
        assertTrue(d.text, d.text.contains("Nintendo DS"))
        assertTrue(d.text, d.text.contains("bios7.bin"))
        assertTrue(d.text, d.text.contains("BIOS/Firmware"))
        assertTrue(d.short, d.short.contains("Nintendo DS"))
        assertTrue("phải mời bấm dùng BIOS có sẵn", d.resetBiosMode)
        assertTrue(d.report, d.report.contains("thiếu BIOS/Firmware: bios7.bin, bios9.bin, firmware.bin"))
    }

    /** (b) Mã 0 khi đã có gói lõi ⇒ "chưa rõ nguyên nhân", và báo cáo có lõi/hệ/bước. */
    @Test fun code0InstalledCoreSaysUnknownAndReportHasFields() {
        val d = CoreLoadError.diagnose(facts(
            code = 0, systemId = "nds", systemName = "Nintendo DS", rom = "game.nds",
            extensions = listOf("nds"), coreInstalled = true, step = "core-load-game",
        ))
        assertTrue(d.text, d.text.contains("chưa rõ nguyên nhân"))
        assertTrue(d.report, d.report.contains("mã 0 (ERROR_LOAD_LIBRARY)"))
        assertTrue(d.report, d.report.contains("lõi: melondsds"))
        assertTrue(d.report, d.report.contains("Nintendo DS (nds)"))
        assertTrue(d.report, d.report.contains("bước: core-load-game"))
    }

    /** Mã 1 không có bằng chứng ⇒ nói "chưa rõ nguyên nhân" và loại bỏ giả thuyết thiếu BIOS. */
    @Test fun code1WithoutEvidenceSaysUnknownAndRulesOutBios() {
        val d = CoreLoadError.diagnose(facts(
            code = 1, systemId = "nds", systemName = "Nintendo DS", rom = "game.nds",
            extensions = listOf("nds"), step = "core-load-game", biosMode = "builtin",
        ))
        assertTrue(d.text, d.text.contains("Chưa rõ nguyên nhân"))
        assertTrue(d.text, d.text.contains("NDS không cần BIOS thật"))
        assertFalse(d.report, d.report.contains("thiếu BIOS/Firmware:"))
        assertTrue(d.report, d.report.contains("nguồn BIOS/Firmware đang chọn: Có sẵn trong lõi"))
    }

    /** Đuôi ROM lõi không nhận ⇒ nêu đuôi lõi nhận và hệ suy từ đuôi. */
    @Test fun code1WrongExtensionNamesCoreLimitAndSystem() {
        val d = CoreLoadError.diagnose(facts(
            code = 1, systemId = "gba", systemName = "Game Boy Advance", rom = "pokemon.gba",
            extensions = listOf("nds"), step = "core-load-game",
        ))
        assertTrue(d.text, d.text.contains(".gba"))
        assertTrue(d.text, d.text.contains("Game Boy Advance"))
        assertTrue(d.text, d.text.contains(".nds"))
        assertTrue(d.report, d.report.contains("đuôi lõi melondsds nhận: .nds"))
    }

    /** Thiếu BIOS chỉ thành bằng chứng khi lõi đang đặt dùng tệp ngoài, và chỉ với lõi melonDS. */
    @Test fun missingBiosOnlyWhenExternalMode() {
        val present = setOf("bios7.bin")
        assertEquals(emptyList<String>(), CoreLoadError.missingExternalBios("melondsds", "builtin", "nds", present))
        assertEquals(
            listOf("bios9.bin", "firmware.bin"),
            CoreLoadError.missingExternalBios("melondsds", "external", "nds", present),
        )
        assertEquals(emptyList<String>(), CoreLoadError.missingExternalBios("mgba", "external", "gba", present))
    }

    /** Mọi mã đều để lại báo cáo có mã + nghĩa, lõi, hệ và bước — không còn "core-error trơ". */
    @Test fun everyCodeReportCarriesCoreSystemAndStep() {
        listOf(0, 1, 2, 3, 4, -1, 99).forEach { code ->
            val d = CoreLoadError.diagnose(facts(
                code = code, systemId = "nds", systemName = "Nintendo DS", rom = "game.nds",
                extensions = listOf("nds"), step = "core-load-game",
            ))
            assertTrue("mã $code", d.report.contains("lõi: melondsds"))
            assertTrue("mã $code", d.report.contains("hệ: Nintendo DS (nds)"))
            assertTrue("mã $code", d.report.contains("bước: core-load-game"))
            assertTrue("mã $code", d.report.contains("nguyên nhân đọc được:"))
            assertTrue("mã $code", d.short.isNotBlank() && !d.short.contains('\n'))
            assertTrue("mã $code", d.summary.contains("melondsds") && d.summary.length <= 200)
        }
    }

    /**
     * Không đọc được thư mục hệ thống ⇒ KHÔNG kết luận thiếu BIOS (tránh báo oan), nhưng báo cáo phải
     * nói rõ là không đọc được — đây là lý do `// diag: bỏ qua` ở `coreErrorFacts` không làm mất thông tin.
     */
    @Test fun unreadableSystemDirDoesNotClaimMissingBios() {
        val d = CoreLoadError.diagnose(facts(
            code = 1, systemId = "nds", systemName = "Nintendo DS", rom = "game.nds",
            extensions = listOf("nds"), step = "core-load-game", biosMode = "external", unreadable = true,
        ))
        assertTrue(d.text, d.text.contains("Chưa rõ nguyên nhân"))
        assertFalse(d.text, d.text.contains("thiếu: "))
        assertTrue(d.report, d.report.contains("không đọc được thư mục hệ thống"))
        assertFalse(d.report, d.report.contains("thiếu BIOS/Firmware:"))
        assertFalse("không mời bấm nút BIOS khi chưa kiểm được", d.resetBiosMode)
    }

    /** Hệ máy lạ (đuôi không có trong config) vẫn ghi rõ trong báo cáo là chưa xác định. */
    @Test fun unknownExtensionIsReportedAsUnknownSystem() {
        val d = CoreLoadError.diagnose(facts(code = 1, rom = "game.xyz", extensions = listOf("xyz"), step = "core-load-game"))
        assertTrue(d.report, d.report.contains("chưa xác định"))
        assertTrue(d.report, d.report.contains(".xyz"))
    }

    /**
     * `melondsds` mã 1 tới được lõi nghĩa là tệp đã qua kiểm header trước khi nạp (`NdsContent`) — thông điệp
     * phải nói rõ điều đó (issue #567) để người chơi không nhập lại game vô ích, và không khuyên đổi vì "tệp hỏng".
     */
    @Test fun melondsDsCode1SaysRomHeaderWasChecked() {
        val d = CoreLoadError.diagnose(facts(
            code = 1, systemId = "nds", systemName = "Nintendo DS", rom = "game.nds",
            extensions = listOf("nds"), step = "core-load-game", biosMode = "builtin",
        ))
        assertTrue(d.text, d.text.contains("Chưa rõ nguyên nhân"))
        assertTrue(d.text, d.text.contains("app đã kiểm tệp trước khi nạp"))
        assertTrue(d.cause, d.cause.contains("qua kiểm header"))
    }

    /** Lõi khác không có bước kiểm header NDS ⇒ giữ câu "tệp có thể hỏng" như trước. */
    @Test fun otherCoreCode1KeepsFileCouldBeBroken() {
        val d = CoreLoadError.diagnose(
            CoreLoadError.Facts(code = 1, coreId = "mgba", romName = "game.gba", systemId = "gba"),
        )
        assertTrue(d.text, d.text.contains("tệp game có thể hỏng"))
        assertFalse(d.text, d.text.contains("app đã kiểm tệp trước khi nạp"))
    }
}
