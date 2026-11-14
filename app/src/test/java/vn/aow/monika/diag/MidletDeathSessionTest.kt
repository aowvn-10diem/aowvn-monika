package vn.aow.monika.diag

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import vn.aow.monika.ui.TestApp

/**
 * #302: chuỗi đọc phiên của một lần chết do dùng tài nguyên quá mức.
 *
 * Bối cảnh: `#638` (gắn phiên cho tiến trình `:midlet`) **đang mở**, chưa gộp. Test này **tự dựng**
 * ảnh chụp phiên `diag/sessions/<pid>.json` đúng khuôn `Diagnostics.write` sẽ ghi khi có phiên — nó
 * chứng minh **phần đọc phiên** của `collectProcessDeaths` hoạt động cho `native`/`resource`/`initfail`,
 * **không** chứng minh tiến trình `:midlet` đã ghi phiên (việc đó thuộc #638).
 *
 * Chuỗi mô phỏng:
 * 1. Có phiên (`kind="midlet"`, `core="midlet"`) + ảnh chụp `diag/sessions/<pid>.json` (PID khác PID test).
 * 2. Android ghi lần chết `REASON_EXCESSIVE_RESOURCE_USAGE` kèm `processStateSummary` của thế hệ đó.
 * 3. `Diagnostics.collectProcessDeaths` đọc lần chết: trước bản sửa chỉ `anr`/`lowmem` đọc phiên nên
 *    báo cáo `resource` mất lõi; sau bản sửa thì có `core="midlet"`.
 *
 * Giả lập: `ApplicationExitInfo` không có API công khai để dựng, nên dùng `ShadowActivityManager`
 * của Robolectric. Bản 4.16.1 chỉ có `addApplicationExitInfo(processName, pid, reason, importance)`
 * (không đặt được `processStateSummary`), nên test dựng đối tượng bằng reflection rồi đưa vào shadow —
 * **không** sửa mã sản phẩm để test dễ hơn.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class MidletDeathSessionTest {
    private val generation = "00000000-0000-0000-0000-0000000000aa"
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val diag get() = File(context.filesDir, "diag")

    @Before fun clean() {
        Diagnostics.end(context)
        diag.deleteRecursively()
        Diagnostics.pending.value = null
    }

    /** Ảnh chụp phiên chết đúng khuôn `Diagnostics.write` ghi (PID, thế hệ, mốc thời gian). */
    private fun writeDeathSnapshot(pid: Int, kind: String, core: String, system: String, diedAt: Long) {
        val session = Diagnostics.Session(
            pid, diedAt - 60_000, kind, core, "kiểm thử",
            system = system, stage = "playing", stageAt = diedAt - 1_000, lastAlive = diedAt - 10,
        )
        val file = File(diag, "sessions/$pid.json").apply { parentFile!!.mkdirs() }
        file.writeText(Json.encodeToString(Diagnostics.DeathSnapshot.serializer(), Diagnostics.DeathSnapshot(session, generation)))
    }

    /**
     * Dựng `ApplicationExitInfo` thật (reflection vì API này bị ẩn) rồi đưa vào shadow của
     * `ActivityManager`. `summary` = token thế hệ mà tiến trình đã đặt bằng `setProcessStateSummary`.
     */
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

    @Test fun midletResourceDeathKeepsMidletCoreInsteadOfUnknown() {
        val pid = 4242
        val diedAt = System.currentTimeMillis() - 5_000
        writeDeathSnapshot(pid, kind = "midlet", core = "midlet", system = "J2ME", diedAt = diedAt)
        addProcessExit(pid, "${context.packageName}:midlet", ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
            diedAt, Diagnostics.deathProcessSummary(generation))

        val report = Diagnostics.collectProcessDeaths(context).single { it.kind == "resource" }

        assertEquals("midlet", report.session?.core)
        assertEquals("J2ME", report.session?.system)
        assertEquals("midlet", report.core)
        assertEquals("J2ME", report.system)
        assertTrue("thành phần phải nêu tiến trình midlet, hiện: ${report.component}", report.component.contains("midlet"))
        assertEquals("app:midlet", report.component)
        assertTrue(report.fromGame)
    }

    @Test fun nativeExitWithLibretroSnapshotStillReportsItsOwnCore() {
        val pid = 4343
        val diedAt = System.currentTimeMillis() - 5_000
        writeDeathSnapshot(pid, kind = "libretro", core = "melondsds", system = "NDS", diedAt = diedAt)
        addProcessExit(pid, "${context.packageName}:game", ApplicationExitInfo.REASON_CRASH_NATIVE,
            diedAt, Diagnostics.deathProcessSummary(generation))

        val report = Diagnostics.collectProcessDeaths(context).single { it.kind == "native" }

        assertEquals("melondsds", report.session?.core)
        assertEquals("melondsds", report.core)
        assertTrue("thành phần phải nêu lõi, hiện: ${report.component}", report.component.contains("melondsds"))
    }

    @Test fun resourceExitWithoutSnapshotStillFallsBackToProcessComponent() {
        val pid = 4444
        val diedAt = System.currentTimeMillis() - 5_000
        addProcessExit(pid, "${context.packageName}:midlet", ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
            diedAt, Diagnostics.deathProcessSummary(generation))

        val report = Diagnostics.collectProcessDeaths(context).single { it.kind == "resource" }

        assertNull(report.session)
        assertEquals("", report.core)
        assertEquals("app:midlet", report.component)
        assertEquals(false, report.fromGame)
    }
}
