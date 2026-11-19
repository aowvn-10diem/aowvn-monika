package vn.aow.monika.diag

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import vn.aow.monika.ui.TestApp
import java.io.File

/**
 * ANR **không có phiên chơi** (10 issue / 48 lượt: #534 #419 #415 #414 #413 #399 #398 #482 #397 #416).
 *
 * Trước bản sửa: ANR xảy ra ở tiến trình chính (chưa mở game) không có `diag/sessions/<pid>.json`,
 * nên `enrichDeathReport` ghi `core="unknown"` và bot không phân loại được.
 * Sau bản sửa: `Diagnostics.startProcess` ghi phiên mức tiến trình ngay khi app khởi động ⇒ báo cáo
 * có `core="app"` (hoặc `midlet`/`game`), `stage` là chặng hiện tại, `component` nêu tiến trình.
 * Phiên chơi vẫn thắng: `Diagnostics.begin` ghi đè ảnh chụp của cùng PID.
 *
 * `ApplicationExitInfo` không có API công khai để dựng → dựng bằng reflection rồi đưa vào
 * `ShadowActivityManager`, cùng cách `MidletDeathSessionTest` đang dùng.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class AppProcessAnrSessionTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val diag get() = File(context.filesDir, "diag")

    @Before fun clean() {
        Diagnostics.end(context)
        diag.deleteRecursively()
        Diagnostics.pending.value = null
        Diagnostics.resetSendDedupe()
    }

    private fun addProcessExit(pid: Int, processName: String, reason: Int, diedAt: Long, summary: ByteArray?) {
        val type = Class.forName("android.app.ApplicationExitInfo")
        val info = type.getDeclaredConstructor().newInstance()
        type.getMethod("setPid", Int::class.javaPrimitiveType).invoke(info, pid)
        type.getMethod("setProcessName", String::class.java).invoke(info, processName)
        type.getMethod("setReason", Int::class.javaPrimitiveType).invoke(info, reason)
        type.getMethod("setTimestamp", Long::class.javaPrimitiveType).invoke(info, diedAt)
        type.getMethod("setImportance", Int::class.javaPrimitiveType)
            .invoke(info, ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND)
        if (summary != null) type.getMethod("setProcessStateSummary", ByteArray::class.java).invoke(info, summary)
        shadowOf(context.getSystemService(ActivityManager::class.java)).addApplicationExitInfo(info)
    }

    /** Token thế hệ đúng như tiến trình đã đặt bằng `setProcessStateSummary` (đọc từ ảnh chụp). */
    private fun summaryOf(pid: Int): ByteArray? {
        val file = File(diag, "sessions/$pid.json")
        if (!file.isFile) return null
        val snapshot = Json { ignoreUnknownKeys = true }
            .decodeFromString(Diagnostics.DeathSnapshot.serializer(), file.readText())
        return Diagnostics.deathProcessSummary(snapshot.generation)
    }

    /** (a) ANR khi chưa mở game: phải có ngữ cảnh tiến trình thay vì `unknown`. */
    @Test fun anrWithoutPlaySessionCarriesAppContextInsteadOfUnknown() {
        val pid = Process.myPid()
        Diagnostics.startProcess(context)
        Diagnostics.processStage(context, "app-foreground")
        val diedAt = System.currentTimeMillis() + 1_000
        addProcessExit(pid, context.packageName, ApplicationExitInfo.REASON_ANR, diedAt, summaryOf(pid))

        val report = Diagnostics.collectProcessDeaths(context).single { it.kind == "anr" }

        assertEquals("app", report.core)
        assertEquals("app", report.session?.core)
        assertNotEquals("unknown", report.core)
        assertEquals("app-foreground", report.stage)
        assertTrue("thiếu thành phần: ${report.component}", report.component.isNotBlank())
        assertFalse("ANR tiến trình chính không phải phiên chơi", report.fromGame)
    }

    /** (b) ANR trong phiên chơi: giữ nguyên lõi phiên chơi, không bị phiên tiến trình ghi đè. */
    @Test fun anrInsidePlaySessionKeepsPlayCore() {
        val pid = Process.myPid()
        Diagnostics.startProcess(context)
        Diagnostics.begin(context, "libretro", "snes9x", "kiểm thử", "game.smc", "SNES")
        val diedAt = System.currentTimeMillis() + 1_000
        addProcessExit(pid, context.packageName, ApplicationExitInfo.REASON_ANR, diedAt, summaryOf(pid))

        val report = Diagnostics.collectProcessDeaths(context).single { it.kind == "anr" }

        assertEquals("snes9x", report.session?.core)
        assertEquals("snes9x", report.core)
        assertNotEquals("app", report.core)
        assertTrue(report.fromGame)
    }

    /** (c) Máy API < 28 (`MidletDiagnostics` bỏ qua): vẫn lấy được tên tiến trình từ `/proc/self/cmdline`. */
    @Test fun processLabelFallsBackToCmdlineOnOldApi() {
        val pkg = context.packageName
        assertEquals("midlet", Diagnostics.processLabel(27, null, "$pkg:midlet", pkg))
        assertEquals("app", Diagnostics.processLabel(27, null, pkg, pkg))
        assertEquals("app", Diagnostics.processLabel(27, null, null, pkg))
        // API ≥ 28: ưu tiên getProcessName(), không đọc /proc.
        assertEquals("midlet", Diagnostics.processLabel(34, "$pkg:midlet", "$pkg:sai", pkg))
        assertEquals("app", Diagnostics.processLabel(34, pkg, null, pkg))
    }
}
