package vn.aow.monika.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import vn.aow.monika.AppGraph
import vn.aow.monika.ui.screens.GameDetailRoute
import vn.aow.monika.ui.screens.LibraryScreen
import vn.aow.monika.ui.screens.OFFICIAL_BUILD_WARNING
import vn.aow.monika.ui.screens.OfficialBuildWarning
import vn.aow.monika.ui.screens.SettingsScreen
import vn.aow.monika.ui.theme.*

/** Bản Play mở vào Thư viện; tệp được người dùng đưa vào qua SAF. */
class MainActivity : ComponentActivity() {
    /** Khóa game (Game.key) vừa được yêu cầu mở trang chi tiết (V139). */
    private var pendingGame by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        acceptFiles(intent)
        takeGameIntent(intent)
        setContent {
            MonikaTheme {
                var page by rememberSaveable { mutableStateOf("library") }
                var gameKey by rememberSaveable { mutableStateOf(pendingGame) }
                LaunchedEffect(pendingGame) {
                    pendingGame?.let { gameKey = it; page = "game"; pendingGame = null }
                }
                // [A78] V154: chứng thư Play App Signing khác bản GitHub; catalog Play để trống = bỏ kiểm.
                val config by AppGraph.config.config.collectAsState()
                BackHandler(page != "library") {
                    if (page == "game") gameKey = null
                    page = "library"
                }
                Column(Modifier.fillMaxSize()) {
                    // Trang game có thanh tiêu đề riêng (nút Quay lại) nên không lặp tiêu đề app.
                    // [A65] Bản Play không có mục đọc bài viết của web AowVN.
                    if (page != "game") MonikaHeader("Aow Monika")
                    // Bản Play mở vào Thư viện (trang chủ của flavor này): cảnh báo đỏ, không tắt được.
                    if (page == "library") OfficialBuildWarning(vn.aow.monika.security.OfficialCertPin.OFFICIAL_APK_CERT_SHA256, OFFICIAL_BUILD_WARNING)
                    Box(Modifier.weight(1f)) {
                        when (page) {
                            "settings" -> SettingsScreen(onBack = { page = "library" })
                            "game" -> {
                                val key = gameKey
                                if (key != null) GameDetailRoute(key) { page = "library"; gameKey = null }
                                else LibraryScreen(onSettings = { page = "settings" })
                            }
                            else -> LibraryScreen(onSettings = { page = "settings" })
                        }
                    }
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); acceptFiles(intent); takeGameIntent(intent) }
    override fun onResume() {
        super.onResume()
        AppGraph.prefs.mergeGameEvents()
        AppGraph.prefs.endSession()
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            vn.aow.monika.diag.Diagnostics.op(this@MainActivity, "doc-bao-loi") {
                val cr = AppGraph.config.current.crash
                val fresh = listOfNotNull(vn.aow.monika.diag.Diagnostics.collect(applicationContext)) +
                    vn.aow.monika.diag.Diagnostics.collectProcessDeaths(applicationContext)
                fresh.forEach { r -> vn.aow.monika.diag.Diagnostics.autoSend(applicationContext, AppGraph.http, r, cr.endpoint, cr.autoSend, AppGraph.prefs.autoSendCrash) }
                vn.aow.monika.diag.Breadcrumbs.sweep(applicationContext)
            }
        }
    }
    private fun acceptFiles(intent: Intent?) {
        val uris = when (intent?.action) {
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            Intent.ACTION_SEND -> listOfNotNull(if (Build.VERSION.SDK_INT >= 33)
                intent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM))
            else -> emptyList()
        }
        if (uris.isNotEmpty()) { AppGraph.pendingImports.value = uris; intent?.action = null }
    }
    private fun takeGameIntent(intent: Intent?) {
        val key = intent?.getStringExtra(EXTRA_GAME_KEY)?.takeIf { it.isNotBlank() } ?: return
        intent.removeExtra(EXTRA_GAME_KEY) // xoay màn hình không mở lại lần nữa
        pendingGame = key
    }
    companion object {
        const val EXTRA_POST_ID = "post_id"
        const val EXTRA_OPEN_LIBRARY = "open_library"
        /** Khóa game (Game.key) để mở trang chi tiết. */
        const val EXTRA_GAME_KEY = "game_key"
    }
}
