package vn.aow.monika.diag

import android.app.Activity
import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.ui.TestApp
import java.io.File

/** #302: game Java (`:midlet`) có phiên chẩn đoán, và thoát bình thường không bị báo là chết. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class MidletDiagnosticsTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val diag get() = File(app.filesDir, "diag")
    private val session get() = File(diag, "active-session.json")

    @Before fun clean() { diag.deleteRecursively() }

    @Test fun onlyMicroActivityRunsInMidletProcess() {
        assertTrue(MidletDiagnostics.isMidletActivity("javax.microedition.shell.MicroActivity"))
        // Màn chuẩn bị không có android:process → chạy ở tiến trình chính, không được gắn phiên ":midlet".
        assertFalse(MidletDiagnostics.isMidletActivity("ru.woesss.j2me.installer.MonikaLaunchActivity"))
        assertFalse(MidletDiagnostics.isMidletActivity("ru.playsoftware.j2meloader.config.ConfigActivity"))
        assertFalse(MidletDiagnostics.isMidletActivity("vn.aow.monika.runner.RetroActivity"))
        assertFalse(MidletDiagnostics.isMidletActivity("vn.aow.monika.ui.MainActivity"))
        assertFalse(MidletDiagnostics.isMidletActivity("javax.microedition.shell.MicroActivity2"))
    }

    @Test fun midletProcessDetectionMatchesMonikaAppRule() {
        assertTrue(MidletDiagnostics.isMidletProcess("com.aow.monika:midlet"))
        assertFalse(MidletDiagnostics.isMidletProcess("com.aow.monika"))
        assertFalse(MidletDiagnostics.isMidletProcess("com.aow.monika:game"))
        assertFalse(MidletDiagnostics.isMidletProcess(null))
    }

    @Test fun sessionCarriesMidletCoreAndGameNameThenEnds() {
        val controller = Robolectric.buildActivity(Activity::class.java,
            Intent().putExtra("midletName", "Bounce")).setup()
        val activity = controller.get()

        MidletDiagnostics.begin(activity)
        val text = session.readText()
        assertTrue(text, text.contains("\"kind\":\"midlet\""))
        assertTrue(text, text.contains("\"core\":\"midlet\""))
        assertTrue(text, text.contains("\"game\":\"Bounce\""))
        assertTrue(text, text.contains("\"system\":\"J2ME\""))
        assertFalse("phiên không được mang đường dẫn máy", text.contains(app.filesDir.absolutePath))

        activity.finish()
        MidletDiagnostics.end(activity)
        assertFalse(session.exists())
    }

    @Test fun installIsNoOpOutsideMidletProcessAndNeverThrows() {
        // Robolectric không chạy tiến trình ":midlet" → không đăng ký callback, chỉ dọn phiên cũ.
        MidletDiagnostics.install(app)
        MidletDiagnostics.install(app) // gọi lặp không ném, không nhân đôi callback
        val name = runCatching { Application.getProcessName() }.getOrNull()
        assertFalse(MidletDiagnostics.isMidletProcess(name))
    }

    @Test fun deadMidletSessionIsClearedButLiveOrForeignKeyIsKept() {
        // pid 2_000_000 không phải tiến trình của bài test → phiên của lần chơi đã thoát.
        writeSession(pid = 2_000_000, kind = "midlet")
        MidletDiagnostics.install(app)
        assertFalse("thoát bình thường không được để lại phiên", session.exists())

        writeSession(pid = 2_000_000, kind = "libretro")
        MidletDiagnostics.install(app)
        assertTrue("phiên của lõi khác do Diagnostics.collect xử lý", session.exists())

        writeSession(pid = android.os.Process.myPid(), kind = "midlet")
        MidletDiagnostics.install(app)
        assertTrue("tiến trình còn sống thì giữ phiên", session.exists())
    }

    private fun writeSession(pid: Int, kind: String) {
        diag.mkdirs()
        session.writeText(
            """{"pid":$pid,"startedAt":1,"kind":"$kind","core":"midlet",""" +
                """"game":"Bounce","system":"J2ME","stage":"playing","stageAt":2,"lastAlive":3}"""
        )
    }
}
