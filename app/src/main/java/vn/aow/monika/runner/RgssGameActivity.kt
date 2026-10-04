package vn.aow.monika.runner

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.json.JSONObject
import vn.aow.monika.diag.Diagnostics
import vn.aow.monika.pack.PackManager
import vn.aow.monika.rgss.sdl.SDLActivity
import java.io.File
import java.util.Locale

/**
 * RPG Maker XP/VX/VX Ace chạy bằng mkxp-z ngay trong Monika (tiến trình ":game"). Java của SDL 2.26.3 nằm trong module :rgss
 * (gói đổi thành vn.aow.monika.rgss.sdl); thư viện native là GÓI tải thêm [PackManager.RGSS] (libSDL2.so … libmkxp-z.so),
 * nạp từ filesDir/packs/rgss/lib/ theo `manifest.json`. Viết mới hoàn toàn, không dùng vỏ Java của bản port.
 * Hợp đồng JNI của mkxp-z (tìm theo lớp của Activity): trường tĩnh GAME_PATH + hàm tĩnh getSystemLanguage, hasVibrator, vibrate,
 * vibrateStop, inMultiWindow — xem docs/opus/2026-10-03-nhung-renpy-rgss.md mục 3.3.
 */
class RgssGameActivity : SDLActivity() {
    private var engineWatch: vn.aow.monika.diag.EngineWatch? = null
    private var clock: PlayClock? = null
    private var host: ComposeHost? = null
    private val ui = InGameState()
    private var shiftHeld by androidx.compose.runtime.mutableStateOf(false)

    private fun packDir() = File(filesDir, "packs/${PackManager.RGSS}")

    /** Tên thư viện theo thứ tự `loadOrder` trong manifest.json của gói (lib…so); lỗi/thiếu → mặc định như bản port. */
    private fun libOrder(): List<String> = runCatching {
        val a = JSONObject(File(packDir(), "manifest.json").readText()).getJSONArray("loadOrder")
        (0 until a.length()).map { a.getString(it) }
    }.getOrNull()?.takeIf { it.isNotEmpty() } ?: DEFAULT_ORDER

    override fun getLibraries(): Array<String> = libOrder().map { it.removePrefix("lib").removeSuffix(".so") }.toTypedArray()

    override fun getMainSharedObject(): String = File(packDir(), "lib/" + libOrder().last()).absolutePath

    /**
     * Thay cách nạp mặc định (System.loadLibrary từ APK) bằng nạp file trong gói tải thêm. Có phụ thuộc lẫn nhau (libc++_shared, openal…)
     * và không biết trước thứ tự → thử lặp phần còn lại tới khi nạp hết hoặc không tiến thêm (như AzaharModule).
     */
    override fun loadLibraries() {
        val libDir = File(packDir(), "lib")
        val all = (libDir.listFiles { f -> f.name.endsWith(".so") }?.map { it.name } ?: emptyList())
        if (all.isEmpty()) throw UnsatisfiedLinkError("gói rgss chưa có thư viện ở ${libDir.path}")
        val order = libOrder()
        var pending = (order.filter { it in all } + all.filter { it !in order }).toMutableList()
        var lastError: Throwable? = null
        while (pending.isNotEmpty()) {
            val next = ArrayList<String>()
            for (n in pending) try { System.load(File(libDir, n).absolutePath) } catch (t: UnsatisfiedLinkError) { next += n; lastError = t }
            if (next.size == pending.size) break // không tiến thêm
            pending = next
        }
        if (pending.isNotEmpty()) throw UnsatisfiedLinkError("không nạp được ${pending.joinToString()}: $lastError")
        Log.i("MonikaGame", "rgss-lib-loaded") // CI (scripts/ci-emulator-games.sh) đợi dòng này
        Diagnostics.stage(this, "lib-loaded")
    }

    override fun getArguments(): Array<String> = emptyArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        GAME_PATH = intent.getStringExtra(EXTRA_GAME_PATH).orEmpty() // PHẢI đặt trước super.onCreate: luồng native đọc trường này
        Log.i("MonikaGame", "rgss game_path=$GAME_PATH")
        // mkxp.json do Monika sinh (phiên bản RGSS, RTP…), giữ khóa của game; thêm preloadScript = bản giả Win32API (assets/rgss/monika-win32api.rb,
        // chỉ thay khi MiniFFI gốc lỗi với DLL Windows). Thư mục chỉ-đọc → bỏ qua, mkxp-z tự đoán.
        runCatching {
            File(GAME_PATH).takeIf { it.isDirectory }?.let {
                val preloads = RgssCompat.preloads(this, it)
                MkxpConfigWriter.write(it, emptyList(), preload = preloads, removePreload = RgssCompat.inactivePreloads(this, preloads))
            }
        }
        val manifest = File(packDir(), "manifest.json").takeIf { it.isFile }?.readText().orEmpty()
        Diagnostics.begin(this, "rgss", "mkxp-z", manifest, intent.getStringExtra(EXTRA_TITLE).orEmpty(), "RPG Maker XP/VX/Ace")
        clock = PlayClock(intent.getStringExtra(PlayClock.EXTRA_KEY))
        super.onCreate(savedInstanceState)
        Diagnostics.stage(this, "created")
        engineWatch = vn.aow.monika.diag.EngineWatch(this, "rgss") { mSurface }
        ui.opacity = vn.aow.monika.AppGraph.prefs.padOpacity
        // Phím ảo + menu Monika đè lên màn game (SDL không phải ComponentActivity nên ComposeHost tự cấp vòng đời, như Kirikiri).
        host = ComposeHost(this).also { h ->
            h.attach(SDLActivity.getContentView() as android.view.ViewGroup) {
                RgssOverlay(
                    state = ui, title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "RPG Maker" }, shiftHeld = shiftHeld,
                    onSend = { action, key -> sendKey(action, rgssKey(key)) },
                    onShift = { setShift(!shiftHeld) },
                    onOpacity = {
                        ui.opacity = when { ui.opacity < 0.4f -> 0.65f; ui.opacity < 0.9f -> 1f; else -> 0.3f }
                        vn.aow.monika.AppGraph.prefs.padOpacity = ui.opacity
                    },
                    onExit = { finish() },
                )
            }
        }
    }

    private fun sendKey(action: Int, key: Int) {
        if (action == android.view.KeyEvent.ACTION_DOWN) SDLActivity.onNativeKeyDown(key) else SDLActivity.onNativeKeyUp(key)
    }

    /** "Chạy nhanh" = giữ Shift (nút A của RGSS: chạy/đi nhanh). */
    private fun setShift(on: Boolean) {
        shiftHeld = on
        sendKey(if (on) android.view.KeyEvent.ACTION_DOWN else android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_SHIFT_LEFT)
    }

    // Back của máy → menu Monika (SDL gốc sẽ gửi phím Back vào game).
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.keyCode == android.view.KeyEvent.KEYCODE_BACK) {
            if (event.action == android.view.KeyEvent.ACTION_UP) ui.menuOpen = !ui.menuOpen
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() { super.onResume(); host?.resume(); clock?.resume(); engineWatch?.resume(); Diagnostics.stage(this, "playing") }
    override fun onPause() { if (shiftHeld) setShift(false); host?.pause(); clock?.pause(); engineWatch?.pause(); Diagnostics.heartbeat(this); super.onPause() }

    override fun onDestroy() {
        engineWatch?.pause()
        host?.destroy()
        super.onDestroy()
        engineWatch?.close()
        Diagnostics.end(this) // thoát bình thường → không tạo báo cáo "chết bất thường"
        // Ruby không khởi tạo lại được trong cùng tiến trình (mkxp-z) → kết thúc tiến trình khi thoát.
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    companion object {
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_GAME_PATH = "game_path"
        private val DEFAULT_ORDER = listOf("libSDL2.so", "libSDL2_image.so", "libSDL2_ttf.so", "libSDL2_sound.so", "libopenal.so", "libruby.so", "libmkxp-z.so")

        /** Đọc bởi mã native (JNI: GetStaticFieldID trên lớp của Activity). */
        @JvmField var GAME_PATH: String = ""

        /** [entry] = thư mục game, hoặc một file trong đó (Game.ini, Game.rgss3a…). */
        fun start(activity: Activity, entry: File?, title: String, key: String?) {
            val dir = entry?.let { if (it.isDirectory) it else it.parentFile } ?: return
            // mkxp.json (+ bản giả Win32API) do onCreate của Activity ghi, để mọi đường mở game đều có.
            activity.startActivity(
                Intent(activity, RgssGameActivity::class.java)
                    .putExtra(PlayClock.EXTRA_KEY, key).putExtra(EXTRA_TITLE, title).putExtra(EXTRA_GAME_PATH, dir.absolutePath)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
        }

        // ---- Hợp đồng JNI của mkxp-z (tên + chữ ký phải giữ nguyên) ----
        @JvmStatic fun getSystemLanguage(): String = Locale.getDefault().toString()

        @JvmStatic fun hasVibrator(): Boolean = vib()?.hasVibrator() == true

        @JvmStatic fun vibrate(duration: Int) {
            val v = vib() ?: return
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(duration.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
            else @Suppress("DEPRECATION") v.vibrate(duration.toLong())
        }

        @JvmStatic fun vibrateStop() { vib()?.cancel() }

        @JvmStatic fun inMultiWindow(activity: Activity): Boolean = Build.VERSION.SDK_INT >= 24 && activity.isInMultiWindowMode

        private fun vib(): Vibrator? = runCatching { SDLActivity.getContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator }.getOrNull()
    }
}
