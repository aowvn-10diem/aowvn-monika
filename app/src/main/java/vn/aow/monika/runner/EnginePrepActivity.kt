package vn.aow.monika.runner

import android.app.Activity
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import vn.aow.monika.AppGraph
import vn.aow.monika.R
import vn.aow.monika.diag.Diagnostics
import vn.aow.monika.library.EntryResolution
import vn.aow.monika.pack.PackAction
import vn.aow.monika.pack.PackChoice
import vn.aow.monika.pack.PackManager
import vn.aow.monika.pack.PackPolicy
import vn.aow.monika.ui.theme.MonikaMenuSheet
import vn.aow.monika.ui.theme.MonikaTheme
import vn.aow.monika.ui.theme.SheetChip
import vn.aow.monika.ui.theme.SheetColors
import java.io.File

/**
 * "Bấm game là chơi": màn chuẩn bị chung cho mọi engine nhúng ([EngineRoutes]). Tự tải gói (lần đầu), xin quyền đọc
 * thư mục game nếu thật sự cần, rồi tự mở game. Không có bước "bấm Chơi lại". Mạng di động + gói > 15 MB → hỏi ngay
 * tại đây (Tải luôn / Đợi Wi-Fi).
 */
class EnginePrepActivity : ComponentActivity() {
    private var status by mutableStateOf("Đang chuẩn bị…")
    private var ask by mutableStateOf(false)
    private var needAccess by mutableStateOf(false)
    private var started = false

    private val engine: String get() = intent.getStringExtra(EXTRA_ENGINE).orEmpty()
    private val route: EngineRoute? get() = EngineRoutes.all[engine]
    private val packId: String get() = route?.packId.orEmpty()
    private val label: String get() = route?.label ?: "game"

    private val entry: File? get() = intent.getStringExtra(EXTRA_ENTRY)?.let(::File)
    private val title: String get() = intent.getStringExtra(EXTRA_TITLE).orEmpty()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MonikaTheme {
                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF141315))) {
                    MonikaMenuSheet(
                        true, { finish() }, actions = emptyList(),
                        title = title.ifBlank { label }, subtitle = status,
                        header = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (ask) {
                                    Text("Bạn đang dùng mạng di động. Thành phần $label chỉ tải một lần.", color = SheetColors.textSecondary)
                                    SheetChip("Tải luôn bằng 4G", true) { ask = false; download() }
                                    SheetChip("Đợi Wi-Fi", false) {
                                        PackManager.choose(applicationContext, packId, PackChoice.WAIT_WIFI)
                                        Toast.makeText(this@EnginePrepActivity, "Sẽ tự tải khi có Wi-Fi. Mở lại game sau nhé.", Toast.LENGTH_LONG).show()
                                        finish()
                                    }
                                } else if (needAccess) {
                                    Text("Game nằm ở thư mục ngoài Monika, cần cho phép đọc tệp (một lần).", color = SheetColors.textSecondary)
                                    SheetChip("Cho phép", true) { openAccessSettings() }
                                }
                            }
                        },
                    )
                }
            }
        }
        if (route == null) return finish()
        Diagnostics.crumb(this, "engine", "prep $engine ${entry?.name}")
        proceed()
    }

    override fun onResume() { super.onResume(); if (needAccess && accessOk()) { needAccess = false; proceed() } }

    private fun accessOk(): Boolean {
        val f = entry ?: return true
        if (!f.exists() || f.canRead()) return true
        return Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()
    }

    private fun openAccessSettings() {
        runCatching {
            if (Build.VERSION.SDK_INT >= 30)
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
            else requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), 1)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (accessOk()) { needAccess = false; proceed() }
    }

    private fun proceed() {
        if (!accessOk()) { needAccess = true; status = "Cần quyền đọc tệp game"; return }
        if (!PackManager.needed(packId)) return play()
        val cm = getSystemService(ConnectivityManager::class.java)
        val size = AppGraph.config.current.modules[packId]?.let { it.sizeByAbi[AppGraph.packs.abi] ?: it.size } ?: 0L
        val action = PackPolicy.decide(size, !cm.isActiveNetworkMetered,
            cm.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED, null)
        if (action == PackAction.ASK) { status = "Cần tải thành phần $label (~${size / (1024 * 1024)} MB)"; ask = true } else download()
    }

    private fun download() {
        status = "Đang tải thành phần $label (chỉ lần đầu)…"
        lifecycleScope.launch {
            runCatching { PackManager.install(packId) { status = it } }
                .onSuccess { play() }
                .onFailure {
                    Diagnostics.recordHandled(this@EnginePrepActivity, "pack:$packId", "tải gói $packId thất bại", it)
                    status = "Không tải được: ${it.message ?: "lỗi mạng"}. Kiểm tra mạng rồi bấm lại."
                }
        }
    }

    private fun play() {
        if (started) return
        started = true
        val resolver = route?.resolve
        if (resolver == null) return open(entry)
        // V26: kiểm/chọn lại lối vào ở luồng nền, có timeout; lỗi hoặc quá hạn → giữ nguyên lối vào cũ.
        status = getString(R.string.engine_prep_checking_entry)
        lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) {
                withTimeoutOrNull(ENTRY_CHECK_TIMEOUT_MS) { runCatching { resolver(entry) }.getOrNull() }
            }
            when (r) {
                is EntryResolution.Use -> { Diagnostics.crumb(this@EnginePrepActivity, "engine-entry", "chọn ${r.entry.name}: ${r.why}"); open(r.entry) }
                is EntryResolution.NotFound -> {
                    Diagnostics.crumb(this@EnginePrepActivity, "engine-entry", "không tìm thấy lối vào: ${r.why}")
                    Diagnostics.recordHandled(this@EnginePrepActivity, "engine:$engine", "không có startup.tjs trong ${r.dirName}: ${r.why}")
                    status = getString(R.string.engine_entry_not_found, r.dirName)
                    started = false
                }
                is EntryResolution.Keep -> { Diagnostics.crumb(this@EnginePrepActivity, "engine-entry", "giữ nguyên: ${r.why}"); open(entry) }
                null -> { Diagnostics.crumb(this@EnginePrepActivity, "engine-entry", "kiểm lối vào lỗi hoặc quá hạn, giữ nguyên"); open(entry) }
            }
        }
    }

    private fun open(entry: File?) {
        route?.open?.invoke(this, entry, title, intent.getStringExtra(EXTRA_KEY))
        finish()
    }

    companion object {
        private const val ENTRY_CHECK_TIMEOUT_MS = 8_000L
        private const val EXTRA_ENGINE = "engine"
        private const val EXTRA_ENTRY = "entry"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_KEY = "key"

        fun start(activity: Activity, engine: String, entry: File?, title: String, key: String?) {
            activity.startActivity(
                Intent(activity, EnginePrepActivity::class.java)
                    .putExtra(EXTRA_ENGINE, engine).putExtra(EXTRA_ENTRY, entry?.absolutePath).putExtra(EXTRA_TITLE, title).putExtra(EXTRA_KEY, key),
            )
        }
    }
}
