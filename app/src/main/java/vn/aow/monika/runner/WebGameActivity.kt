package vn.aow.monika.runner

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import vn.aow.monika.ui.theme.MonikaMenuSheet

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader
import vn.aow.monika.AppGraph
import vn.aow.monika.cheats.WebCheatSheet
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream

/**
 * Chạy game dạng web trong WebView:
 * - html5: RPG Maker MV/MZ, TyranoScript... (mở index.html của game)
 * - ruffle: Flash .swf qua Ruffle (script lấy từ link trong cấu hình → cập nhật Ruffle không cần phát hành app)
 *
 * File game được phục vụ qua https://appassets.androidplatform.net/game/... để game đọc file JSON/âm thanh như trên web thật.
 */
class WebGameActivity : ComponentActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        clock = PlayClock(intent.getStringExtra(PlayClock.EXTRA_KEY))
        val entry = File(intent.getStringExtra(EXTRA_ENTRY) ?: return finish())
        val player = intent.getStringExtra(EXTRA_PLAYER) ?: "html5"
        val root = entry.parentFile ?: return finish()

        val loader = WebViewAssetLoader.Builder()
            .setDomain(HOST)
            .addPathHandler("/game/", DirectoryHandler(root))
            .addPathHandler("/__monika/", PlayerPageHandler(entry.name, resources))
            .addPathHandler("/__onsyuri/", OnsyuriHandler(AppGraph.packs.dir(vn.aow.monika.pack.PackManager.ONSYURI), root, intent.getStringExtra(EXTRA_TITLE)))
            .build()

        var cheatsRef: vn.aow.monika.cheats.WebCheatController? = null
        val web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    loader.shouldInterceptRequest(request.url)

                override fun onPageFinished(view: WebView, url: String?) {
                    cheatsRef?.onPageFinished()
                }
            }
        }
        val cheats = vn.aow.monika.cheats.WebCheatController(this) { js, done -> web.post { web.evaluateJavascript(js) { done(it) } } }
        cheatsRef = cheats
        val start = when (player) {
            "ruffle" -> "https://$HOST/__monika/ruffle.html"
            "onsyuri" -> "https://$HOST/__onsyuri/onsyuri.html"
            else -> "https://$HOST/game/" + android.net.Uri.encode(entry.name)
        }
        val title = intent.getStringExtra(EXTRA_TITLE) ?: entry.parentFile?.name ?: entry.nameWithoutExtension
        val system = when (player) { "ruffle" -> "Flash"; "onsyuri" -> "ONScripter"; else -> "Game web" }
        // Lớp giao diện Monika nổi trên game: nút menu tròn + menu popup dưới đáy (giống giả lập khác).
        val overlay = androidx.compose.ui.platform.ComposeView(this).apply {
            setContent {
                vn.aow.monika.ui.theme.MonikaTheme {
                    WebGameOverlay(title, system, cheats, onReload = { web.loadUrl(start) }, onExit = { finish() }, onAsk = {
                        // Game web tự lưu theo cách của game (localStorage) → chỉ cần chụp màn hình.
                        val bmp = runCatching {
                            android.graphics.Bitmap.createBitmap(web.width, web.height, android.graphics.Bitmap.Config.ARGB_8888)
                                .also { web.draw(android.graphics.Canvas(it)) }
                        }.getOrNull()
                        vn.aow.monika.community.AskGroup.ask(this@WebGameActivity, bmp, title, system)
                    })
                }
            }
        }
        setContentView(android.widget.FrameLayout(this).apply {
            addView(web)
            addView(overlay)
        })
        if (player == "onsyuri") {
            // Engine ONScripter nằm trong gói tải khi cần (thường đã được tải trước lúc bấm tải game).
            val pm = vn.aow.monika.pack.PackManager
            if (pm.needed(pm.ONSYURI)) {
                android.widget.Toast.makeText(this, "Đang tải engine ONScripter (chỉ lần đầu)…", android.widget.Toast.LENGTH_LONG).show()
                lifecycleScope.launch {
                    val ok = runCatching { pm.install(pm.ONSYURI) {} }.isSuccess
                    if (ok) web.loadUrl(start) else {
                        android.widget.Toast.makeText(this@WebGameActivity, "Không tải được engine ONScripter. Kiểm tra mạng rồi thử lại.", android.widget.Toast.LENGTH_LONG).show()
                        finish()
                    }
                }
            } else web.loadUrl(start)
        } else web.loadUrl(start)
    }

    private var clock: PlayClock? = null

    override fun onResume() {
        super.onResume()
        clock?.resume()
    }

    override fun onPause() {
        clock?.pause()
        super.onPause()
    }

    /** Phục vụ file trong thư mục game, chặn đọc ra ngoài thư mục. */
    private class DirectoryHandler(root: File) : WebViewAssetLoader.PathHandler {
        private val base = root.canonicalFile
        override fun handle(path: String): WebResourceResponse {
            val file = File(base, path).canonicalFile
            if (!file.path.startsWith(base.path + File.separator) || !file.isFile) {
                return WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
            }
            return WebResourceResponse(mimeOf(file), null, FileInputStream(file))
        }

        private fun mimeOf(file: File): String = when (file.extension.lowercase()) {
            "js" -> "text/javascript"
            "json" -> "application/json"
            "wasm" -> "application/wasm"
            "ogg" -> "audio/ogg"
            "m4a" -> "audio/mp4"
            "swf" -> "application/x-shockwave-flash"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
        }
    }

    /**
     * ONScripter (OnscripterYuri bản web): phục vụ trang + wasm từ gói đã tải, và tự sinh onsyuri_index.json liệt kê file game
     * (tải lười theo nhu cầu từ /game/…) — thay cho bước chạy onsyuri_index.py trên máy tính.
     */
    private class OnsyuriHandler(private val moduleDir: File, private val gameRoot: File, private val title: String?) : WebViewAssetLoader.PathHandler {
        private val files = DirectoryHandler(moduleDir)
        override fun handle(path: String): WebResourceResponse? {
            if (path != "onsyuri_index.json") return files.handle(path)
            val base = gameRoot.canonicalFile
            val list = base.walkTopDown().filter { it.isFile && !it.name.startsWith(".monika") }.map { f ->
                f.relativeTo(base).path.replace(File.separatorChar, '/')
            }.toList()
            val saveId = java.lang.Integer.toHexString(base.path.hashCode())
            val arr = org.json.JSONArray()
            for (rel in list) arr.put(org.json.JSONObject().put("path", rel).put("url", "https://$HOST/game/" + android.net.Uri.encode(rel, "/")))
            if (list.none { it.equals("default.ttf", ignoreCase = true) })
                arr.put(org.json.JSONObject().put("path", "default.ttf").put("url", "https://$HOST/__monika/default.ttf").put("lazyload", false))
            val json = org.json.JSONObject()
                .put("title", title ?: base.name).put("gamedir", "/onsyuri/game").put("savedir", "/onsyuri_save/$saveId")
                .put("lazyload", true).put("args", org.json.JSONArray()).put("files", arr)
            return WebResourceResponse("application/json", "utf-8", ByteArrayInputStream(json.toString().toByteArray()))
        }
    }

    /** Trang HTML nhúng Ruffle để chạy file .swf. */
    private class PlayerPageHandler(private val swfName: String, private val res: android.content.res.Resources) : WebViewAssetLoader.PathHandler {
        override fun handle(path: String): WebResourceResponse? {
            // Phông dự phòng cho ONScripter khi game không có default.ttf (Manrope, OFL, có đủ chữ Việt).
            if (path == "default.ttf") return WebResourceResponse("font/ttf", null, res.openRawResource(vn.aow.monika.R.font.manrope))
            if (path != "ruffle.html") return null
            val script = AppGraph.config.current.webPlayers["ruffle"]?.script ?: return null
            val swf = "/game/" + android.net.Uri.encode(swfName)
            val html = """
                <!doctype html><html><head>
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <style>html,body{margin:0;height:100%;background:#000;overflow:hidden}ruffle-player{width:100%;height:100%}</style>
                <script>window.RufflePlayer=window.RufflePlayer||{};window.RufflePlayer.config={autoplay:"on",unmuteOverlay:"hidden",letterbox:"on"};</script>
                <script src="$script"></script>
                </head><body><script>
                const p = window.RufflePlayer.newest().createPlayer();
                document.body.appendChild(p);
                p.load("$swf");
                </script></body></html>
            """.trimIndent()
            return WebResourceResponse("text/html", "utf-8", ByteArrayInputStream(html.toByteArray()))
        }
    }

    companion object {
        private const val HOST = "appassets.androidplatform.net"
        private const val EXTRA_ENTRY = "entry"
        private const val EXTRA_PLAYER = "player"
        private const val EXTRA_TITLE = "title"

        fun start(activity: Activity, entry: File, player: String, title: String? = null, key: String? = null) {
            activity.startActivity(
                Intent(activity, WebGameActivity::class.java)
                    .putExtra(PlayClock.EXTRA_KEY, key)
                    .putExtra(EXTRA_ENTRY, entry.absolutePath)
                    .putExtra(EXTRA_PLAYER, player)
                    .putExtra(EXTRA_TITLE, title)
            )
        }
    }
}

/** Nút menu nhỏ góc phải trên (né camera) + menu popup chung: Chơi tiếp · Tải lại · Thoát. Nút Back của máy cũng mở menu. */
@androidx.compose.runtime.Composable
private fun WebGameOverlay(title: String, system: String, cheats: vn.aow.monika.cheats.WebCheatController, onReload: () -> Unit, onExit: () -> Unit, onAsk: () -> Unit) {
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = !open) { open = true }
    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
        GameMenuButton(open, { open = !open }, androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.TopEnd))
        MonikaMenuSheet(
            open, { open = false }, title = title, subtitle = system,
            actions = listOf(
                vn.aow.monika.ui.theme.SheetAction("Chơi tiếp", vn.aow.monika.R.drawable.ic_fluent_play_24_regular, highlight = true) {},
                vn.aow.monika.ui.theme.SheetAction("Cheat & tốc độ", vn.aow.monika.R.drawable.ic_fluent_document_24_regular) { cheats.show() },
                vn.aow.monika.ui.theme.SheetAction("Hỏi nhóm FB", vn.aow.monika.R.drawable.ic_fluent_people_community_24_regular, onClick = onAsk),
                vn.aow.monika.ui.theme.SheetAction("Tải lại game", vn.aow.monika.R.drawable.ic_fluent_arrow_clockwise_24_regular, onClick = onReload),
                vn.aow.monika.ui.theme.SheetAction("Thoát game", vn.aow.monika.R.drawable.ic_fluent_door_arrow_left_24_regular, onClick = onExit),
            ),
        )
        WebCheatSheet(cheats)
    }
}
