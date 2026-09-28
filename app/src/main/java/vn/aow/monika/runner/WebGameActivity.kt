package vn.aow.monika.runner

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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader
import vn.aow.monika.AppGraph
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

        val entry = File(intent.getStringExtra(EXTRA_ENTRY) ?: return finish())
        val player = intent.getStringExtra(EXTRA_PLAYER) ?: "html5"
        val root = entry.parentFile ?: return finish()

        val loader = WebViewAssetLoader.Builder()
            .setDomain(HOST)
            .addPathHandler("/game/", DirectoryHandler(root))
            .addPathHandler("/__monika/", PlayerPageHandler(entry.name))
            .build()

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
            }
        }
        setContentView(web)
        val start = if (player == "ruffle") "https://$HOST/__monika/ruffle.html" else "https://$HOST/game/" + android.net.Uri.encode(entry.name)
        web.loadUrl(start)
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

    /** Trang HTML nhúng Ruffle để chạy file .swf. */
    private class PlayerPageHandler(private val swfName: String) : WebViewAssetLoader.PathHandler {
        override fun handle(path: String): WebResourceResponse? {
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

        fun start(activity: Activity, entry: File, player: String) {
            activity.startActivity(
                Intent(activity, WebGameActivity::class.java)
                    .putExtra(EXTRA_ENTRY, entry.absolutePath)
                    .putExtra(EXTRA_PLAYER, player)
            )
        }
    }
}
