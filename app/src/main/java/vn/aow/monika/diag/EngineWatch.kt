package vn.aow.monika.diag

import android.app.Activity
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.view.PixelCopy
import android.view.SurfaceView
import kotlinx.coroutines.*
import vn.aow.monika.AppGraph
import java.util.concurrent.TimeUnit

/** Chỉ giữ thống kê 64x36 trong RAM; không ghi/gửi ảnh ngầm. Đóng trước Diagnostics.end(). */
internal class EngineWatch(private val activity: Activity, private val engine: String, private val surface: () -> SurfaceView?) {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tracker = BlackFrameTracker()
    private val seen = mutableSetOf<String>()
    private var active = false
    private var generation = 0
    private var job: Job? = null
    private val sample = Runnable { capture(generation) }

    fun resume() {
        if (active) return
        active = true; generation++; tracker.reset()
        handler.postDelayed(sample, 10_000)
        if (job == null) job = scope.launch { while (isActive) { scan(); delay(3_000) } }
    }
    fun pause() { active = false; generation++; handler.removeCallbacks(sample); tracker.reset() }
    fun close() {
        pause(); job?.cancel()
        // Native có thể log lỗi ngay khi thoát; scan cuối có giới hạn 700ms, trước end/kill.
        scan(); scope.cancel()
    }
    @Synchronized private fun scan() {
        val patterns = runCatching { AppGraph.config.current.engines.firstOrNull { it.system == engine }?.errorPatterns }.getOrNull().orEmpty()
        if (patterns.isEmpty()) return
        val hits = EngineSignals.matched(readLog(), patterns).filter { seen.add(it) }
        if (hits.isNotEmpty()) Diagnostics.recordEngineSignal(activity, engine, "lỗi script/log engine", hits)
    }
    private fun readLog(): List<String> = runCatching {
        val process = ProcessBuilder("logcat", "-d", "-v", "threadtime", "--pid", Process.myPid().toString(), "-t", "400")
            .redirectErrorStream(true).start()
        val lines = java.util.Collections.synchronizedList(mutableListOf<String>())
        val reader = Thread { runCatching { process.inputStream.bufferedReader().useLines { it.forEach { line -> lines.add(line.take(300)) } } } }
        reader.isDaemon = true; reader.start()
        try { if (!process.waitFor(500, TimeUnit.MILLISECONDS)) process.destroyForcibly(); reader.join(200) }
        finally { process.destroy(); runCatching { process.inputStream.close() } }
        synchronized(lines) { lines.toList() }
    }.getOrDefault(emptyList())

    private fun capture(token: Int) {
        if (!active || token != generation || activity.isFinishing) return
        val view = surface()
        if (view == null || !view.holder.surface.isValid || view.width <= 0 || view.height <= 0) { next(null); return }
        val bitmap = Bitmap.createBitmap(64, 36, Bitmap.Config.ARGB_8888)
        runCatching {
            PixelCopy.request(view, bitmap, { result ->
                try {
                    if (active && token == generation) {
                        if (result == PixelCopy.SUCCESS) {
                            val pixels = IntArray(64 * 36); bitmap.getPixels(pixels, 0, 64, 0, 0, 64, 36)
                            next(EngineSignals.isBlack(pixels))
                        } else next(null)
                    }
                } finally { bitmap.recycle() }
            }, handler)
        }.onFailure { bitmap.recycle(); next(null) }
    }
    private fun next(black: Boolean?) {
        if (!active) return
        if (black != null) Diagnostics.crumb(activity, "pixel-copy", "$engine: black=$black grid=64x36")
        if (tracker.observe(black, SystemClock.elapsedRealtime())) {
            Diagnostics.crumb(activity, "black-frame", "$engine: 64x36 điểm RGB<=12 liên tục >=30s")
            scope.launch { Diagnostics.recordEngineSignal(activity, engine, "màn đen", listOf("64x36 điểm RGB<=12 liên tục >=30s; không đính ảnh")) }
        }
        if (black == null) Diagnostics.crumb(activity, "pixel-copy", "$engine: chưa đọc được frame")
        handler.postDelayed(sample, 10_000)
    }
}
