package vn.aow.monika.runner

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.tvp.kirikiri2.KR2Activity
import vn.aow.monika.diag.Diagnostics
import java.io.File

/**
 * Kirikiri (Kirikiroid2Yuri) chạy ngay trong Monika, tiến trình ":game" như các giả lập khác.
 * Mã Java nằm trong module :kirikiri; thư viện native libkrkr2yuri.so + tài nguyên (ui, font) là GÓI tải thêm
 * ([vn.aow.monika.pack.PackManager.KIRIKIRI]) nên APK vẫn nhẹ. Cho phép mở thẳng game theo Intent (bản vá "aow_game_path").
 */
class KirikiriGameActivity : KR2Activity() {
    private var clock: PlayClock? = null
    private var host: ComposeHost? = null
    private var menuOpen by androidx.compose.runtime.mutableStateOf(false)
    private var fastForward by androidx.compose.runtime.mutableStateOf(false)

    override fun get_res_sd_operate_step(): Int = vn.aow.monika.kirikiri.R.drawable.sd_operate_step

    private val name: String get() = intent.getStringExtra(EXTRA_TITLE).orEmpty()

    private fun packDir() = File(filesDir, "packs/${vn.aow.monika.pack.PackManager.KIRIKIRI}")

    /** Thay cách nạp lõi gốc (System.loadLibrary từ APK) bằng nạp file trong gói đã tải. */
    override fun onLoadNativeLibraries() {
        val lib = File(packDir(), vn.aow.monika.pack.PackManager.KIRIKIRI_LIB)
        try {
            System.load(lib.absolutePath)
            Diagnostics.stage(this, "lib-loaded")
            android.util.Log.i("MonikaGame", "kirikiri-lib-loaded") // CI (scripts/ci-emulator-games.sh) đợi dòng này
        } catch (t: Throwable) {
            android.util.Log.e("MonikaGame", "error kirikiri-lib: $t")
            Diagnostics.recordHandled(this, "engine:kirikiri", "không nạp được ${lib.name}", t)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        sAssetsRoot = File(packDir(), "assets").absolutePath
        Diagnostics.begin(this, "kirikiri", "kirikiroid2-yuri", File(packDir(), "manifest.json").takeIf { it.isFile }?.readText().orEmpty(), name, "Kirikiri")
        clock = PlayClock(intent.getStringExtra(PlayClock.EXTRA_KEY))
        super.onCreate(savedInstanceState)
        Diagnostics.stage(this, "created")
        // Giao diện Monika đè lên game: nút menu + menu popup tiếng Việt (Back của máy cũng mở menu).
        host = ComposeHost(this).also { h ->
            h.attach(mFrameLayout) {
                KirikiriOverlay(
                    title = name.ifBlank { "Kirikiri" }, open = menuOpen, fastForward = fastForward,
                    onOpen = { menuOpen = it },
                    onGameMenu = { tapKey(android.view.KeyEvent.KEYCODE_MENU) },
                    onFastForward = { applyFastForward(!fastForward) },
                    onExit = { finish() },
                )
            }
        }
    }

    private fun tapKey(code: Int) { nativeKeyAction(code, true); nativeKeyAction(code, false) }

    /** Tua nhanh = giữ phím Ctrl (cách Kirikiri bỏ qua thoại đã đọc). */
    private fun applyFastForward(on: Boolean) {
        fastForward = on
        nativeKeyAction(android.view.KeyEvent.KEYCODE_CTRL_LEFT, on)
    }

    // Back/Menu của máy → menu Monika thay vì menu cocos gốc.
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.keyCode == android.view.KeyEvent.KEYCODE_BACK) {
            if (event.action == android.view.KeyEvent.ACTION_UP) menuOpen = !menuOpen
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onStart() { super.onStart(); host?.pause() }
    override fun onResume() { super.onResume(); host?.resume(); clock?.resume(); Diagnostics.stage(this, "playing") }
    override fun onPause() {
        if (fastForward) applyFastForward(false)
        host?.pause(); clock?.pause(); Diagnostics.heartbeat(this); super.onPause()
    }

    override fun onDestroy() {
        host?.destroy()
        Diagnostics.end(this) // thoát bình thường → không tạo báo cáo "chết bất thường"
        super.onDestroy() // KR2Activity gọi System.exit(0)
    }

    companion object {
        private const val EXTRA_TITLE = "title"

        /** [entry] = file game (data.xp3, *.xp3, startup.tjs) → mở thẳng; null → vào trình duyệt file của Kirikiri. */
        fun start(activity: Activity, entry: File?, title: String, key: String?) {
            val i = Intent(activity, KirikiriGameActivity::class.java)
                .putExtra(PlayClock.EXTRA_KEY, key)
                .putExtra(EXTRA_TITLE, title)
                // KR2Activity (cocos2d) tự thoát nếu không phải activity gốc của task → mở trong task riêng.
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            entry?.let { i.putExtra("aow_game_path", it.absolutePath) }
            activity.startActivity(i)
        }
    }
}
