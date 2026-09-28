package vn.aow.monika.runner

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import kotlinx.coroutines.launch
import vn.aow.monika.AppGraph
import java.io.File

/** Màn hình chơi game giả lập (LibretroDroid + lõi libretro tải theo cấu hình). */
class RetroActivity : ComponentActivity() {
    private var retroView: GLRetroView? = null
    private lateinit var sramFile: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val status = TextView(this).apply {
            text = "Đang chuẩn bị…"
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            textSize = 16f
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(status, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }
        setContentView(root)

        val coreId = intent.getStringExtra(EXTRA_CORE) ?: return finish()
        val gamePath = intent.getStringExtra(EXTRA_GAME) ?: return finish()
        val saves = File(filesDir, "saves").apply { mkdirs() }
        sramFile = File(saves, "${File(gamePath).nameWithoutExtension}-${gamePath.hashCode()}.srm")

        lifecycleScope.launch {
            val core = runCatching { AppGraph.cores.ensureCore(coreId) { status.text = it } }
                .getOrElse {
                    status.text = "Không tải được lõi giả lập.\n${it.message}\n\nKiểm tra mạng rồi mở lại game."
                    return@launch
                }
            val data = GLRetroViewData(this@RetroActivity).apply {
                coreFilePath = core.absolutePath
                gameFilePath = gamePath
                systemDirectory = AppGraph.cores.systemDir().absolutePath
                savesDirectory = saves.absolutePath
                saveRAMState = sramFile.takeIf { it.exists() }?.readBytes()
            }
            val view = GLRetroView(this@RetroActivity, data)
            lifecycle.addObserver(view)
            root.removeView(status)
            root.addView(view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            root.addView(
                VirtualPad(this@RetroActivity) { action, key -> view.sendKeyEvent(action, key) },
                FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            )
            retroView = view
            launch {
                view.getGLRetroErrors().collect { code ->
                    root.addView(TextView(this@RetroActivity).apply {
                        text = "Lỗi chạy game (mã $code). Có thể file game hỏng hoặc lõi không hợp."
                        setTextColor(Color.WHITE)
                        setBackgroundColor(0xCC000000.toInt())
                        gravity = Gravity.CENTER
                    }, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                }
            }
        }
    }

    /** Chuyển phím từ tay cầm thật vào game. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val view = retroView
        val fromGamepad = event.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            event.source and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD
        if (view != null && fromGamepad && event.keyCode != KeyEvent.KEYCODE_BACK) {
            view.sendKeyEvent(event.action, event.keyCode)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onPause() {
        saveSram()
        super.onPause()
    }

    private fun saveSram() {
        val view = retroView ?: return
        runCatching {
            val bytes = view.serializeSRAM()
            if (bytes.isNotEmpty()) sramFile.writeBytes(bytes)
        }
    }

    companion object {
        private const val EXTRA_CORE = "core"
        private const val EXTRA_GAME = "game"

        fun start(activity: Activity, coreId: String, game: File) {
            activity.startActivity(
                Intent(activity, RetroActivity::class.java)
                    .putExtra(EXTRA_CORE, coreId)
                    .putExtra(EXTRA_GAME, game.absolutePath)
            )
        }
    }
}
