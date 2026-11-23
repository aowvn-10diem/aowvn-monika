package vn.aow.monika.diag

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
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
        // `autoSend` ghi "đã gửi" vào prefs + bộ nhớ; dọn để ca sau không bị chặn trùng.
        context.getSharedPreferences("diag-send", Context.MODE_PRIVATE).edit().clear().commit()
        Diagnostics.resetSendDedupe()
        Diagnostics.allowDebugSend = true
    }

    @After fun cleanUp() {
        Diagnostics.allowDebugSend = false
        Diagnostics.resetSendDedupe()
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
        assertTrue("thành phần phải nêu engine Java, hiện: ${report.component}", report.component.contains("j2me"))
        // #302 (lỗ 1): có phiên `midlet` thì `Components` nhận ra engine, không rơi về fallback `app:midlet`.
        assertEquals("engine:j2me", report.component)
        assertTrue(report.fromGame)
    }

    /**
     * #655: `collect` xu ly phien `:midlet` chet vi REASON_EXCESSIVE_RESOURCE_USAGE thanh bao cao
     * `killed` (CO phien) roi xoa `diag/sessions/<pid>.json`. Truoc ban sua, cua chan trung trong
     * `collectProcessDeaths` chi ap cho `:game`, nen no van tao bao cao `resource` cung `id` (moc
     * thoi gian) va ghi de tep -> mat ban co phien, nhom #302 lai "unknown". Test chay dung chuoi do.
     */
    @Test fun collectThenScanKeepsMidletSessionReportInsteadOfOverwriting() {
        val pid = 4545
        val diedAt = System.currentTimeMillis() - 5_000
        writeLiveSession(pid, core = "midlet", system = "J2ME", diedAt = diedAt)
        writeDeathSnapshot(pid, kind = "midlet", core = "midlet", system = "J2ME", diedAt = diedAt)
        addProcessExit(pid, "${context.packageName}:midlet", ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
            diedAt, Diagnostics.deathProcessSummary(generation))

        val fromCollect = Diagnostics.collect(context)
        // #302: cùng một lần chết, `collect()` phải mang nhãn `resource` như `collectProcessDeaths`
        // (trước đây là `killed`), nếu không lõi và nhóm bị lệch tuỳ đường ghi.
        assertEquals("resource", fromCollect?.kind)
        assertEquals("midlet", fromCollect?.session?.core)

        // Quet lai lich su chet: khong duoc tao bao cao thu hai cung moc (se ghi de ban co phien).
        assertTrue(Diagnostics.collectProcessDeaths(context).none { it.id == diedAt })

        val saved = Diagnostics.list(context).single { it.id == diedAt }
        assertEquals("resource", saved.kind)
        assertEquals("midlet", saved.session?.core)
        assertEquals("J2ME", saved.session?.system)
    }

    /** Phien dang mo o `diag/active-session.json` — dung khuon `Diagnostics.write` ghi (PID tien trinh game). */
    private fun writeLiveSession(pid: Int, core: String, system: String, diedAt: Long) {
        val session = Diagnostics.Session(
            pid, diedAt - 60_000, "midlet", core, "kiem thu",
            system = system, stage = "playing", stageAt = diedAt - 1_000, lastAlive = diedAt - 10,
        )
        File(diag, "active-session.json").apply { parentFile!!.mkdirs() }
            .writeText(Json.encodeToString(Diagnostics.Session.serializer(), session))
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

    /**
     * Ảnh chụp phiên không khớp (processStateSummary lệch / đã bị dọn / PID được cấp lại) → báo cáo không có
     * phiên, `core` rỗng, thành phần lùi về nhãn tiến trình.
     *
     * Đây **chính là** đường còn lại sinh được chữ ký cũ `unknown / resource / 2ba6b80864be…` của #302: bot
     * tính `core` từ `session.core`, thiếu phiên thì `token("")` = `"unknown"`, và `frames` rỗng vì báo cáo
     * `resource` không có stack. Không bịa lõi từ tên tiến trình (quy ước: `unknown` = thiếu bằng chứng),
     * nên ca này được giữ nguyên và theo dõi qua số lượt của nhóm đó sau khi phát hành.
     */
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

    /**
     * #302 (duyệt PR #692, điểm 1): một lần chết chỉ được ra **một** báo cáo.
     *
     * Chuỗi thật: `collect` chạy khi Android **chưa** ghi lý do chết (`exitInfo == null`) → lập báo cáo tạm
     * `noexit` với `id = lastAlive`; lượt quét chạy ngay sau đó thấy `EXCESSIVE_RESOURCE_USAGE` → lập báo cáo
     * `resource` với `id = mốc chết`. Hai `id` khác nhau nên cửa chặn theo `id` (`collectProcessDeaths`) không
     * khớp → cả hai cùng nằm trong kho. Bản `resource` có phiên là bản đầy đủ hơn, nên phải **thay thế** bản
     * tạm của đúng phiên đó (pid + startedAt), và hộp thoại phải trỏ sang bản đầy đủ.
     */
    @Test fun oneDeathYieldsExactlyOneReport() {
        val pid = 4646
        val diedAt = System.currentTimeMillis() - 5_000
        writeLiveSession(pid, core = "midlet", system = "J2ME", diedAt = diedAt)
        writeDeathSnapshot(pid, kind = "midlet", core = "midlet", system = "J2ME", diedAt = diedAt)

        // Lượt 1: chưa có lý do chết → báo cáo tạm `noexit`; ảnh chụp phiên phải được giữ cho lượt quét.
        val fromCollect = Diagnostics.collect(context)
        assertEquals("noexit", fromCollect?.kind)
        assertTrue("ảnh chụp phiên phải còn cho lượt quét sau: sessions/$pid.json", File(diag, "sessions/$pid.json").exists())

        // Lượt 2: Android đã ghi lý do chết → báo cáo `resource` có phiên, thay thế bản tạm.
        addProcessExit(pid, "${context.packageName}:midlet", ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
            diedAt, Diagnostics.deathProcessSummary(generation))
        Diagnostics.collectProcessDeaths(context)

        val forDeath = Diagnostics.list(context).filter { it.id in (diedAt - 60_000)..(diedAt + 60_000) }
        assertEquals("mỗi lần chết chỉ được 1 báo cáo, hiện: ${forDeath.map { it.kind }}", 1, forDeath.size)
        val only = forDeath.single()
        assertEquals("resource", only.kind)
        assertEquals("midlet", only.core)
        assertEquals("midlet", only.session?.core)
        assertEquals("J2ME", only.system)
        assertEquals("engine:j2me", only.component)
        assertEquals("hộp thoại phải trỏ bản đầy đủ", only.id, Diagnostics.pending.value?.id)
    }

    /** Lỗ 2, vế ngược: có `ApplicationExitInfo` thì dọn ảnh chụp như cũ — không để rác trong `diag/sessions`. */
    @Test fun collectWithExitInfoStillClearsSnapshot() {
        val pid = 4747
        val diedAt = System.currentTimeMillis() - 5_000
        writeLiveSession(pid, core = "midlet", system = "J2ME", diedAt = diedAt)
        writeDeathSnapshot(pid, kind = "midlet", core = "midlet", system = "J2ME", diedAt = diedAt)
        addProcessExit(pid, "${context.packageName}:midlet", ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
            diedAt, Diagnostics.deathProcessSummary(generation))

        assertEquals("resource", Diagnostics.collect(context)?.kind)
        assertFalse("có lý do chết thì phải dọn ảnh chụp: sessions/$pid.json", File(diag, "sessions/$pid.json").exists())
    }

    /**
     * Duyệt PR #692 (lần 3) — **đường thật**, không gọi `record` trực tiếp.
     *
     * `MainActivity.kt:88-89` gọi `collect` + `collectProcessDeaths` rồi `autoSend` **từng** báo cáo trong
     * `fresh`. Bản tạm `noexit` bị bản `resource` thay thế ngay trong cùng lượt (tệp `<lastAlive>.json` bị
     * `record` xoá), nhưng `autoSend` vẫn gửi nó và `save(c, r.copy(sent = true))`
     * (`Diagnostics.kt:783`, ghi tệp ở `:1141`) **dựng lại tệp vừa xoá** ⇒ một lần chết thành 2 báo cáo
     * trong kho **và** 2 bản ghi ở Worker (2 nhóm bot).
     */
    @Test fun realFlowWithAutoSendKeepsOneReportPerDeath() {
        val pid = 4646
        val diedAt = System.currentTimeMillis() - 5_000
        writeLiveSession(pid, core = "midlet", system = "J2ME", diedAt = diedAt)
        writeDeathSnapshot(pid, kind = "midlet", core = "midlet", system = "J2ME", diedAt = diedAt)

        // Đúng thứ tự MainActivity: collect (Android chưa ghi lý do chết) → có lý do chết → quét → gửi.
        val fresh = ArrayList<Diagnostics.Report>()
        Diagnostics.collect(context)?.let { fresh.add(it) }
        addProcessExit(pid, "${context.packageName}:midlet", ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
            diedAt, Diagnostics.deathProcessSummary(generation))
        fresh.addAll(Diagnostics.collectProcessDeaths(context))

        val sends = Sends()
        fresh.forEach { Diagnostics.autoSend(context, sends.http, it, "https://crash.invalid/report", true, true) }
        val sent = sends.settle()

        val forDeath = Diagnostics.list(context).filter { it.id in (diedAt - 60_000)..(diedAt + 60_000) }
        assertEquals("mỗi lần chết chỉ được 1 báo cáo trong kho, hiện: ${forDeath.map { it.kind }}", 1, forDeath.size)
        assertEquals("mỗi lần chết chỉ được gửi 1 lần, hiện: ${sends.kinds()}", 1, sent)
        assertEquals("bản gửi phải là `resource`", listOf("resource"), sends.kinds())
        assertEquals("resource", forDeath.single().kind)
        assertEquals("midlet", forDeath.single().core)
        assertEquals("midlet", forDeath.single().session?.core)
    }

    /**
     * API < 30 (máy 9–10): **không có** `ApplicationExitInfo` nên lượt quét không bao giờ bù được — bản
     * `noexit` là bằng chứng duy nhất. Hành vi phải giữ nguyên: đúng **1** báo cáo, có phiên, và vẫn được
     * `autoSend` gửi.
     */
    @Test @Config(sdk = [29]) fun apiUnder30KeepsExactlyOneNoexitReportAndStillSendsIt() {
        val pid = 4848
        val diedAt = System.currentTimeMillis() - 5_000
        writeLiveSession(pid, core = "midlet", system = "J2ME", diedAt = diedAt)
        writeDeathSnapshot(pid, kind = "midlet", core = "midlet", system = "J2ME", diedAt = diedAt)

        val fresh = ArrayList<Diagnostics.Report>()
        Diagnostics.collect(context)?.let { fresh.add(it) }
        assertTrue("API < 30 không có lượt quét bù", Diagnostics.collectProcessDeaths(context).isEmpty())

        val sends = Sends()
        fresh.forEach { Diagnostics.autoSend(context, sends.http, it, "https://crash.invalid/report", true, true) }
        assertEquals("bản `noexit` vẫn phải được gửi", 1, sends.settle())

        val reports = Diagnostics.list(context)
        assertEquals("đúng 1 báo cáo", 1, reports.size)
        assertEquals("noexit", reports.single().kind)
        assertEquals("midlet", reports.single().session?.core)
        assertEquals(listOf("noexit"), sends.kinds())
    }

    /**
     * Vế "lượt quét sau" của cùng lỗi: bản tạm được `autoSend` gửi **trước** (đúng lúc nó là bằng chứng duy
     * nhất), rồi lượt quét sau mới tìm ra `resource`. Lúc gửi xong, `save(c, r.copy(sent = true))` không được
     * dựng lại tệp vừa bị `record` xoá — nếu không kho lại có 2 báo cáo cho một lần chết.
     *
     * Chặn phản hồi bằng latch để cuộc đua "gửi xong sau khi bị thay thế" là tất định.
     */
    @Test fun lateSendDoesNotResurrectReplacedReport() {
        val pid = 4949
        val diedAt = System.currentTimeMillis() - 5_000
        writeLiveSession(pid, core = "midlet", system = "J2ME", diedAt = diedAt)
        writeDeathSnapshot(pid, kind = "midlet", core = "midlet", system = "J2ME", diedAt = diedAt)

        val release = java.util.concurrent.CountDownLatch(1)
        val bodies = CopyOnWriteArrayList<String>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            assertTrue("chờ lượt quét chạy trước khi trả lời", release.await(5, java.util.concurrent.TimeUnit.SECONDS))
            val buf = okio.Buffer()
            chain.request().body!!.writeTo(buf)
            bodies.add(buf.readUtf8())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("{}".toResponseBody(null)).build()
        }.build()

        val first = Diagnostics.collect(context)
        assertEquals("noexit", first?.kind)
        Diagnostics.autoSend(context, http, first!!, "https://crash.invalid/report", true, true)

        // Trong lúc request còn treo: Android ghi lý do chết, lượt quét thay thế bản tạm.
        addProcessExit(pid, "${context.packageName}:midlet", ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
            diedAt, Diagnostics.deathProcessSummary(generation))
        Diagnostics.collectProcessDeaths(context)
        release.countDown()
        // Chờ request chạy xong rồi chờ `markSent` + `save` của luồng gửi.
        val deadline = System.currentTimeMillis() + 3_000
        while (bodies.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertEquals("request phải chạy xong", 1, bodies.size)
        Thread.sleep(400)

        val forDeath = Diagnostics.list(context).filter { it.id in (diedAt - 60_000)..(diedAt + 60_000) }
        assertEquals("bản tạm đã bị thay thế không được dựng lại: ${forDeath.map { it.kind }}", 1, forDeath.size)
        assertEquals("resource", forDeath.single().kind)
    }

    /** OkHttp giả: ghi lại body từng request (đọc `kind`), trả 200; [settle] chờ luồng gửi lắng xuống. */
    private class Sends {
        val bodies = CopyOnWriteArrayList<String>()
        val http: OkHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
            val buf = okio.Buffer()
            chain.request().body!!.writeTo(buf)
            bodies.add(buf.readUtf8())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("{}".toResponseBody(null)).build()
        }.build()

        fun kinds(): List<String> = bodies.map { Regex("\"kind\":\"(\\w+)\"").find(it)?.groupValues?.get(1).orEmpty() }

        /** Chờ tới khi số request đứng yên 250 ms (luồng `autoSend` ghi body rồi mới `save`). */
        fun settle(ms: Long = 3_000): Int {
            val deadline = System.currentTimeMillis() + ms
            var last = bodies.size
            var stableSince = System.currentTimeMillis()
            while (System.currentTimeMillis() < deadline) {
                Thread.sleep(25)
                if (bodies.size != last) {
                    last = bodies.size
                    stableSince = System.currentTimeMillis()
                } else if (System.currentTimeMillis() - stableSince >= 250) break
            }
            Thread.sleep(150) // `markSent` + `save` chạy ngay sau phản hồi
            return bodies.size
        }
    }
}
