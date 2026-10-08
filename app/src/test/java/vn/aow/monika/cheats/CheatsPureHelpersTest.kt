package vn.aow.monika.cheats

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.ui.TestApp

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class CheatsPureHelpersTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @Test fun parseSortsByIndexAndIgnoresOtherLines() {
        val txt = """
            cheats = 2
            cheat1_desc = "Bất tử"
            cheat1_code = "7E0DBE01"
            cheat1_enable = true
            cheat0_desc = "Vô hạn tiền"
            cheat0_code = "7E0DBF02"
            cheat0_enable = false
        """.trimIndent()
        val list = ChtFormat.parse(txt, "auto")
        assertEquals(listOf("Vô hạn tiền", "Bất tử"), list.map { it.name })
        assertEquals(listOf(false, true), list.map { it.enabled })
        assertTrue(list.all { it.source == "auto" })
    }

    @Test fun parseFillsMissingNameAndDropsEmptyCode() {
        val txt = """
            cheat0_code = "AAAA BBBB"
            cheat1_desc = "Không có mã"
            cheat1_code = "   "
        """.trimIndent()
        assertEquals(listOf(CheatEntry("Mã 1", "AAAA BBBB", false, "user")), ChtFormat.parse(txt))
    }

    @Test fun serializeEscapesQuotesAndRoundTrips() {
        val txt = ChtFormat.serialize(listOf(CheatEntry("Say \"hi\"", "01 02", true)))
        assertTrue(txt, txt.startsWith("cheats = 1\n"))
        assertTrue(txt, txt.contains("cheat0_desc = \"Say 'hi'\""))
        assertEquals(listOf(CheatEntry("Say 'hi'", "01 02", true, "user")), ChtFormat.parse(txt))
    }

    @Test fun serializeEmptyList() {
        assertEquals("cheats = 0\n", ChtFormat.serialize(emptyList()))
    }

    @Test fun candidateLabelComesFromLastParenthesis() {
        assertEquals("GameShark", CheatDb.Candidate("gba", "Pokemon Emerald (USA) (GameShark).cht").label)
        assertEquals("USA", CheatDb.Candidate("gba", "Pokemon Emerald (USA).cht").label)
        assertEquals("Bộ mã", CheatDb.Candidate("gba", "Pokemon Emerald.cht").label)
        assertEquals("Bộ mã", CheatDb.Candidate("gba", "Game (Một nhãn rất dài để vượt ngưỡng hai mươi bốn ký tự).cht").label)
    }

    @Test fun addValidatesAndNormalizesCode() {
        val backend = object : CheatBackend { override fun apply(previous: List<CheatEntry>, list: List<CheatEntry>) {} }
        val c = CheatController(app, "addcase", "gba", listOf("Pokemon Emerald"), backend, CheatDb(app, OkHttpClient()))
        assertEquals("Nhập tên cho mã", c.add("  ", "01 02"))
        assertEquals("Nhập mã cheat", c.add("Tên", " \n "))
        assertNull(c.add("  Bất tử  ", "7E0DBE01 0001\n 7E0DBF02 0002 "))
        assertEquals(listOf(CheatEntry("Bất tử", "7E0DBE01 0001+7E0DBF02 0002", false, "user")), c.items)
    }
}
