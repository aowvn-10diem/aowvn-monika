package vn.aow.monika.diag

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
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

/** #302: game Java (`:midlet`) có phiên chẩn đoán, và chỉ dọn khi chứng minh được người dùng tự thoát. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class MidletDiagnosticsTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val diag get() = File(app.filesDir, "diag")
    private val session get() = File(diag, "active-session.json")
    private val deadPid = 2_000_000 // pid không phải tiến trình của bài test

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

    @Test fun installOnlyInMidletProcessOnApi28Plus() {
        assertTrue(MidletDiagnostics.shouldInstall(34, "com.aow.monika:midlet"))
        assertFalse(MidletDiagnostics.shouldInstall(34, "com.aow.monika"))
        assertFalse(MidletDiagnostics.shouldInstall(34, "com.aow.monika:game"))
        // getProcessName() chưa có trước API 28 → không đăng ký, chạy như cũ.
        assertFalse(MidletDiagnostics.shouldInstall(27, "com.aow.monika:midlet"))
        assertFalse(MidletDiagnostics.shouldInstall(26, null))
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
        // Robolectric không chạy tiến trình ":midlet" → không đăng ký callback (và không dọn phiên ở đây).
        MidletDiagnostics.install(app)
        MidletDiagnostics.install(app) // gọi lặp không ném, không nhân đôi callback
        assertFalse(MidletDiagnostics.isRegistered())
        val name = runCatching { Application.getProcessName() }.getOrNull()
        assertFalse(MidletDiagnostics.isMidletProcess(name))
    }

    @Test fun shouldClearOnlyForProvenNormalExit() {
        // API < 30: không có dữ liệu để phân biệt → giữ phiên.
        assertFalse(MidletDiagnostics.shouldClear("midlet", false, ApplicationExitInfo.REASON_EXIT_SELF, 29))
        // Đúng loại, pid đã chết, API 30+, lý do là người dùng tự thoát → được xoá.
        assertTrue(MidletDiagnostics.shouldClear("midlet", false, ApplicationExitInfo.REASON_USER_REQUESTED, 34))
        assertTrue(MidletDiagnostics.shouldClear("midlet", false, ApplicationExitInfo.REASON_EXIT_SELF, 30))
        // Còn sống hoặc không phải phiên game Java → không đụng.
        assertFalse(MidletDiagnostics.shouldClear("midlet", true, ApplicationExitInfo.REASON_EXIT_SELF, 34))
        assertFalse(MidletDiagnostics.shouldClear("libretro", false, ApplicationExitInfo.REASON_EXIT_SELF, 34))
        // Mọi lý do chết thật / không rõ → giữ để báo (thà báo giả còn hơn che crash thật).
        for (reason in listOf(
            ApplicationExitInfo.REASON_ANR,
            ApplicationExitInfo.REASON_CRASH,
            ApplicationExitInfo.REASON_CRASH_NATIVE,
            ApplicationExitInfo.REASON_LOW_MEMORY,
            ApplicationExitInfo.REASON_OTHER,
            ApplicationExitInfo.REASON_SIGNALED,
            ApplicationExitInfo.REASON_UNKNOWN,
            null,
        )) assertFalse("reason=$reason", MidletDiagnostics.shouldClear("midlet", false, reason, 34))
    }

    @Test fun cleanupKeepsLiveSession() {
        writeSession(pid = android.os.Process.myPid(), kind = "midlet")
        MidletDiagnostics.cleanupAfterCollect(app, 34, exitReasonOf = { ApplicationExitInfo.REASON_EXIT_SELF })
        assertTrue("tiến trình còn sống thì giữ phiên", session.exists())
    }

    @Test fun cleanupKeepsSessionOfOtherCores() {
        writeSession(pid = deadPid, kind = "libretro")
        MidletDiagnostics.cleanupAfterCollect(app, 34, exitReasonOf = { ApplicationExitInfo.REASON_USER_REQUESTED })
        assertTrue("phiên của lõi khác do Diagnostics.collect xử lý", session.exists())
    }

    @Test fun cleanupKeepsSessionWhenExitReasonIsUnproven() {
        writeSession(pid = deadPid)
        MidletDiagnostics.cleanupAfterCollect(app, 34, exitReasonOf = { null }) // không tìm thấy bản ghi
        assertTrue(session.exists())
        MidletDiagnostics.cleanupAfterCollect(app, 34, exitReasonOf = { ApplicationExitInfo.REASON_ANR })
        assertTrue(session.exists())
        MidletDiagnostics.cleanupAfterCollect(app, 34, exitReasonOf = { ApplicationExitInfo.REASON_CRASH_NATIVE })
        assertTrue(session.exists())
        MidletDiagnostics.cleanupAfterCollect(app, 34, exitReasonOf = { ApplicationExitInfo.REASON_OTHER })
        assertTrue(session.exists())
    }

    @Test fun cleanupDeletesOnlyProvenNormalExit() {
        writeSession(pid = deadPid)
        val snapshot = File(diag, "sessions/$deadPid.json").apply { parentFile!!.mkdirs(); writeText("{}") }
        MidletDiagnostics.cleanupAfterCollect(app, 34, exitReasonOf = { ApplicationExitInfo.REASON_USER_REQUESTED })
        assertFalse(session.exists())
        assertFalse("ảnh chụp của pid đã thoát bình thường không còn ý nghĩa", snapshot.exists())
    }

    @Test fun cleanupDoesNothingBelowApi30() {
        writeSession(pid = deadPid)
        MidletDiagnostics.cleanupAfterCollect(app, 29, exitReasonOf = { ApplicationExitInfo.REASON_USER_REQUESTED })
        assertTrue("API < 30 không xoá gì (đánh đổi có chủ ý)", session.exists())
    }

    @Test fun unreadableProcessListMeansAliveSoSessionIsKept() {
        // Máy không trả danh sách tiến trình (null) → coi như CÒN SỐNG, không xoá phiên.
        assertTrue(MidletDiagnostics.pidAlive(null, deadPid))
        assertFalse(MidletDiagnostics.shouldClear("midlet", MidletDiagnostics.pidAlive(null, deadPid), ApplicationExitInfo.REASON_EXIT_SELF, 34))

        writeSession(pid = deadPid)
        MidletDiagnostics.cleanupAfterCollect(
            app, 34,
            exitReasonOf = { ApplicationExitInfo.REASON_USER_REQUESTED },
            aliveOf = { pid -> MidletDiagnostics.pidAlive(null, pid) },
        )
        assertTrue("danh sách null → giữ phiên", session.exists())

        // Đọc được danh sách: có pid → sống, không có → chết (nhánh cũ vẫn đúng).
        assertTrue(MidletDiagnostics.pidAlive(listOf(ActivityManager.RunningAppProcessInfo().apply { pid = deadPid }), deadPid))
        assertFalse(MidletDiagnostics.pidAlive(emptyList(), deadPid))
    }

    private fun writeSession(pid: Int, kind: String = "midlet") {
        diag.mkdirs()
        session.writeText(
            """{"pid":$pid,"startedAt":1,"kind":"$kind","core":"midlet",""" +
                """"game":"Bounce","system":"J2ME","stage":"playing","stageAt":2,"lastAlive":3}"""
        )
    }
}
