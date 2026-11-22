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
import java.util.UUID

/**
 * L23: Android **cấp lại PID** cho tiến trình mới trước khi lượt quét `ApplicationExitInfo` chạy.
 *
 * Trước bản sửa: `startProcess` ghi `diag/sessions/<pid>.json` **đè** ảnh chụp của tiến trình đã chết
 * ⇒ ANR của nó mất bằng chứng ⇒ báo cáo `core="unknown"`. `startProcess` cũng không dọn thư mục phiên,
 * nên máy chỉ mở app tích ~0,3 KB mỗi lần khởi động, không giới hạn.
 *
 * Sau bản sửa: bản cũ được giữ thành `<pid>-<startedAt>.json`, lượt quét duyệt mọi bản của PID rồi khớp
 * token thế hệ; `startProcess` dọn còn 30 ảnh chụp mới nhất.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class DeathSessionPidReuseTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val diag get() = File(context.filesDir, "diag")
    private val sessions get() = File(diag, "sessions")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Before fun clean() {
        Diagnostics.end(context)
        diag.deleteRecursively()
        Diagnostics.pending.value = null
        Diagnostics.resetSendDedupe()
    }

    /** Ghi tệp ảnh chụp như một tiến trình đã chết để lại (không đi qua `startProcess` của tiến trình này). */
    private fun writeSnapshot(name: String, generation: String, startedAt: Long, core: String) {
        val session = Diagnostics.Session(
            pid = Process.myPid(), startedAt = startedAt, kind = Diagnostics.PROCESS_KIND,
            core = core, coreInfo = "", game = "", system = "", stage = "app-start",
            stageAt = startedAt, lastAlive = startedAt,
        )
        val file = File(sessions, name).apply { parentFile?.mkdirs() }
        file.writeText(json.encodeToString(Diagnostics.DeathSnapshot.serializer(), Diagnostics.DeathSnapshot(session, generation)))
    }

    private fun generationOf(file: File): String? = runCatching {
        json.decodeFromString(Diagnostics.DeathSnapshot.serializer(), file.readText()).generation
    }.getOrNull()

    private fun addProcessExit(pid: Int, reason: Int, diedAt: Long, summary: ByteArray?) {
        val type = Class.forName("android.app.ApplicationExitInfo")
        val info = type.getDeclaredConstructor().newInstance()
        type.getMethod("setPid", Int::class.javaPrimitiveType).invoke(info, pid)
        type.getMethod("setProcessName", String::class.java).invoke(info, context.packageName)
        type.getMethod("setReason", Int::class.javaPrimitiveType).invoke(info, reason)
        type.getMethod("setTimestamp", Long::class.javaPrimitiveType).invoke(info, diedAt)
        type.getMethod("setImportance", Int::class.javaPrimitiveType)
            .invoke(info, ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND)
        if (summary != null) type.getMethod("setProcessStateSummary", ByteArray::class.java).invoke(info, summary)
        shadowOf(context.getSystemService(ActivityManager::class.java)).addApplicationExitInfo(info)
    }

    /**
     * (a) PID cấp lại, ảnh chụp tiến trình cũ **không** được mất: ANR của tiến trình cũ vẫn ra lõi
     * `midlet` thay vì `unknown`.
     */
    @Test fun reusedPidKeepsOldSnapshotAndAnrStillReportsItsCore() {
        val pid = Process.myPid()
        val startedOld = System.currentTimeMillis() - 60_000
        val generationOld = UUID.randomUUID().toString()
        writeSnapshot("$pid.json", generationOld, startedOld, "midlet")

        // Android cấp lại đúng PID này cho tiến trình mới (app vừa mở lại).
        Diagnostics.startProcess(context)

        val archived = File(sessions, "$pid-$startedOld.json")
        assertTrue("ảnh chụp tiến trình cũ phải còn: ${sessions.list()?.toList()}", archived.isFile)
        assertNotEquals(
            "tệp sống phải là ảnh của tiến trình mới",
            generationOld, generationOf(File(sessions, "$pid.json")),
        )

        val diedAt = startedOld + 30_000 // chết trước khi tiến trình mới khởi động
        addProcessExit(pid, ApplicationExitInfo.REASON_ANR, diedAt, Diagnostics.deathProcessSummary(generationOld))

        val report = Diagnostics.collectProcessDeaths(context).single { it.kind == "anr" }

        assertEquals("midlet", report.core)
        assertEquals("midlet", report.session?.core)
        assertNotEquals("unknown", report.core)
    }

    /**
     * (b) Tương thích ngược: tệp `<pid>.json` kiểu cũ vẫn đọc được — kể cả sau khi PID bị cấp lại
     * (trước bản sửa: bị `startProcess` ghi đè ⇒ lượt quét đọc ra `null`).
     */
    @Test fun legacyBareSnapshotStaysReadableAfterPidReuse() {
        val pid = Process.myPid()
        val startedOld = System.currentTimeMillis() - 120_000
        val generationOld = UUID.randomUUID().toString()
        writeSnapshot("$pid.json", generationOld, startedOld, "game")
        val summary = Diagnostics.deathProcessSummary(generationOld)
        val diedAt = startedOld + 5_000

        // Chống hồi quy: cách đọc kiểu cũ vẫn đúng.
        assertEquals("game", Diagnostics.readDeathSession(context, pid, diedAt, summary)?.core)

        // PID cấp lại: bản kiểu cũ phải còn đọc được, không bị ảnh của tiến trình mới che mất.
        Diagnostics.startProcess(context)
        assertEquals("game", Diagnostics.readDeathSession(context, pid, diedAt, summary)?.core)
    }

    /** (c) Dọn có giới hạn: 35 ảnh chụp cũ + 1 ảnh mới ⇒ còn đúng 30, giữ ảnh mới nhất. */
    @Test fun startProcessKeepsOnlyThirtyNewestSnapshots() {
        val base = System.currentTimeMillis() - 1_000_000
        for (i in 0 until 35) {
            val file = File(sessions, "${10_000 + i}.json").apply { parentFile?.mkdirs() }
            file.writeText("{}") // chỉ cần tệp để đo việc dọn, không cần đọc được
            file.setLastModified(base + i * 1_000L)
        }

        Diagnostics.startProcess(context)

        val left = sessions.listFiles()!!.filter { it.isFile && it.extension == "json" }
        assertEquals("chỉ giữ 30 ảnh chụp mới nhất", 30, left.size)
        assertTrue("ảnh chụp mới nhất trong 35 tệp cũ phải được giữ", File(sessions, "10034.json").isFile)
        assertFalse("ảnh chụp cũ nhất phải bị xoá", File(sessions, "10000.json").isFile)
        assertTrue("ảnh chụp của tiến trình hiện tại phải được giữ", File(sessions, "${Process.myPid()}.json").isFile)
    }
}
