package vn.aow.monika.runner

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import com.swordfish.libretrodroid.Variable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.aow.monika.AppGraph
import androidx.compose.foundation.layout.fillMaxSize
import vn.aow.monika.cheats.CheatController
import vn.aow.monika.cheats.CheatSheet
import vn.aow.monika.cheats.LibretroCheats
import vn.aow.monika.diag.Diagnostics
import vn.aow.monika.ui.theme.MonikaTheme
import java.io.File

/**
 * Màn chơi game giả lập: toàn màn hình, không có menu app.
 * - Máy dọc: game nằm trên (đúng tỉ lệ hệ máy), tay cầm ở vùng ngón cái bên dưới → phím không che game.
 * - Máy ngang: game giữa màn hình, tay cầm nổi hai bên.
 * - Menu nhanh (…): lưu/tải trạng thái 1 chạm, tăng tốc 2x, chỉnh độ mờ phím.
 */
class RetroActivity : ComponentActivity() {
    private var retroView: GLRetroView? = null
    private lateinit var sramFile: File
    private lateinit var stateFile: File
    private lateinit var root: FrameLayout
    private val ui = InGameState()
    private var ready by mutableStateOf(false)
    private lateinit var cheats: CheatController
    private val cheatPicker = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { u -> if (u != null) cheats.importFile(u) }
    private var aspect = 4f / 3f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Cho game vẽ tràn cả vùng camera (máy ngang đỡ viền đen); phần giao diện tự né bằng insets.
        if (android.os.Build.VERSION.SDK_INT >= 28) window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        clock = PlayClock(intent.getStringExtra(PlayClock.EXTRA_KEY))
        val coreId = intent.getStringExtra(EXTRA_CORE) ?: return finish()
        val gamePath = intent.getStringExtra(EXTRA_GAME) ?: return finish()
        val systemName = intent.getStringExtra(EXTRA_SYSTEM).orEmpty()
        // Ghi "phiên chơi" để nếu lõi native sập thì lần mở Monika kế tiếp có đủ thông tin lập báo cáo (xem Diagnostics).
        Diagnostics.begin(this, "libretro", coreId, AppGraph.cores.info(coreId), File(gamePath).name, systemName)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: File(gamePath).nameWithoutExtension
        val layout = padFor(coreId, intent.getStringExtra(EXTRA_PAD))
        aspect = AppGraph.config.current.cores[coreId]?.aspectRatio ?: DEFAULT_ASPECT[coreId] ?: 4f / 3f
        val prefs = AppGraph.prefs
        ui.opacity = prefs.padOpacity
        ui.scale = prefs.padScale
        ui.dpadOffset = prefs.padOffset("dpad").let { (x, y) -> androidx.compose.ui.geometry.Offset(x, y) }
        ui.faceOffset = prefs.padOffset("face").let { (x, y) -> androidx.compose.ui.geometry.Offset(x, y) }

        val key = "${File(gamePath).nameWithoutExtension}-${gamePath.hashCode()}"
        // Cheat chung của Monika: nhận diện game theo tên → tự thêm mã từ kho libretro-database (mặc định tắt).
        cheats = CheatController(this, key, intent.getStringExtra(EXTRA_SYSTEM_ID).orEmpty(), listOf(title, File(gamePath).nameWithoutExtension), LibretroCheats { retroView })
        sramFile = File(File(filesDir, "saves").apply { mkdirs() }, "$key.srm")
        stateFile = File(File(filesDir, "states").apply { mkdirs() }, "$key.state")
        refreshSlots()

        // Màn chờ: tên game + vòng quay + trạng thái, giữ tới khi game vẽ khung hình đầu tiên (không còn màn đen).
        val status = TextView(this).apply {
            text = "Đang chuẩn bị…"
            setTextColor(Color.parseColor("#C8C5CB"))
            gravity = Gravity.CENTER
            textSize = 14f
        }
        val loading = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#141315"))
            addView(TextView(this@RetroActivity).apply {
                text = title; setTextColor(Color.WHITE); textSize = 18f; gravity = Gravity.CENTER
                setTypeface(typeface, android.graphics.Typeface.BOLD); setPadding(48, 0, 48, 24)
            })
            addView(android.widget.ProgressBar(this@RetroActivity).apply {
                indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FF7A32"))
            })
            addView(status.apply { setPadding(48, 24, 48, 0) })
        }
        root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#141315"))
            addView(loading, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }
        val overlay = ComposeView(this).apply {
            setContent {
                MonikaTheme {
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                    InGameOverlay(
                        state = ui, system = systemName, title = title, layout = layout, showPad = ready,
                        send = { action, k -> retroView?.sendKeyEvent(action, k) },
                        onBack = { finish() },
                        onSave = { saveState() },
                        onLoad = { loadState() },
                        onTurbo = { ui.speed = ui.speed % 4 + 1; retroView?.frameSpeed = ui.speed },
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
                        onOptions = { openOptions(coreId) },
                        onAsk = { askGroup(title, systemName) },
                        onMotion = { src, x, y ->
                            retroView?.sendMotionEvent(if (src == STICK_RIGHT) GLRetroView.MOTION_SOURCE_ANALOG_RIGHT else GLRetroView.MOTION_SOURCE_ANALOG_LEFT, x, y, 0)
                        },
                        onOptionChange = { o, v ->
                            retroView?.updateVariables(Variable(o.key, v))
                            CoreOptions.save(this@RetroActivity, coreId, o.key, v)
                            ui.options = ui.options?.map { if (it.key == o.key) it.copy(value = v) else it }
                        },
                        onOptionsReset = {
                            CoreOptions.reset(this@RetroActivity, coreId)
                            ui.options = null
                            showToast("Đã về mặc định. Mở lại game để áp dụng hết.", 2600)
                        },
                        extraActions = listOf(
                            vn.aow.monika.ui.theme.SheetAction("Mã cheat", vn.aow.monika.R.drawable.ic_fluent_document_24_regular) { cheats.show() },
                        ),
                    )
                    CheatSheet(cheats) { cheatPicker.launch(arrayOf("text/plain", "application/octet-stream", "*/*")) }
                    }
                }
            }
        }
        setContentView(root)
        // Biết vùng camera/cutout rồi mới đặt lại vị trí khung game.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            retroView?.layoutParams = gameLayoutParams()
            androidx.core.view.ViewCompat.onApplyWindowInsets(v, insets)
        }
        root.addView(overlay, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        lifecycleScope.launch {
            val core = runCatching { AppGraph.cores.ensureCore(coreId) { status.text = it } }.getOrElse {
                status.text = "Không tải được lõi giả lập.\n${it.message}\n\nKiểm tra mạng rồi mở lại game."
                Diagnostics.stage(this@RetroActivity, "core-download-failed")
                return@launch
            }
            Diagnostics.coreInfo(this@RetroActivity, AppGraph.cores.info(coreId))
            Diagnostics.stage(this@RetroActivity, "core-ready")
            val data = GLRetroViewData(this@RetroActivity).apply {
                coreFilePath = core.absolutePath
                gameFilePath = gamePath
                systemDirectory = AppGraph.cores.systemDir().absolutePath
                savesDirectory = sramFile.parentFile!!.absolutePath
                saveRAMState = sramFile.takeIf { it.exists() }?.readBytes()
                variables = CoreOptions.initial(this@RetroActivity, coreId, AppGraph.config.current.cores[coreId]?.options.orEmpty())
                    .map { (k, v) -> Variable(k, v) }.toTypedArray()
            }
            status.text = "Đang khởi động game…"
            Diagnostics.stage(this@RetroActivity, "loading-game") // lõi nạp file game ngay sau dòng này — chết ở đây = lõi hoặc file game hỏng
            val view = GLRetroView(this@RetroActivity, data)
            lifecycle.addObserver(view)
            root.addView(view, 0, gameLayoutParams()) // Dưới màn chờ; màn chờ bỏ đi khi có khung hình đầu.
            retroView = view
            ready = true
            Diagnostics.stage(this@RetroActivity, "view-created")
            launch { if (cheats.prepare()) showToast("Đã tìm thấy mã cheat cho game này (Menu → Mã cheat)", 2800) }
            launch { while (true) { delay(15_000); Diagnostics.heartbeat(this@RetroActivity) } }
            launch {
                view.getGLRetroEvents().collect { e ->
                    if (e is GLRetroView.GLRetroEvents.FrameRendered && !firstFrame) {
                        firstFrame = true
                        Diagnostics.stage(this@RetroActivity, "first-frame")
                        cheats.applyAll() // lõi đã nạp game → áp các mã đang bật
                    }
                    if (e is GLRetroView.GLRetroEvents.FrameRendered && loading.parent != null) {
                        loading.animate().alpha(0f).setDuration(180).withEndAction { root.removeView(loading) }.start()
                    }
                }
            }
            launch {
                delay(15_000)
                if (loading.parent != null) status.text = "Game nặng, đang nạp… (lần đầu có thể lâu hơn)"
            }
            launch {
                view.getGLRetroErrors().collect { code -> Diagnostics.stage(this@RetroActivity, "core-error-$code"); showToast("Lỗi chạy game (mã $code). File game hỏng hoặc lõi không hợp.", 4000) }
            }
        }
    }

    /** Dọc: game sát trên (dưới header), cao theo tỉ lệ hệ máy, tối đa 58% màn. Ngang: phủ màn (lõi tự giữ tỉ lệ). */
    private fun gameLayoutParams(): FrameLayout.LayoutParams {
        val dm = resources.displayMetrics
        val portrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        if (!portrait) return FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        // Khung game nằm dưới header; header đã bị đẩy xuống nếu máy có camera/cutout ở trên.
        val cutoutTop = androidx.core.view.ViewCompat.getRootWindowInsets(root)
            ?.getInsets(WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.statusBars())?.top ?: 0
        val top = (76 * dm.density).toInt() + cutoutTop
        val h = (dm.widthPixels / aspect).toInt().coerceAtMost((dm.heightPixels * 0.58f).toInt())
        return FrameLayout.LayoutParams(MATCH_PARENT, h, Gravity.TOP).apply { topMargin = top }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        retroView?.layoutParams = gameLayoutParams()
    }

    /** Đọc danh sách tùy chọn lõi đang chạy (lõi tự khai báo) rồi mở bảng chỉnh. */
    private fun openOptions(coreId: String) {
        val view = retroView ?: run { showToast("Game chưa chạy xong"); return }
        val list = runCatching { view.getVariables().mapNotNull { CoreOptions.parse(it.key ?: return@mapNotNull null, it.description, it.value, AppGraph.config.current.coreOptionText) } }
            .getOrDefault(emptyList())
            .sortedBy { it.label.lowercase() }
        ui.options = list
    }

    /** Ô 1 giữ tên file cũ (tương thích bản trước); ô 2, 3 thêm hậu tố; ô 0 = bản tự lưu. */
    private fun slotFile(slot: Int): File = when (slot) {
        1 -> stateFile
        0 -> File(stateFile.parentFile, stateFile.nameWithoutExtension + ".auto.state")
        else -> File(stateFile.parentFile, stateFile.nameWithoutExtension + ".s$slot.state")
    }

    private fun refreshSlots() {
        ui.filledSlots = (0..3).filter { slotFile(it).exists() }.toSet()
    }

    /** Hỏi nhóm: tự lưu game (ô "Tự lưu") → chụp màn hình game → mở Group FB với ảnh đính kèm sẵn. */
    private fun askGroup(title: String, system: String) {
        val view = retroView ?: run { showToast("Game chưa chạy xong"); return }
        lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching { view.serializeState().also { slotFile(0).writeBytes(it) }.isNotEmpty() }.getOrDefault(false)
            }
            refreshSlots()
            if (saved) showToast("Đã tự lưu game")
            val shot = captureSurface(view)
            vn.aow.monika.community.AskGroup.ask(this@RetroActivity, shot, title, system)
        }
    }

    /** Chụp khung hình đang hiện của màn game (GL) bằng PixelCopy. */
    private suspend fun captureSurface(v: android.view.SurfaceView): android.graphics.Bitmap? =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            if (v.width <= 0 || v.height <= 0) { cont.resumeWith(Result.success(null)); return@suspendCancellableCoroutine }
            val bmp = android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888)
            runCatching {
                android.view.PixelCopy.request(v, bmp, { r ->
                    if (cont.isActive) cont.resumeWith(Result.success(if (r == android.view.PixelCopy.SUCCESS) bmp else null))
                }, android.os.Handler(android.os.Looper.getMainLooper()))
            }.onFailure { if (cont.isActive) cont.resumeWith(Result.success(null)) }
        }

    private fun saveState() {
        val view = retroView ?: return
        val slot = ui.slot
        ui.menuOpen = false
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { view.serializeState().also { slotFile(slot).writeBytes(it) }.isNotEmpty() }.getOrDefault(false)
            }
            refreshSlots()
            showToast(if (ok) "Đã lưu vào ô $slot" else "Lõi này chưa hỗ trợ lưu trạng thái")
        }
    }

    private fun loadState() {
        val view = retroView ?: return
        val slot = ui.slot
        ui.menuOpen = false
        val file = slotFile(slot)
        if (!file.exists()) { showToast("Ô $slot chưa có dữ liệu"); return }
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { view.unserializeState(file.readBytes()) }.getOrDefault(false) }
            showToast(if (ok) "Đã tải ô $slot" else "Không tải được ô $slot")
        }
    }

    private fun showToast(text: String, ms: Long = 1600) {
        ui.toast = text
        lifecycleScope.launch { delay(ms); if (ui.toast == text) ui.toast = null }
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

    private var clock: PlayClock? = null

    override fun onResume() {
        super.onResume()
        clock?.resume()
    }

    override fun onPause() {
        clock?.pause()
        saveSram()
        super.onPause()
    }

    private var firstFrame = false

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            // Thoát bình thường: xóa phiên (không báo lỗi) rồi TẮT tiến trình game → lõi native giải phóng sạch RAM,
            // lần chơi sau chạy tiến trình mới (không dính trạng thái/leak của lõi lần trước).
            Diagnostics.end(this)
            android.os.Process.killProcess(android.os.Process.myPid())
        }
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
        private const val EXTRA_SYSTEM = "system"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_PAD = "pad"
        private const val EXTRA_SYSTEM_ID = "system_id"

        /** Tỉ lệ khung hình mặc định theo lõi (config `cores.<id>.aspectRatio` ghi đè được). NDS = 2 màn chồng dọc. */
        private val DEFAULT_ASPECT = mapOf(
            "desmume" to 256f / 384f, "melonds" to 256f / 384f,
            "mgba" to 3f / 2f, "gambatte" to 10f / 9f,
            "pcsx_rearmed" to 4f / 3f, "ppsspp" to 480f / 272f, "easyrpg" to 4f / 3f,
        )

        fun start(activity: Activity, coreId: String, game: File, system: String = "", title: String = "", pad: String? = null, key: String? = null, systemId: String = "") {
            activity.startActivity(
                Intent(activity, RetroActivity::class.java)
                    .putExtra(PlayClock.EXTRA_KEY, key)
                    .putExtra(EXTRA_CORE, coreId)
                    .putExtra(EXTRA_GAME, game.absolutePath)
                    .putExtra(EXTRA_SYSTEM, system)
                    .putExtra(EXTRA_TITLE, title)
                    .putExtra(EXTRA_PAD, pad)
                    .putExtra(EXTRA_SYSTEM_ID, systemId)
            )
        }
    }
}
