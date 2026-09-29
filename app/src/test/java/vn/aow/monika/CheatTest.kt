package vn.aow.monika

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.cheats.CheatBackend
import vn.aow.monika.cheats.CheatController
import vn.aow.monika.cheats.CheatDb
import vn.aow.monika.cheats.CheatEntry
import vn.aow.monika.cheats.ChtFormat
import vn.aow.monika.ui.TestApp

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class CheatTest {
    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test fun chtRoundTrip() {
        val txt = """
            cheats = 2

            cheat0_desc = "Master Code"
            cheat0_code = "D8BAE4D9+4864DCE5"
            cheat0_enable = false

            cheat1_desc = "Vô hạn HP"
            cheat1_code = "82003884+0063"
            cheat1_enable = true
        """.trimIndent()
        val list = ChtFormat.parse(txt, "auto")
        assertEquals(2, list.size)
        assertEquals("Vô hạn HP", list[1].name)
        assertTrue(list[1].enabled && !list[0].enabled)
        assertEquals(list, ChtFormat.parse(ChtFormat.serialize(list), "auto"))
    }

    @Test fun matchesGameByName() {
        val db = CheatDb(app, OkHttpClient())
        val gba = db.match("gba", listOf("Pokemon Emerald"))
        assertTrue(gba.toString(), gba.isNotEmpty() && gba.first().file.contains("Emerald"))
        val snes = db.match("snes", listOf("Super Mario World"))
        assertTrue(snes.toString(), snes.isNotEmpty() && snes.first().file.contains("Super Mario World"))
        assertTrue(db.match("gba", listOf("Tên game không tồn tại xyz")).isEmpty())
        assertTrue(!db.supports("java") && db.supports("nds"))
    }

    @Test fun controllerTogglesAndPersists() = runBlocking {
        val calls = mutableListOf<Pair<List<CheatEntry>, List<CheatEntry>>>()
        val backend = object : CheatBackend { override fun apply(previous: List<CheatEntry>, list: List<CheatEntry>) { calls += previous to list } }
        val c = CheatController(app, "test-key", "java", listOf("x"), backend)
        c.prepare()
        assertEquals(null, c.add("Vô hạn HP", "82003884\n0063"))
        assertEquals("82003884+0063", c.items[0].code)
        c.toggle(0)
        assertTrue(c.items[0].enabled)
        assertTrue(calls.last().second[0].enabled)
        val again = CheatController(app, "test-key", "java", listOf("x"), backend)
        again.prepare()
        assertEquals(1, again.items.size)
        assertTrue(again.items[0].enabled)
        again.remove(0)
        assertTrue(again.items.isEmpty())
    }
}
