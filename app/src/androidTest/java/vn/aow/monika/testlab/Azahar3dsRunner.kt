package vn.aow.monika.testlab

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import vn.aow.monika.AppGraph
import vn.aow.monika.diag.Breadcrumbs
import vn.aow.monika.diag.Diagnostics
import java.io.File

/**
 * Chạy game 3DS THẬT (workflow real-3ds.yml đẩy file vào /sdcard/Download/monika-3ds.<đuôi>) trên máy ARM64 Test Lab.
 * Không in tên game; chỉ ghi số liệu: tiến trình sống/chết, khung hình có vẽ không, vệt (crumb), logcat, tombstone.
 */
class Azahar3dsRunner : Instrumentation() {
    private val rows = JSONArray()
    private lateinit var evidence: File
    private var gamePid = 0
    private val manager get() = targetContext.getSystemService(ActivityManager::class.java)

    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }

    override fun onStart() {
        evidence = File(checkNotNull(targetContext.getExternalFilesDir(null)), "r3ds").apply { mkdirs() }
        var game: File? = null
        val steps = listOf<Pair<String, () -> Unit>>(
            "R1" to { engine() },
            "R2" to { game = importGame() },
            "R3" to { run(checkNotNull(game)) },
        )
        var failures = 0
        var blocked: String? = null
        steps.forEachIndexed { index, (name, action) ->
            val status = Bundle().apply {
                putString("id", "InstrumentationTestRunner"); putInt("numtests", steps.size)
                putString("class", javaClass.name); putString("test", name); putInt("current", index + 1)
            }
            sendStatus(1, status)
            val row = JSONObject().put("step", name)
            val skip = blocked
            if (skip != null) {
                row.put("status", "BLOCKED").put("detail", "dependency: $skip")
            } else {
                try { action(); row.put("status", "PASS") } catch (error: Throwable) {
                    failures++; blocked = name
                    row.put("status", "FAIL").put("detail", Diagnostics.scrub(targetContext, error.stackTraceToString()).take(8000))
                }
            }
            try { collect(name, row) } catch (error: Throwable) { row.put("collection", error.toString().take(500)) }
            status.putString("stream", "$name ${row.getString("status")}\n")
            sendStatus(if (row.getString("status") == "PASS") 0 else -2, status)
            rows.put(row); writeSummary()
        }
        finish(-1, Bundle().apply {
            putString("stream", "R3DS: ${steps.size} steps; $failures failures; $rows\n")
            if (failures != 0) putString("shortMsg", "3DS thật: có bước lỗi, xem evidence")
        })
    }

    private fun engine() = runBlocking {
        check("arm64-v8a" in Build.SUPPORTED_ABIS) { "cần máy arm64-v8a thật" }
        check(AppGraph.azahar.available()) { "config không cho engine 3DS trên máy này" }
        AppGraph.azahar.ensure()
        check(AppGraph.azahar.ready()) { "engine 3DS chưa sẵn sàng sau ensure()" }
    }

    private fun importGame(): File {
        val src = shell("ls /sdcard/Download/ 2>/dev/null | grep '^monika-3ds\\.'").trim().lineSequence().firstOrNull().orEmpty()
        if (src.isEmpty()) {
            // Chế độ engine: ROM giả 2 MiB (chỉ có dấu NCSD) để thử khởi tạo engine mà không đẩy game thật lên máy.
            val fake = File(evidence.parentFile, "game-synthetic.3ds")
            fake.outputStream().use { out -> val block = ByteArray(1 shl 20); block[0x100] = 'N'.code.toByte(); block[0x101] = 'C'.code.toByte(); block[0x102] = 'S'.code.toByte(); block[0x103] = 'D'.code.toByte(); out.write(block); out.write(ByteArray(1 shl 20)) }
            File(evidence, "game-meta.txt").writeText("synthetic=true bytes=${fake.length()}\n")
            return fake
        }
        val dest = File(evidence.parentFile, "game-" + src.substringAfterLast('.')).also { it.delete() }
        shell("cp /sdcard/Download/$src ${dest.absolutePath}")
        check(dest.isFile && dest.length() > 1_000_000) { "chép game lỗi (size=${dest.length()})" }
        File(evidence, "game-meta.txt").writeText("synthetic=false ext=${src.substringAfterLast('.')} bytes=${dest.length()}\n")
        return dest
    }

    private fun run(game: File) {
        val synthetic = game.name.startsWith("game-synthetic")
        targetContext.startActivity(Intent().setComponent(ComponentName(targetContext, "vn.aow.monika.azahar.AzaharActivity"))
            .putExtra("game", game.absolutePath).putExtra("system", "Nintendo 3DS").putExtra("title", "R3DS")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        val startedAt = System.currentTimeMillis()
        waitFor("tiến trình :game", 20000) { findPid() > 0 }
        gamePid = findPid()
        var rendered = false
        var diedAt = -1
        val ticks = if (synthetic) 6 else 18 // 30 giây (ROM giả) hoặc 90 giây (ROM thật), 5 giây/lần
        for (t in 1..ticks) {
            SystemClock.sleep(5000)
            if (findPid() != gamePid) { diedAt = t * 5; break }
            if (!synthetic && t % 3 == 0) rendered = rendered || shotStats("t${t * 5}")
        }
        val reachedSetup = Breadcrumbs.read(targetContext, gamePid, 200).any { it.contains("setUp: xong phần nền") }
        val nativeCrash = Build.VERSION.SDK_INT >= 30 && manager.getHistoricalProcessExitReasons(targetContext.packageName, 0, 20)
            .any { it.timestamp >= startedAt && it.processName.endsWith(":game") && it.reason == ApplicationExitInfo.REASON_CRASH_NATIVE }
        val row = JSONObject().put("synthetic", synthetic).put("rendered", rendered).put("diedAtSec", diedAt)
            .put("reachedSetup", reachedSetup).put("nativeCrash", nativeCrash)
        File(evidence, "run.json").writeText(row.toString())
        check(!nativeCrash) { "native crash trong :game (xem exit/crumbs/tombstone), reachedSetup=$reachedSetup" }
        check(reachedSetup) { "không tới được cuối setUp() (xem crumbs để biết bước dừng)" }
        if (!synthetic) {
            check(diedAt < 0) { "tiến trình game chết sau ${diedAt}s (xem exit/crash/crumb)" }
            check(rendered) { "game sống 90s nhưng khung hình chỉ một màu (màn đen/trắng)" }
        }
    }

    private fun findPid() = manager.runningAppProcesses.orEmpty().firstOrNull { it.processName == "${targetContext.packageName}:game" }?.pid ?: 0

    /** Trả về true nếu khung hình có nhiều màu (đang vẽ thật). Lưu PNG vào evidence (workflow không đưa PNG lên artifact). */
    private fun shotStats(name: String): Boolean {
        val image = checkNotNull(uiAutomation.takeScreenshot()) { "không chụp được màn hình" }
        try {
            File(evidence, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 90, it) }
            val colors = HashSet<Int>()
            for (x in 0 until 24) for (y in 0 until 24) colors += image.getPixel(x * image.width / 24, y * image.height / 24) and 0xF8F8F8
            File(evidence, "$name.txt").writeText("distinctColors=${colors.size}\n")
            return colors.size >= 12
        } finally { image.recycle() }
    }

    private fun collect(name: String, row: JSONObject) {
        val exits = if (Build.VERSION.SDK_INT >= 30) manager.getHistoricalProcessExitReasons(targetContext.packageName, 0, 20)
            .filter { it.processName.endsWith(":game") } else emptyList()
        val pids = (exits.map { it.pid } + gamePid).filter { it > 0 }.distinct()
        row.put("pid", gamePid)
        File(evidence, "$name-exit.txt").writeText(Diagnostics.scrub(targetContext, exits.joinToString("\n") {
            "pid=${it.pid} reason=${it.reason} status=${it.status} desc=${it.description}"
        }))
        File(evidence, "$name-crumbs.txt").writeText(Diagnostics.scrub(targetContext, pids.joinToString("\n") { p ->
            "== pid $p ==\n" + Breadcrumbs.read(targetContext, p, 80).joinToString("\n")
        }))
        val log = pids.joinToString("\n") { shell("logcat -d -v threadtime --pid=$it -t 2500") }
        File(evidence, "$name-pid.log").writeText(Diagnostics.scrub(targetContext, log))
        val crash = shell("logcat -b crash -d -v threadtime -t 500")
        File(evidence, "$name-crash.log").writeText(Diagnostics.scrub(targetContext, crash))
        row.put("nativeCrash", exits.any { it.reason == ApplicationExitInfo.REASON_CRASH_NATIVE })
        exits.filter { it.reason == ApplicationExitInfo.REASON_CRASH_NATIVE }.forEach { exit ->
            exit.traceInputStream?.use { trace ->
                val bytes = trace.readBytes()
                if (bytes.isNotEmpty() && bytes.size <= 1024 * 1024) File(evidence, "$name-${exit.pid}-tombstone.pb").writeBytes(bytes)
            }
        }
    }

    private fun waitFor(label: String, timeout: Long, predicate: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < until) { if (predicate()) return; SystemClock.sleep(250) }
        error("timeout: $label")
    }

    private fun shell(command: String): String = uiAutomation.executeShellCommand(command).use { descriptor ->
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText().take(1_000_000) }
    }

    private fun writeSummary() {
        File(evidence, "summary.json").writeText(JSONObject().put("abi", Build.SUPPORTED_ABIS.joinToString())
            .put("sdk", Build.VERSION.SDK_INT).put("model", Build.MODEL).put("rows", rows).toString(2))
    }
}
