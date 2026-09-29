package vn.aow.monika

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.aow.monika.diag.Diagnostics
import vn.aow.monika.ui.TestApp
import java.io.File

/** Bắt lỗi game: phiên chơi dang dở = game đã chết bất thường → báo cáo; thoát bình thường → không báo. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34])
class DiagnosticsTest {
    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before fun clean() { File(app.filesDir, "diag").deleteRecursively(); Diagnostics.pending.value = null }

    /** Giả lập tiến trình game khác (pid khác) bắt đầu phiên rồi chết. */
    private fun fakeDeadSession(stage: String) {
        val dir = File(app.filesDir, "diag").apply { mkdirs() }
        File(dir, "active-session.json").writeText(
            """{"pid":424242,"startedAt":1000,"kind":"libretro","core":"desmume","coreInfo":"cfg v1 · arm64-v8a","game":"Pokemon.nds","system":"Nintendo DS","stage":"$stage","stageAt":5000,"lastAlive":9000}"""
        )
    }

    @Test fun deadSessionBecomesReport() {
        fakeDeadSession("first-frame")
        val r = Diagnostics.collect(app)
        assertNotNull(r)
        assertEquals("Pokemon.nds", r!!.session!!.game)
        assertTrue(r.title.contains("khi đang chơi"))
        assertTrue(r.toText().contains("desmume"))
        assertEquals(r.id, Diagnostics.pending.value?.id)
        assertEquals(1, Diagnostics.list(app).size)
        // Phiên đã xử lý → lần mở sau không báo lại.
        assertNull(Diagnostics.collect(app))
    }

    @Test fun crashWhileLoadingIsSaidSo() {
        fakeDeadSession("loading-game")
        assertTrue(Diagnostics.collect(app)!!.title.contains("khởi động"))
    }

    @Test fun normalExitMakesNoReport() {
        Diagnostics.begin(app, "libretro", "mgba", "", "a.gba", "GBA")
        Diagnostics.end(app)
        assertNull(Diagnostics.collect(app))
        assertEquals(0, Diagnostics.list(app).size)
    }

    @Test fun ownLiveSessionIsNotACrash() {
        Diagnostics.begin(app, "libretro", "mgba", "", "a.gba", "GBA")
        assertNull(Diagnostics.collect(app)) // cùng pid = đang chạy
    }

    @Test fun javaCrashKeepsSessionContext() {
        Diagnostics.begin(app, "libretro", "mgba", "cfg v1", "a.gba", "GBA")
        Diagnostics.stage(app, "first-frame")
        val r = Diagnostics.recordJavaCrash(app, "main", IllegalStateException("boom"))
        assertEquals("java", r.kind)
        assertTrue(r.fromGame)
        assertTrue(r.detail.contains("boom"))
        assertNull(Diagnostics.collect(app)) // đã ghi rồi → không báo trùng
        assertEquals(1, Diagnostics.list(app).size)
    }

    @Test fun clipboardFallbackWhenNoEndpoint() {
        fakeDeadSession("first-frame")
        val r = Diagnostics.collect(app)!!
        val sent = Diagnostics.send(app, okhttp3.OkHttpClient(), r, "")
        assertEquals(false, sent)
        val clip = app.getSystemService(android.content.ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString()
        assertTrue(clip.contains("Pokemon.nds"))
    }
}
