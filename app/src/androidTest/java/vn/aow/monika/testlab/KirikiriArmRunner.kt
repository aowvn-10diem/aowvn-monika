package vn.aow.monika.testlab

import android.app.ActivityManager
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import vn.aow.monika.diag.Diagnostics
import vn.aow.monika.pack.PackTransaction
import vn.aow.monika.runner.KirikiriGameActivity
import java.io.File

/** Built-in Instrumentation protocol; no new dependency, no production/test exported activity. */
class KirikiriArmRunner : Instrumentation() {
    private val rows = JSONArray()
    private lateinit var evidence: File
    private lateinit var gameRoot: File
    private var gamePid = 0
    private var blocked: String? = null
    private var phaseStartedAt = System.currentTimeMillis()
    private val manager get() = targetContext.getSystemService(ActivityManager::class.java)

    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }

    override fun onStart() {
        // Files only under this fresh Test Lab installation. No owner games, accounts or credentials.
        evidence = File(checkNotNull(targetContext.getExternalFilesDir(null)), "v56").apply { mkdirs() }
        gameRoot = File(targetContext.filesDir, "games/v56").apply { mkdirs() }
        val steps = listOf<Pair<String, () -> Unit>>(
            "K1" to { install() },
            "K2" to { launch("s0"); marker("s0", "monika-ready.txt") },
            "K3" to { stopGame(); launch("s3"); marker("s3", "monika-ready.txt"); orange(shot("K3")) },
            "K4" to {
                val bitmap = checkNotNull(uiAutomation.takeScreenshot())
                shell("input tap ${bitmap.width / 2} ${bitmap.height / 2}")
                bitmap.recycle(); marker("s3", "monika-touch.txt"); alive()
            },
            "K5" to {
                shell("input keyevent KEYCODE_BACK")
                waitFor("Back menu Chơi tiếp", 10000) { hasText(uiAutomation.rootInActiveWindow, "Chơi tiếp") }
                shot("K5").recycle(); alive()
                shell("input keyevent KEYCODE_BACK")
                waitFor("menu dismissed", 10000) { !hasText(uiAutomation.rootInActiveWindow, "Chơi tiếp") }
            },
            "K6" to {
                val text = marker("s3", "monika-audio.txt")
                val positions = checkNotNull(Regex("pos1=(\\d+) pos2=(\\d+)").find(text))
                check(text.contains("status=play") && positions.groupValues[2].toLong() > positions.groupValues[1].toLong()) {
                    "audio position did not advance: $text (not an audible-sound test)"
                }
            },
            "K7" to {
                check(File(gameRoot, "s3/monika-save.txt").isFile) { "save absent before process kill" }
                stopGame(); launch("s3"); check(marker("s3", "monika-load.txt").contains("n=2")) { "load marker != n=2" }
                marker("s3", "monika-ready.txt"); orange(shot("K7"))
            },
            "K8" to {
                val before = gamePid
                shell("input keyevent KEYCODE_HOME"); SystemClock.sleep(3000); alive()
                val task = manager.appTasks.single { it.taskInfo.baseActivity?.className == KirikiriGameActivity::class.java.name }
                task.moveToFront()
                // Assert throughout the 60-second window, not merely a surviving stale PID at the end.
                repeat(30) { SystemClock.sleep(2000); alive(); check(gamePid == before) }
                orange(shot("K8"))
            },
        )
        var failures = 0
        steps.forEachIndexed { index, (name, action) ->
            phaseStartedAt = System.currentTimeMillis()
            val status = Bundle().apply {
                putString("id", "InstrumentationTestRunner"); putInt("numtests", steps.size)
                putString("class", this@KirikiriArmRunner.javaClass.name); putString("test", name); putInt("current", index + 1)
            }
            sendStatus(1, status)
            val row = JSONObject().put("game", name).put("synthetic", true).put("pid", gamePid)
            val skip = blocked
            if (skip != null) {
                row.put("status", "BLOCKED").put("detail", "dependency: $skip")
                status.putString("stream", "$name BLOCKED: $skip\n")
            } else {
                try {
                    action()
                    row.put("status", "PASS").put("detail", "assertions passed; synthetic only")
                    status.putString("stream", "$name PASS\n")
                } catch (error: Throwable) {
                    failures++
                    val detail = Diagnostics.scrub(targetContext, error.stackTraceToString()).take(12000)
                    row.put("status", "FAIL").put("detail", detail)
                    status.putString("stack", detail); status.putString("stream", "$name FAIL: $detail\n")
                    // No probing other transport/encodings after a foundational engine failure.
                    if (name in setOf("K1", "K2", "K3", "K7")) blocked = name
                }
            }
            try {
                shot("$name-final").recycle()
                collect(name, row)
                if (row.getString("status") == "PASS") check(!row.optBoolean("crash")) { "app crash exit record during $name" }
            } catch (error: Throwable) {
                val detail = Diagnostics.scrub(targetContext, error.toString())
                row.put("collection", detail)
                if (row.getString("status") == "PASS") {
                    failures++; row.put("status", "FAIL"); row.put("detail", detail)
                    status.putString("stack", detail); status.putString("stream", "$name FAIL: $detail\n")
                }
            }
            sendStatus(when (row.getString("status")) { "PASS" -> 0; "BLOCKED" -> -3; else -> -2 }, status)
            rows.put(row); writeSummary()
        }
        try { stopGame() } catch (_: Throwable) { /* Results survive in external evidence dir. */ }
        finish(-1, Bundle().apply {
            putString("stream", "V56: ${steps.size} steps; $failures failures; ${rows}\n")
            if (failures != 0) putString("shortMsg", "V56 engine assertions failed; inspect evidence")
        })
    }

    private fun install() = runBlocking {
        check("arm64-v8a" in Build.SUPPORTED_ABIS) { "requires arm64-v8a physical device" }
        val meta = JSONObject(context.assets.open("metadata.json").bufferedReader().use { it.readText() })
        check(meta.getBoolean("synthetic"))
        val target = File(targetContext.filesDir, "packs/kirikiri")
        PackTransaction.locked(target) {
            PackTransaction.install(target) { archive, candidate ->
                context.assets.open("kirikiri.zip").use {
                    PackTransaction.download(it, archive, meta.getString("sha256"), meta.getLong("size"))
                }
                PackTransaction.unzip(archive, candidate, mainFile = "libkrkr2yuri.so")
                PackTransaction.validate(candidate, "libkrkr2yuri.so", "arm64-v8a")
                check(File(candidate, "assets").isDirectory) { "engine assets absent" }
                File(candidate, "version").writeText(meta.getString("version"))
            }
        }
        listOf("s0", "s3").forEach { tier ->
            val dir = File(gameRoot, tier).apply { mkdirs() }
            listOf("startup.tjs", "beep.wav").forEach { name ->
                context.assets.open("games/$tier/$name").use { input -> File(dir, name).outputStream().use { input.copyTo(it) } }
            }
        }
        File(evidence, "metadata.json").writeText(meta.toString(2))
    }

    private fun launch(tier: String) {
        listOf("ready", "touch", "audio", "load").forEach { File(gameRoot, "$tier/monika-$it.txt").delete() }
        targetContext.startActivity(Intent(targetContext, KirikiriGameActivity::class.java)
            .putExtra("aow_game_path", File(gameRoot, "$tier/startup.tjs").absolutePath)
            .putExtra("title", "V56 synthetic $tier")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        waitFor("game PID", 15000) {
            gamePid = manager.runningAppProcesses.orEmpty().firstOrNull { it.processName == "${targetContext.packageName}:game" }?.pid ?: 0
            gamePid > 0
        }
    }
    private fun alive() {
        check(gamePid > 0 && manager.runningAppProcesses.orEmpty().any { it.pid == gamePid && it.processName == "${targetContext.packageName}:game" }) { "game process died" }
    }
    private fun stopGame() {
        if (gamePid <= 0) return
        val old = gamePid
        Process.killProcess(old) // Same UID; never force-stop the instrumented main process.
        waitFor("game PID stopped", 10000) { manager.runningAppProcesses.orEmpty().none { it.pid == old } }
        gamePid = 0
    }
    private fun marker(tier: String, name: String): String {
        val file = File(gameRoot, "$tier/$name")
        waitFor(name, 30000) { alive(); file.isFile && file.length() > 0 }
        return file.readText().also { File(evidence, "$tier-$name").writeText(it) }
    }
    private fun waitFor(label: String, timeout: Long, predicate: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < until) {
            if (predicate()) return
            SystemClock.sleep(250)
        }
        error("timeout: $label")
    }
    private fun shell(command: String): String = uiAutomation.executeShellCommand(command).use { descriptor ->
        android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText().take(256000) }
    }
    private fun shot(name: String): Bitmap = checkNotNull(uiAutomation.takeScreenshot()) { "screenshot unavailable" }.also { image ->
        File(evidence, "$name.png").outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
    private fun orange(image: Bitmap) {
        try {
            val pixel = image.getPixel(image.width / 2, image.height / 2)
            check(kotlin.math.abs(Color.red(pixel) - 242) <= 40 && kotlin.math.abs(Color.green(pixel) - 140) <= 40 && kotlin.math.abs(Color.blue(pixel) - 40) <= 40) {
                "center not orange: ${Color.red(pixel)},${Color.green(pixel)},${Color.blue(pixel)}"
            }
        } finally { image.recycle() }
    }
    private fun hasText(node: AccessibilityNodeInfo?, text: String): Boolean {
        if (node == null) return false
        if (node.text?.toString() == text) return true
        return (0 until node.childCount).any { hasText(node.getChild(it), text) }
    }
    private fun collect(name: String, row: JSONObject) {
        val exits = if (Build.VERSION.SDK_INT >= 30) manager.getHistoricalProcessExitReasons(targetContext.packageName, 0, 20)
            .filter { it.timestamp >= phaseStartedAt && it.processName.endsWith(":game") } else emptyList()
        val pids = (exits.map { it.pid } + gamePid).filter { it > 0 }.distinct()
        row.put("pid", gamePid).put("log", "$name-pid.log")
        val log = pids.joinToString("\n") { shell("logcat -d -v threadtime --pid=$it -t 1500") }
        File(evidence, "$name-pid.log").writeText(Diagnostics.scrub(targetContext, log))
        // Crash buffer retained only for own game PIDs; Google/launcher errors are not app failures.
        val crash = shell("logcat -b crash -d -v threadtime -t 500").lineSequence()
            .filter { line -> pids.any { line.contains("pid: $it,") || Regex("\\s$it\\s").containsMatchIn(line) } }.joinToString("\n")
        File(evidence, "$name-crash.log").writeText(Diagnostics.scrub(targetContext, crash))
        row.put("crash", exits.any { it.reason in listOf(android.app.ApplicationExitInfo.REASON_CRASH, android.app.ApplicationExitInfo.REASON_CRASH_NATIVE) } ||
            Regex("FATAL EXCEPTION|Fatal signal|error kirikiri-lib").containsMatchIn(log))
        // On Android 12+, native tombstone stream is protobuf. Read it bounded; absence is UNKNOWN, not "no crash".
        row.put("tombstone", "UNAVAILABLE / CHƯA KIỂM")
        exits.filter { it.reason == android.app.ApplicationExitInfo.REASON_CRASH_NATIVE }.forEach { exit ->
            exit.traceInputStream?.use { trace ->
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                while (buffer.size() <= 1024 * 1024) {
                    val count = trace.read(chunk)
                    if (count < 0) break
                    buffer.write(chunk, 0, count)
                }
                val bytes = buffer.toByteArray()
                if (bytes.isNotEmpty() && bytes.size <= 1024 * 1024) {
                    val filename = "$name-${exit.pid}-tombstone.pb"
                    File(evidence, filename).writeBytes(bytes); row.put("tombstone", filename)
                }
            }
        }
    }
    private fun writeSummary() {
        File(evidence, "summary.json").writeText(JSONObject().put("synthetic", true).put("abi", Build.SUPPORTED_ABIS.joinToString())
            .put("sdk", Build.VERSION.SDK_INT).put("model", Build.MODEL).put("rows", rows).toString(2))
    }
}
