package vn.aow.monika.apkinstall

import android.os.Bundle
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = vn.aow.monika.ui.TestApp::class, sdk = [34])
class Step5Test {
    @get:Rule val tmp = TemporaryFolder()
    private val dev34 = DeviceInfo(34, listOf("arm64-v8a"), emptyList(), 420, 100L shl 30)
    private val dev29 = DeviceInfo(29, listOf("arm64-v8a"), listOf("armeabi-v7a"), 420, 100L shl 30)
    private val dev33 = DeviceInfo(33, listOf("arm64-v8a"), emptyList(), 420, 100L shl 30)

    @Before fun setup() { LoaderEvents.clock = { System.currentTimeMillis() } }
    @After fun teardown() { LoaderEvents.clock = { System.currentTimeMillis() } }

    private fun payload(b: ByteArray) = Payload("x", b.size.toLong()) { b.inputStream() }
    private fun result(target: Int = 30, data: List<DataFile> = emptyList(), obb: List<ObbFile> = emptyList(), dev: DeviceInfo = dev34): InspectResult {
        val apk = ApkFixture.apk(File(tmp.root, "b${System.nanoTime()}.apk"), "com.foo.game", targetSdk = target)
        val low = dev.minInstallableTargetSdk > 0 && target < dev.minInstallableTargetSdk
        return InspectResult(PackKind.APK, "com.foo.game", "Foo", 5, "1", 21, target, listOf(ApkPart(apk, null)), emptySet(), obb, data,
            if (low) listOf(Problem.LowTargetSdk(target, dev.minInstallableTargetSdk)) else emptyList())
    }
    private fun checklist(): InstallChecklist { val m = HashMap<String, String>(); return InstallChecklist({ m[it] }, { k, v -> m[k] = v }, { 1L }) }

    // ---- kế hoạch ----
    @Test fun planChoosesRightPath() {
        assertEquals(InstallPlan.PLAIN, ApkInstallFlow.plan(result(30), dev34))
        assertEquals(InstallPlan.CHECKLIST, ApkInstallFlow.plan(result(22), dev34))          // game cũ, Android 14
        assertEquals(InstallPlan.PLAIN, ApkInstallFlow.plan(result(22, dev = dev33), dev33))              // Android 13 không chặn
        val data = listOf(DataFile("a", payload(ByteArray(1))))
        assertEquals(InstallPlan.CHECKLIST, ApkInstallFlow.plan(result(30, data), dev34))    // cần Data, Android 11+
        assertEquals(InstallPlan.PLAIN_DIRECT_DATA, ApkInstallFlow.plan(result(30, data, dev = dev29), dev29))
    }

    @Test fun applicableMethods() {
        val data = listOf(DataFile("a", payload(ByteArray(1))))
        assertTrue(Method.SAF in ApkInstallFlow.applicable(result(30, data)))
        assertFalse(Method.SAF in ApkInstallFlow.applicable(result(22, data)))   // Cách 2 không cài được game bị chặn targetSdk
        assertFalse(Method.SAF in ApkInstallFlow.applicable(result(22)))
        assertTrue(Method.REPACK in ApkInstallFlow.applicable(result(22)))
    }

    // ---- checklist ----
    @Test fun checklistKeepsThreeRowsAndNext() {
        val c = checklist()
        assertEquals(3, c.read("p", 1).size)
        assertEquals(Method.REPACK, c.next("p", 1, setOf(Method.REPACK, Method.ADB)))
        c.set("p", 1, Method.REPACK, State.FAILED, "hỏng")
        assertEquals(State.FAILED, c.read("p", 1)[0].state)
        assertEquals("hỏng", c.read("p", 1)[0].reason)
        assertEquals(Method.ADB, c.next("p", 1, setOf(Method.REPACK, Method.ADB)))
        assertNull(c.next("p", 1, setOf(Method.REPACK)))
        assertEquals(State.NOT_TRIED, c.read("p", 2)[0].state) // phiên bản khác → sổ riêng
        c.clear("p", 1)
        assertEquals(State.NOT_TRIED, c.read("p", 1)[0].state)
    }

    // ---- bus ----
    @Test fun loaderEventsTrackState() {
        var t = 1000L; LoaderEvents.clock = { t }
        LoaderEvents.reset("com.g")
        LoaderEvents.post("com.g", "started", null, null); t = 2000
        LoaderEvents.post("com.g", "screen", "MainActivity", null); t = 3000
        LoaderEvents.post("com.g", "beat", null, null)
        val s = LoaderEvents.state("com.g")
        assertEquals(1000L, s.startedAt); assertEquals(2000L, s.screenAt); assertEquals(3000L, s.lastBeat)
        val b = Bundle().apply { putString("summary", "java.lang.Boom\n at x") }
        LoaderEvents.post("com.g", "crash", null, b)
        assertEquals("java.lang.Boom\n at x", s.crashSummary)
        LoaderEvents.reset("com.g")
        assertEquals(0L, LoaderEvents.state("com.g").screenAt)
    }

    @Test fun copyEventsReachListener() {
        val got = ArrayList<CopyEvent>()
        LoaderEvents.onCopy("job1") { got += it }
        LoaderEvents.post("com.g", "progress", "job1", Bundle().apply { putLong("done", 5); putLong("total", 10); putInt("files", 1) })
        LoaderEvents.post("com.g", "done", "job1", Bundle().apply { putInt("files", 2); putInt("expected", 2); putLong("bytes", 10) })
        LoaderEvents.post("com.g", "error", "job1", Bundle().apply { putString("error", "hỏng") })
        LoaderEvents.onCopy("job1", null)
        LoaderEvents.post("com.g", "done", "job1", Bundle())
        assertEquals(3, got.size)
        assertEquals(5L, (got[0] as CopyEvent.Progress).p.done)
        assertEquals(2, (got[1] as CopyEvent.Done).files)
        assertEquals("hỏng", (got[2] as CopyEvent.Error).message)
    }

    @Test fun dataJobManifest() {
        val job = DataJob("j", "com.g", listOf(DataFile("files/a.dat", payload(ByteArray(3))), DataFile("obb/b.bin", payload(ByteArray(7)))))
        assertEquals("0\t3\tfiles/a.dat\n1\t7\tobb/b.bin", job.manifest())
    }

    // ---- kiểm tra thử ----
    private fun health(script: (LoaderState, () -> Long) -> Unit, start: Long = 400, alive: Long = 500): Health {
        return runBlocking {
            val pkg = "com.health.${System.nanoTime()}"
            HealthCheck.run(pkg, launch = { script(LoaderEvents.state(pkg), { System.currentTimeMillis() }) }, startMs = start, aliveMs = alive, pollMs = 20)
        }
    }

    @Test fun healthNeverStarts() {
        val h = health({ _, _ -> })
        assertTrue(h is Health.Failed); assertTrue((h as Health.Failed).reason.contains("không hiện màn hình"))
    }

    @Test fun healthCrash() {
        val h = health({ s, now -> s.crashAt = now(); s.crashSummary = "java.lang.IllegalStateException: x\n at y" })
        assertTrue((h as Health.Failed).reason.contains("IllegalStateException"))
    }

    @Test fun healthExitsImmediately() {
        // Hiện màn hình nhưng nhịp sống đã cũ (tiến trình chết) → "thoát ngay".
        val h = health({ s, now -> s.screenAt = now(); s.lastBeat = now() - 10_000 })
        assertTrue((h as Health.Failed).reason.contains("thoát ngay"))
    }

    @Test fun healthLikelyOk() {
        val stop = java.util.concurrent.atomic.AtomicBoolean(false)
        val h = health({ s, now ->
            s.screenAt = now(); s.lastBeat = now()
            Thread { while (!stop.get()) { s.lastBeat = System.currentTimeMillis(); Thread.sleep(50) } }.apply { isDaemon = true }.start()
        })
        stop.set(true)
        assertTrue(h is Health.LikelyOk)
    }

    @Test fun healthAnswersUpdateChecklist() {
        val c = checklist(); val r = result(30)
        val ask = ApkInstallFlow.applyHealth(r, Health.LikelyOk, c)
        assertEquals(UiState.Phase.HEALTH_ASK, ask.phase)
        assertEquals(UiState.Phase.DONE, ApkInstallFlow.confirmWorks(r, true, c).phase)
        assertEquals(State.OK, c.read("com.foo.game", 5)[0].state)
        val bad = ApkInstallFlow.confirmWorks(r, false, c)
        assertEquals(UiState.Phase.CHOOSE_METHOD, bad.phase)
        assertEquals(State.FAILED, c.read("com.foo.game", 5)[0].state)
        val failed = ApkInstallFlow.applyHealth(r, Health.Failed("game thoát ngay"), c)
        assertEquals(UiState.Phase.CHOOSE_METHOD, failed.phase)
        assertEquals("game thoát ngay", c.read("com.foo.game", 5)[0].reason)
    }

    // ---- Cách 1 ----
    private val okInstaller = ApkInstaller { _, _, p -> p(100); InstallOutcome.Success }

    @Test fun repackFlowHappyPath() = runBlocking {
        val data = listOf(DataFile("files/a.dat", payload(ByteArray(4) { 1 })))
        val r = result(22, data, listOf(ObbFile("main.5.com.foo.game.obb", payload(ByteArray(9)))))
        val c = checklist(); val seen = ArrayList<UiState.Phase>(); var registered: String? = null; var raised: Int? = -1; var pushed = 0
        val end = ApkInstallFlow.executeRepack(
            r, okInstaller,
            repacker = { part, isBase, raise -> raised = raise; assertTrue(isBase); part.file },
            pushData = { files, p -> pushed = files.size; p(100); DataPusher.Result.Ok(files.size, 4) },
            register = { registered = it },
            obbDir = File(tmp.root, "obb"), device = dev34, checklist = c, onState = { seen += it.phase },
        )
        assertEquals(UiState.Phase.HEALTH_READY, end.phase)
        assertEquals("com.foo.game", registered)
        assertEquals(dev34.minInstallableTargetSdk, raised)
        assertEquals(1, pushed)
        assertEquals(9L, File(tmp.root, "obb/main.5.com.foo.game.obb").length())
        assertTrue(seen.containsAll(listOf(UiState.Phase.REPACKING, UiState.Phase.INSTALLING, UiState.Phase.COPYING_OBB, UiState.Phase.PUSHING_DATA, UiState.Phase.HEALTH_READY)))
        assertEquals(State.RUNNING, c.read("com.foo.game", 5)[0].state)
    }

    @Test fun repackDoesNotRaiseWhenNotLowTarget() = runBlocking {
        var raised: Int? = -1
        ApkInstallFlow.executeRepack(result(30, listOf(DataFile("a", payload(ByteArray(1))))), okInstaller, { p, _, r -> raised = r; p.file },
            { f, _ -> DataPusher.Result.Ok(f.size, 1) }, {}, File(tmp.root, "o"), dev34, checklist()) {}
        assertNull(raised)
    }

    @Test fun repackFailureRecordedInChecklist() = runBlocking {
        val c = checklist()
        val end = ApkInstallFlow.executeRepack(result(22), okInstaller, { _, _, _ -> throw IllegalStateException("boom") },
            { f, _ -> DataPusher.Result.Ok(f.size, 1) }, {}, File(tmp.root, "o2"), dev34, c) {}
        assertEquals(UiState.Phase.FAILED, end.phase)
        assertEquals(State.FAILED, c.read("com.foo.game", 5)[0].state)
    }

    @Test fun signatureConflictIsNotAMethodFailure() = runBlocking {
        val c = checklist()
        val conflict = InstallOutcome.from(android.content.pm.PackageInstaller.STATUS_FAILURE_CONFLICT, "INSTALL_FAILED_UPDATE_INCOMPATIBLE")
        val end = ApkInstallFlow.executeRepack(result(22), { _, _, _ -> conflict }, { p, _, _ -> p.file },
            { f, _ -> DataPusher.Result.Ok(f.size, 1) }, {}, File(tmp.root, "o3"), dev34, c) {}
        assertEquals(UiState.Phase.FAILED, end.phase)
        assertEquals(InstallOutcome.Kind.SIGNATURE_CONFLICT, end.failure?.kind)
        assertEquals(State.NOT_TRIED, c.read("com.foo.game", 5)[0].state) // gỡ bản cũ rồi thử lại, không tính là hỏng
    }

    @Test fun dataPushFailureRecorded() = runBlocking {
        val c = checklist()
        val r = result(30, listOf(DataFile("a", payload(ByteArray(1)))))
        val end = ApkInstallFlow.executeRepack(r, okInstaller, { p, _, _ -> p.file }, { _, _ -> DataPusher.Result.Failed("Game không phản hồi") }, {}, File(tmp.root, "o4"), dev34, c) {}
        assertEquals(UiState.Phase.FAILED, end.phase)
        assertEquals("Game không phản hồi", c.read("com.foo.game", 5)[0].reason)
    }

    // ---- Android ≤ 10 chép Data thẳng ----
    @Test fun directDataOnAndroid10() = runBlocking {
        val r = result(30, listOf(DataFile("files/a.dat", payload(ByteArray(6) { 3 })), DataFile("b/c.dat", payload(ByteArray(2)))))
        val dataDir = File(tmp.root, "Android/data/com.foo.game")
        val end = ApkInstallFlow.execute(r, okInstaller, File(tmp.root, "obb9"), dev29, dataDir) {}
        assertEquals(UiState.Phase.DONE, end.phase)
        assertEquals(6L, File(dataDir, "files/a.dat").length())
        assertEquals(2L, File(dataDir, "b/c.dat").length())
    }

    @Test fun directDataRejectsPathTraversal() {
        val r = DataInstaller.copyDirect("p", listOf(DataFile("../evil.txt", payload(ByteArray(1)))), File(tmp.root, "safe"))
        assertTrue(r is DataInstaller.Result.Failed)
        assertFalse(File(tmp.root, "evil.txt").exists())
    }
}
