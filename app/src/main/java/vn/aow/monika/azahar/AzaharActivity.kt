package vn.aow.monika.azahar

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.Choreographer
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.citra.citra_emu.NativeLibrary
import vn.aow.monika.AppGraph
import vn.aow.monika.diag.Diagnostics
import vn.aow.monika.runner.CoreOptions
import vn.aow.monika.runner.InGameOverlay
import vn.aow.monika.runner.InGameState
import vn.aow.monika.runner.PadLayout
import vn.aow.monika.runner.PlayClock
import vn.aow.monika.runner.RetroActivity
import vn.aow.monika.runner.STICK_RIGHT
import vn.aow.monika.ui.theme.MonikaTheme
import java.io.File

/**
 * Màn chơi 3DS bằng engine Azahar nhúng. Giao diện (tay cầm, menu popup dưới đáy, tùy chọn, hộp thoại)
 * là của Monika — dùng chung [InGameOverlay] với mọi giả lập. Lưu game nằm trong thư mục do Monika quản lý.
 * Engine chỉ là thư viện native tải khi cần ([AzaharModule]); nạp lỗi thì tự chuyển sang lõi Citra libretro.
 */
class AzaharActivity : ComponentActivity(), SurfaceHolder.Callback, Choreographer.FrameCallback {
    private val ui = InGameState()
    private lateinit var root: FrameLayout
    private lateinit var surface: SurfaceView
    private val cheats by lazy { AzaharCheats(this) }
    private val cheatPicker = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { u -> if (u != null) cheats.importFile(u) }
    private var ready by mutableStateOf(false)
    private var surfaceOk: Boolean = false
    private var started = false
    private var paused = false
    private var firstFrame = false
    private var clock: PlayClock? = null
    private lateinit var gamePath: String
    private lateinit var title: String
    private lateinit var systemName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (android.os.Build.VERSION.SDK_INT >= 28) window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        clock = PlayClock(intent.getStringExtra(PlayClock.EXTRA_KEY))
        gamePath = intent.getStringExtra(EXTRA_GAME) ?: return finish()
        systemName = intent.getStringExtra(EXTRA_SYSTEM).orEmpty()
        title = intent.getStringExtra(EXTRA_TITLE) ?: File(gamePath).nameWithoutExtension
        val module = AppGraph.azahar
        Diagnostics.begin(this, "azahar", "azahar", module.info(), File(gamePath).name, systemName)
        AzaharBridge.portrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

        val prefs = AppGraph.prefs
        ui.opacity = prefs.padOpacity
        ui.scale = prefs.padScale
        ui.dpadOffset = prefs.padOffset("dpad").let { (x, y) -> androidx.compose.ui.geometry.Offset(x, y) }
        ui.faceOffset = prefs.padOffset("face").let { (x, y) -> androidx.compose.ui.geometry.Offset(x, y) }

        val status = TextView(this).apply {
            text = "Đang chuẩn bị…"; setTextColor(Color.parseColor("#C8C5CB")); gravity = Gravity.CENTER; textSize = 14f
        }
        val loading = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#141315"))
            addView(TextView(this@AzaharActivity).apply {
                text = title; setTextColor(Color.WHITE); textSize = 18f; gravity = Gravity.CENTER
                setTypeface(typeface, android.graphics.Typeface.BOLD); setPadding(48, 0, 48, 24)
            })
            addView(android.widget.ProgressBar(this@AzaharActivity).apply {
                indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FF7A32"))
            })
            addView(status.apply { setPadding(48, 24, 48, 0) })
        }
        surface = SurfaceView(this).apply {
            holder.addCallback(this@AzaharActivity)
            setOnTouchListener { _, e -> touch(e); true }
        }
        root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#141315"))
            addView(surface, gameLayoutParams())
            addView(loading, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }
        val overlay = ComposeView(this).apply {
            setContent {
                MonikaTheme {
                    Box(Modifier.fillMaxSize()) {
                        InGameOverlay(
                            state = ui, system = systemName, title = title, layout = PadLayout.N3DS, showPad = ready,
                            send = { action, k -> key(action, k) },
                            onBack = { finish() },
                            onSave = { saveState() },
                            onLoad = { loadState() },
                            onTurbo = {
                                ui.turbo = !ui.turbo
                                if (ui.turbo) NativeLibrary.setTemporaryFrameLimit(2.0) else NativeLibrary.disableTemporaryFrameLimit()
                            },
                            onOpacity = {
                                ui.opacity = when { ui.opacity < 0.4f -> 0.65f; ui.opacity < 0.9f -> 1f; else -> 0.3f }
                                AppGraph.prefs.padOpacity = ui.opacity
                            },
                            onEditDone = {
                                prefs.padScale = ui.scale
                                prefs.setPadOffset("dpad", ui.dpadOffset.x, ui.dpadOffset.y)
                                prefs.setPadOffset("face", ui.faceOffset.x, ui.faceOffset.y)
                                showToast("Đã lưu vị trí phím")
                            },
                            onOptions = { ui.options = AzaharConfig.options(this@AzaharActivity) },
                            onAsk = { askGroup() },
                            extraActions = listOf(
                                vn.aow.monika.ui.theme.SheetAction("Mã cheat", vn.aow.monika.R.drawable.ic_fluent_document_24_regular) { cheats.show() },
                            ),
                            onMotion = { src, x, y -> stick(if (src == STICK_RIGHT) NativeLibrary.ButtonType.STICK_C else NativeLibrary.ButtonType.STICK_LEFT, x, y) },
                            onOptionChange = { o, v ->
                                CoreOptions.save(this@AzaharActivity, AzaharConfig.CORE_ID, o.key, v)
                                ui.options = ui.options?.map { if (it.key == o.key) it.copy(value = v) else it }
                                AzaharConfig.write(this@AzaharActivity)
                                runCatching { NativeLibrary.reloadSettings() }
                            },
                            onOptionsReset = {
                                CoreOptions.reset(this@AzaharActivity, AzaharConfig.CORE_ID)
                                ui.options = null
                                AzaharConfig.write(this@AzaharActivity)
                                runCatching { NativeLibrary.reloadSettings() }
                                showToast("Đã về mặc định. Mở lại game để áp dụng hết.", 2600)
                            },
                        )
                        AzaharCheatsSheet(cheats) { cheatPicker.launch(arrayOf("text/plain", "application/octet-stream")) }
                        AzaharDialogs(AzaharBridge.dialog)
                    }
                }
            }
        }
        setContentView(root)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            surface.layoutParams = gameLayoutParams()
            androidx.core.view.ViewCompat.onApplyWindowInsets(v, insets)
        }
        root.addView(overlay, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        AzaharBridge.onExit = { runOnUiThread { finish() } }
        AzaharBridge.onError = { Diagnostics.stage(this, "core-error: ${it.take(120)}") }
        AzaharBridge.onFirstFrame = { markFirstFrame(loading) }

        lifecycleScope.launch {
            val ok = runCatching {
                module.ensure { status.text = it }
                Diagnostics.stage(this@AzaharActivity, "module-ready")
                status.text = "Đang nạp engine 3DS…"
                Diagnostics.stage(this@AzaharActivity, "native-loading") // chết ở đây = thư viện native không khớp máy
                withContext(Dispatchers.Default) { module.load() }
                Diagnostics.stage(this@AzaharActivity, "native-loaded")
                setUp()
                Diagnostics.stage(this@AzaharActivity, "initialized")
            }
            ok.onFailure { e ->
                Diagnostics.stage(this@AzaharActivity, "engine-failed: ${e.javaClass.simpleName}: ${e.message?.take(100)}")
                fallback(status, e)
                return@launch
            }
            ready = true
            startIfPossible()
            launch { while (true) { delay(15_000); Diagnostics.heartbeat(this@AzaharActivity) } }
            launch {
                delay(20_000)
                if (loading.parent != null && !firstFrame) status.text = "Game nặng, đang nạp… (lần đầu dựng shader có thể lâu)"
            }
            launch {
                AzaharBridge.let { b -> while (loading.parent != null) { b.shader?.let { status.text = it }; delay(300) } }
            }
        }
    }

    /** Engine không nạp được → chơi bằng lõi Citra libretro (giao diện vẫn của Monika). */
    private fun fallback(status: TextView, e: Throwable) {
        val core = "citra"
        status.text = "Engine 3DS chưa chạy được trên máy này (${e.javaClass.simpleName}). Chuyển sang lõi dự phòng…"
        lifecycleScope.launch {
            delay(1500)
            Diagnostics.end(this@AzaharActivity)
            RetroActivity.start(this@AzaharActivity, core, File(gamePath), systemName, title, "n3ds", intent.getStringExtra(PlayClock.EXTRA_KEY), "3ds")
            finish()
        }
    }

    private fun setUp() {
        val dir = AzaharConfig.userDir(this)
        AzaharBridge.userDir = dir.absolutePath + "/"
        NativeLibrary.setUserDirectory(dir.absolutePath)
        NativeLibrary.createLogFile()
        NativeLibrary.createConfigFile()
        AzaharConfig.write(this)
        NativeLibrary.reloadSettings()
        val redirect = File(dir, "gpu/vk_file_redirect").apply { mkdirs() }
        val drivers = File(filesDir, "gpu_driver").apply { mkdirs() }
        val moduleDir = File(filesDir, "engines/azahar").absolutePath + "/"
        runCatching { NativeLibrary.initializeGpuDriver(moduleDir, drivers.absolutePath + "/", "", redirect.absolutePath + "/") }
        refreshSlots()
    }

    private fun gameLayoutParams(): FrameLayout.LayoutParams {
        val dm = resources.displayMetrics
        val portrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        if (!portrait) return FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        val cutoutTop = if (::root.isInitialized) androidx.core.view.ViewCompat.getRootWindowInsets(root)
            ?.getInsets(WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.statusBars())?.top ?: 0 else 0
        val top = (76 * dm.density).toInt() + cutoutTop
        val aspect = AppGraph.config.current.cores["citra"]?.aspectRatio ?: (400f / 480f)
        val h = (dm.widthPixels / aspect).toInt().coerceAtMost((dm.heightPixels * 0.58f).toInt())
        return FrameLayout.LayoutParams(MATCH_PARENT, h, Gravity.TOP).apply { topMargin = top }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val portrait = newConfig.orientation == Configuration.ORIENTATION_PORTRAIT
        AzaharBridge.portrait = portrait
        surface.layoutParams = gameLayoutParams()
        if (started) runCatching { NativeLibrary.updateFramebuffer(portrait) }
    }

    // ---- Vòng đời màn vẽ / luồng giả lập ----

    override fun surfaceCreated(holder: SurfaceHolder) {}

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        surfaceOk = true
        if (ready) { NativeLibrary.surfaceChanged(holder.surface); startIfPossible() }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceOk = false
        if (started) runCatching { NativeLibrary.surfaceDestroyed() }
    }

    private fun startIfPossible() {
        if (!surfaceOk || !ready) return
        NativeLibrary.surfaceChanged(surface.holder.surface)
        Diagnostics.stage(this, "surface")
        if (!started) {
            started = true
            Thread({
                Diagnostics.stage(this, "run-started") // native chạy game từ đây; chết sau dòng này = lõi/game
                NativeLibrary.run(gamePath)
                runOnUiThread { finish() }
            }, "NativeEmulation").start()
            Choreographer.getInstance().postFrameCallback(this)
            // Không có tín hiệu "nạp shader xong" (bộ đệm rỗng) → coi là có hình sau khi engine báo đang chạy.
            lifecycleScope.launch {
                repeat(120) { if (!firstFrame && runCatching { NativeLibrary.isRunning() }.getOrDefault(false)) { delay(1500); markFirstFrame(null); return@launch }; delay(500) }
            }
        } else if (paused) resumeEmu()
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (started && !paused) runCatching { NativeLibrary.doFrame() }
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun markFirstFrame(loading: android.view.View?) {
        if (firstFrame) return
        firstFrame = true
        Diagnostics.stage(this, "first-frame")
        val v = loading ?: root.getChildAt(1)
        if (v != null && v !== surface) v.animate().alpha(0f).setDuration(180).withEndAction { root.removeView(v) }.start()
    }

    private fun resumeEmu() { paused = false; runCatching { NativeLibrary.unPauseEmulation() } }

    override fun onResume() {
        super.onResume()
        clock?.resume()
        if (started && paused && surfaceOk) resumeEmu()
    }

    override fun onPause() {
        clock?.pause()
        if (started) { paused = true; runCatching { NativeLibrary.pauseEmulation() } }
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        Choreographer.getInstance().removeFrameCallback(this)
        AzaharBridge.onExit = {}; AzaharBridge.onFirstFrame = {}; AzaharBridge.onError = {}
        if (isFinishing) {
            Diagnostics.end(this)
            if (started) runCatching { NativeLibrary.stopEmulation() }
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    // ---- Nhập liệu ----

    private fun key(action: Int, keyCode: Int): Boolean {
        val btn = when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> NativeLibrary.ButtonType.BUTTON_B // theo VỊ TRÍ: nút dưới = B của 3DS
            KeyEvent.KEYCODE_BUTTON_B -> NativeLibrary.ButtonType.BUTTON_A
            KeyEvent.KEYCODE_BUTTON_X -> NativeLibrary.ButtonType.BUTTON_Y
            KeyEvent.KEYCODE_BUTTON_Y -> NativeLibrary.ButtonType.BUTTON_X
            KeyEvent.KEYCODE_BUTTON_L1 -> NativeLibrary.ButtonType.TRIGGER_L
            KeyEvent.KEYCODE_BUTTON_R1 -> NativeLibrary.ButtonType.TRIGGER_R
            KeyEvent.KEYCODE_BUTTON_L2 -> NativeLibrary.ButtonType.BUTTON_ZL
            KeyEvent.KEYCODE_BUTTON_R2 -> NativeLibrary.ButtonType.BUTTON_ZR
            KeyEvent.KEYCODE_BUTTON_START -> NativeLibrary.ButtonType.BUTTON_START
            KeyEvent.KEYCODE_BUTTON_SELECT -> NativeLibrary.ButtonType.BUTTON_SELECT
            KeyEvent.KEYCODE_BUTTON_MODE -> NativeLibrary.ButtonType.BUTTON_HOME
            KeyEvent.KEYCODE_DPAD_UP -> NativeLibrary.ButtonType.DPAD_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> NativeLibrary.ButtonType.DPAD_DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> NativeLibrary.ButtonType.DPAD_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> NativeLibrary.ButtonType.DPAD_RIGHT
            else -> return false
        }
        if (!started) return true
        val state = if (action == KeyEvent.ACTION_DOWN) NativeLibrary.ButtonState.PRESSED else NativeLibrary.ButtonState.RELEASED
        return runCatching { NativeLibrary.onGamePadEvent(NativeLibrary.TOUCHSCREEN_DEVICE, btn, state) }.getOrDefault(false)
    }

    private fun stick(id: Int, x: Float, y: Float) {
        if (started) runCatching { NativeLibrary.onGamePadMoveEvent(NativeLibrary.TOUCHSCREEN_DEVICE, id, x, y) }
    }

    private fun touch(e: MotionEvent) {
        if (!started) return
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                NativeLibrary.onTouchEvent(e.getX(i), e.getY(i), true)
            }
            MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount) NativeLibrary.onTouchMoved(e.getX(i), e.getY(i))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> NativeLibrary.onTouchEvent(0f, 0f, false)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val fromPad = event.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            event.source and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD
        if (fromPad && event.keyCode != KeyEvent.KEYCODE_BACK && key(event.action, event.keyCode)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onGenericMotionEvent(e: MotionEvent): Boolean {
        if (e.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK && e.action == MotionEvent.ACTION_MOVE) {
            stick(NativeLibrary.ButtonType.STICK_LEFT, e.getAxisValue(MotionEvent.AXIS_X), e.getAxisValue(MotionEvent.AXIS_Y))
            stick(NativeLibrary.ButtonType.STICK_C, e.getAxisValue(MotionEvent.AXIS_Z), e.getAxisValue(MotionEvent.AXIS_RZ))
            return true
        }
        return super.onGenericMotionEvent(e)
    }

    // ---- Lưu / tải trạng thái: ô 0 = Tự lưu (quick save), 1–3 = ô của người chơi ----

    private fun refreshSlots() {
        ui.filledSlots = runCatching { NativeLibrary.getSavestateInfo()?.map { it.slot }?.filter { it in 0..3 }?.toSet() }.getOrNull().orEmpty()
    }

    private fun saveState() {
        ui.menuOpen = false
        val slot = ui.slot
        runCatching { NativeLibrary.saveState(slot) }
        lifecycleScope.launch { delay(900); refreshSlots(); showToast("Đã lưu vào ô $slot") }
    }

    private fun loadState() {
        ui.menuOpen = false
        val slot = ui.slot
        if (slot !in ui.filledSlots) { showToast("Ô $slot chưa có dữ liệu"); return }
        runCatching { NativeLibrary.loadState(slot) }
        showToast("Đã tải ô $slot")
    }

    private fun askGroup() {
        runCatching { NativeLibrary.saveState(0) }
        showToast("Đã tự lưu game")
        lifecycleScope.launch {
            val shot = capture()
            vn.aow.monika.community.AskGroup.ask(this@AzaharActivity, shot, title, systemName)
        }
    }

    private suspend fun capture(): android.graphics.Bitmap? =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            val v = surface
            if (v.width <= 0 || v.height <= 0) { cont.resumeWith(Result.success(null)); return@suspendCancellableCoroutine }
            val bmp = android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888)
            runCatching {
                android.view.PixelCopy.request(v, bmp, { r ->
                    if (cont.isActive) cont.resumeWith(Result.success(if (r == android.view.PixelCopy.SUCCESS) bmp else null))
                }, android.os.Handler(android.os.Looper.getMainLooper()))
            }.onFailure { if (cont.isActive) cont.resumeWith(Result.success(null)) }
        }

    private fun showToast(text: String, ms: Long = 1600) {
        ui.toast = text
        lifecycleScope.launch { delay(ms); if (ui.toast == text) ui.toast = null }
    }

    companion object {
        private const val EXTRA_GAME = "game"
        private const val EXTRA_SYSTEM = "system"
        private const val EXTRA_TITLE = "title"

        fun start(activity: Activity, game: File, system: String, title: String, key: String?) {
            activity.startActivity(
                Intent(activity, AzaharActivity::class.java)
                    .putExtra(PlayClock.EXTRA_KEY, key)
                    .putExtra(EXTRA_GAME, game.absolutePath)
                    .putExtra(EXTRA_SYSTEM, system)
                    .putExtra(EXTRA_TITLE, title)
            )
        }
    }
}
