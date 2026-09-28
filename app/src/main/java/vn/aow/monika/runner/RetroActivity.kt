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
    private var aspect = 4f / 3f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val coreId = intent.getStringExtra(EXTRA_CORE) ?: return finish()
        val gamePath = intent.getStringExtra(EXTRA_GAME) ?: return finish()
        val systemName = intent.getStringExtra(EXTRA_SYSTEM).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE) ?: File(gamePath).nameWithoutExtension
        val layout = padFor(coreId, intent.getStringExtra(EXTRA_PAD))
        aspect = AppGraph.config.current.cores[coreId]?.aspectRatio ?: DEFAULT_ASPECT[coreId] ?: 4f / 3f
        val prefs = AppGraph.prefs
        ui.opacity = prefs.padOpacity
        ui.scale = prefs.padScale
        ui.dpadOffset = prefs.padOffset("dpad").let { (x, y) -> androidx.compose.ui.geometry.Offset(x, y) }
        ui.faceOffset = prefs.padOffset("face").let { (x, y) -> androidx.compose.ui.geometry.Offset(x, y) }

        val key = "${File(gamePath).nameWithoutExtension}-${gamePath.hashCode()}"
        sramFile = File(File(filesDir, "saves").apply { mkdirs() }, "$key.srm")
        stateFile = File(File(filesDir, "states").apply { mkdirs() }, "$key.state")
        refreshSlots()

        val status = TextView(this).apply {
            text = "Đang chuẩn bị…"
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            textSize = 16f
        }
        root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#141315"))
            addView(status, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }
        val overlay = ComposeView(this).apply {
            setContent {
                MonikaTheme {
                    InGameOverlay(
                        state = ui, system = systemName, title = title, layout = layout, showPad = ready,
                        send = { action, k -> retroView?.sendKeyEvent(action, k) },
                        onBack = { finish() },
                        onSave = { saveState() },
                        onLoad = { loadState() },
                        onTurbo = { ui.turbo = !ui.turbo; retroView?.frameSpeed = if (ui.turbo) 2 else 1 },
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
                    )
                }
            }
        }
        setContentView(root)
        root.addView(overlay, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        lifecycleScope.launch {
            val core = runCatching { AppGraph.cores.ensureCore(coreId) { status.text = it } }.getOrElse {
                status.text = "Không tải được lõi giả lập.\n${it.message}\n\nKiểm tra mạng rồi mở lại game."
                return@launch
            }
            val data = GLRetroViewData(this@RetroActivity).apply {
                coreFilePath = core.absolutePath
                gameFilePath = gamePath
                systemDirectory = AppGraph.cores.systemDir().absolutePath
                savesDirectory = sramFile.parentFile!!.absolutePath
                saveRAMState = sramFile.takeIf { it.exists() }?.readBytes()
                variables = CoreOptions.initial(this@RetroActivity, coreId, AppGraph.config.current.cores[coreId]?.options.orEmpty())
                    .map { (k, v) -> Variable(k, v) }.toTypedArray()
            }
            val view = GLRetroView(this@RetroActivity, data)
            lifecycle.addObserver(view)
            root.removeView(status)
            root.addView(view, 0, gameLayoutParams())
            retroView = view
            ready = true
            launch {
                view.getGLRetroErrors().collect { code -> showToast("Lỗi chạy game (mã $code). File game hỏng hoặc lõi không hợp.", 4000) }
            }
        }
    }

    /** Dọc: game sát trên (dưới header), cao theo tỉ lệ hệ máy, tối đa 58% màn. Ngang: phủ màn (lõi tự giữ tỉ lệ). */
    private fun gameLayoutParams(): FrameLayout.LayoutParams {
        val dm = resources.displayMetrics
        val portrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        if (!portrait) return FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        val top = (76 * dm.density).toInt()
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

    /** Ô 1 giữ tên file cũ (tương thích bản trước); ô 2, 3 thêm hậu tố. */
    private fun slotFile(slot: Int): File =
        if (slot == 1) stateFile else File(stateFile.parentFile, stateFile.nameWithoutExtension + ".s$slot.state")

    private fun refreshSlots() {
        ui.filledSlots = (1..3).filter { slotFile(it).exists() }.toSet()
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
        private const val EXTRA_SYSTEM = "system"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_PAD = "pad"

        /** Tỉ lệ khung hình mặc định theo lõi (config `cores.<id>.aspectRatio` ghi đè được). NDS = 2 màn chồng dọc. */
        private val DEFAULT_ASPECT = mapOf(
            "desmume" to 256f / 384f, "melonds" to 256f / 384f,
            "mgba" to 3f / 2f, "gambatte" to 10f / 9f,
            "pcsx_rearmed" to 4f / 3f, "ppsspp" to 480f / 272f, "easyrpg" to 4f / 3f,
        )

        fun start(activity: Activity, coreId: String, game: File, system: String = "", title: String = "", pad: String? = null) {
            activity.startActivity(
                Intent(activity, RetroActivity::class.java)
                    .putExtra(EXTRA_CORE, coreId)
                    .putExtra(EXTRA_GAME, game.absolutePath)
                    .putExtra(EXTRA_SYSTEM, system)
                    .putExtra(EXTRA_TITLE, title)
                    .putExtra(EXTRA_PAD, pad)
            )
        }
    }
}
