package vn.aow.monika.runner

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.renpy.android.PythonSDLActivity
import vn.aow.monika.diag.Diagnostics
import vn.aow.monika.pack.PackManager
import java.io.File

/**
 * Ren'Py 8 chạy ngay trong Monika (tiến trình ":game"). Java (RAPT 8.5.3, SDL 2.0.20) nằm trong module :renpy; `librenpython.so`
 * (gồm SDL, Python 3.12, pygame_sdl2…) cùng thư mục `private/` (main.py, renpy/, lib/) là GÓI tải thêm [PackManager.RENPY8],
 * nằm ở filesDir/packs/renpy8. Không giải nén gì từ APK (bản vá `0002` của :renpy): Monika trỏ biến môi trường vào gói.
 * Bảng biến: docs/opus/2026-10-03-nhung-renpy-rgss.md mục P3.
 */
class RenpyGameActivity : PythonSDLActivity() {
    private var clock: PlayClock? = null
    private var host: ComposeHost? = null
    private var menuOpen by mutableStateOf(false)

    private fun packDir() = File(filesDir, "packs/${PackManager.RENPY8}")
    private fun libFile() = File(packDir(), PackManager.RENPY8_LIB)
    private fun gameBase() = File(intent.getStringExtra(EXTRA_GAME_BASE).orEmpty())

    /** Thay nạp mặc định (System.loadLibrary từ APK) bằng nạp file trong gói tải thêm. */
    override fun loadLibraries() {
        val lib = libFile()
        if (!lib.isFile) throw UnsatisfiedLinkError("gói renpy8 chưa có ${lib.path}")
        System.load(lib.absolutePath)
        Log.i("MonikaGame", "renpy-lib-loaded") // CI (scripts/ci-emulator-games.sh) đợi dòng này
        Diagnostics.stage(this, "lib-loaded")
    }

    override fun getMainSharedObject(): String = libFile().absolutePath

    /** Không giải nén `private` từ APK: trỏ thẳng vào gói. [MONIKA_GAME_BASE]/[MONIKA_SAVE_DIR] được `private/environment.txt` của gói đọc. */
    override fun setupPythonEnvironment(externalStorage: File, oldExternalStorage: File) {
        val priv = File(packDir(), "private")
        val base = gameBase()
        val id = Integer.toHexString(base.absolutePath.hashCode())
        val pub = File(filesDir, "renpy-games/$id").apply { mkdirs() }
        val saves = File(pub, "saves").apply { mkdirs() }
        nativeSetEnv("ANDROID_PRIVATE", priv.absolutePath)
        nativeSetEnv("ANDROID_PUBLIC", pub.absolutePath)
        nativeSetEnv("ANDROID_OLD_PUBLIC", pub.absolutePath)
        nativeSetEnv("ANDROID_APK", File(packDir(), "empty.zip").absolutePath)
        nativeSetEnv("MONIKA_GAME_BASE", base.absolutePath)
        nativeSetEnv("MONIKA_SAVE_DIR", saves.absolutePath)
        Log.i("MonikaGame", "renpy game_base=${base.absolutePath} private=${priv.absolutePath}")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val manifest = File(packDir(), "manifest.json").takeIf { it.isFile }?.readText().orEmpty()
        Diagnostics.begin(this, "renpy", "renpy8", manifest, title, "Ren'Py")
        clock = PlayClock(intent.getStringExtra(PlayClock.EXTRA_KEY))
        super.onCreate(savedInstanceState)
        Diagnostics.stage(this, "created")
        // SDL không phải ComponentActivity nên ComposeHost tự cấp vòng đời (như Kirikiri/RGSS).
        host = ComposeHost(this).also { h ->
            h.attach(org.libsdl.app.SDLActivity.getContentView() as android.view.ViewGroup) {
                RenpyOverlay(title.ifBlank { "Ren'Py" }, menuOpen, { menuOpen = it }, onExit = { finish() })
            }
        }
    }

    override fun onResume() { super.onResume(); host?.resume(); clock?.resume(); Diagnostics.stage(this, "playing") }
    override fun onPause() { host?.pause(); clock?.pause(); Diagnostics.heartbeat(this); super.onPause() }

    override fun onDestroy() {
        host?.destroy()
        super.onDestroy()
        Diagnostics.end(this) // thoát bình thường → không tạo báo cáo "chết bất thường"
        // Python không khởi tạo lại được trong cùng tiến trình → kết thúc tiến trình khi thoát.
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    companion object {
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_GAME_BASE = "game_base"

        /** [entry] = thư mục game, thư mục `game/`, hoặc một file trong cây game; không thấy `game/` thì không mở. */
        fun start(activity: Activity, entry: File?, title: String, key: String?) {
            val base = RenpyBase.resolve(entry) ?: return
            activity.startActivity(
                Intent(activity, RenpyGameActivity::class.java)
                    .putExtra(PlayClock.EXTRA_KEY, key).putExtra(EXTRA_TITLE, title).putExtra(EXTRA_GAME_BASE, base.absolutePath)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
        }
    }
}
