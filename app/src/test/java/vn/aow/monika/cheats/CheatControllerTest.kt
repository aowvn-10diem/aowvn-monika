package vn.aow.monika.cheats

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.ui.TestApp
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class CheatControllerTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val backend = RecordingBackend()

    @Before fun clearSavedCheats() {
        File(app.filesDir, "cheats").deleteRecursively()
    }

    private fun controller(gameKey: String, systemId: String = "gba", recorder: RecordingBackend = backend) =
        CheatController(app, gameKey, systemId, listOf("Pokemon Emerald"), recorder, CheatDb(app, OkHttpClient()))

    @Test fun toggleFlipsEnabledAndSendsPreviousAndNewListToBackend() {
        val c = controller("toggle")
        assertNull(c.add("Bất tử", "7E0DBE01 0001"))
        c.toggle(0)
        val off = CheatEntry("Bất tử", "7E0DBE01 0001", false, "user")
        assertEquals(listOf(off.copy(enabled = true)), c.items)
        assertEquals(
            listOf(
                emptyList<CheatEntry>() to listOf(off),
                listOf(off) to listOf(off.copy(enabled = true)),
            ),
            backend.calls,
        )
    }

    @Test fun removeDropsOnlyThatCheatAndSendsPreviousListSoItIsTurnedOff() {
        val c = controller("remove")
        c.add("A", "01 02")
        c.add("B", "03 04")
        c.remove(0)
        val a = CheatEntry("A", "01 02", false, "user")
        val b = CheatEntry("B", "03 04", false, "user")
        assertEquals(listOf(b), c.items)
        assertEquals(listOf(a, b) to listOf(b), backend.calls.last())
    }

    @Test fun applyAllSendsWholeListAndBecomesBaselineForLaterToggles() {
        val c = controller("applyall")
        c.add("A", "01 02")
        c.applyAll()
        c.toggle(0)
        val a = CheatEntry("A", "01 02", false, "user")
        assertEquals(
            listOf(
                emptyList<CheatEntry>() to listOf(a),
                emptyList<CheatEntry>() to listOf(a),
                listOf(a) to listOf(a.copy(enabled = true)),
            ),
            backend.calls,
        )
    }

    @Test fun savedCheatsAndTheirOnOffStateComeBackAfterRestart() {
        val first = controller("restart", systemId = "khong-ho-tro")
        first.add("Bất tử", "7E0DBE01 0001")
        first.toggle(0)

        val restartedBackend = RecordingBackend()
        val restarted = controller("restart", systemId = "khong-ho-tro", recorder = restartedBackend)
        val added = runBlocking { restarted.prepare() }
        restarted.applyAll()

        val saved = CheatEntry("Bất tử", "7E0DBE01 0001", true, "user")
        assertFalse(added)
        assertEquals(listOf(saved), restarted.items)
        assertEquals(listOf(emptyList<CheatEntry>() to listOf(saved)), restartedBackend.calls)
    }

    @Test fun importFileAddsCheatsFromChtFileAndReportsCount() {
        val cht = File(app.cacheDir, "import-test.cht").apply {
            writeText("cheats = 2\ncheat0_desc = \"Vô hạn tiền\"\ncheat0_code = \"7E0DBF02\"\ncheat1_desc = \"Bất tử\"\ncheat1_code = \"7E0DBE01\"\n")
        }
        val c = controller("import")
        c.importFile(Uri.fromFile(cht))
        assertEquals("Đã nhập 2 mã", c.message)
        assertEquals(listOf("Vô hạn tiền", "Bất tử"), c.items.map { it.name })
        assertEquals(1, backend.calls.size)
    }

    @Test fun importFileWithUnreadableUriShowsMessageAndChangesNothing() {
        val c = controller("import-bad")
        c.importFile(Uri.parse("content://vn.aow.monika.missing/none.cht"))
        assertEquals("Không đọc được mã trong tệp (cần định dạng .cht)", c.message)
        assertTrue(c.items.isEmpty())
        assertTrue(backend.calls.isEmpty())
    }

    @Test fun showOpensMenuAndCloseResetsAddingMode() {
        val c = controller("menu")
        c.message = "cũ"
        c.show()
        assertTrue(c.open)
        assertNull(c.message)
        c.adding = true
        c.close()
        assertFalse(c.open)
        assertFalse(c.adding)
    }

    private class RecordingBackend : CheatBackend {
        val calls = mutableListOf<Pair<List<CheatEntry>, List<CheatEntry>>>()
        override fun apply(previous: List<CheatEntry>, list: List<CheatEntry>) {
            calls += previous to list
        }
    }
}
