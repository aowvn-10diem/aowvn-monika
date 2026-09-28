package vn.aow.monika.browser

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import vn.aow.monika.AppGraph
import vn.aow.monika.ui.theme.Radius
import vn.aow.monika.ui.theme.primaryGradient
import java.io.ByteArrayInputStream
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import vn.aow.monika.R
import vn.aow.monika.ui.theme.CircleButton
import vn.aow.monika.ui.theme.Monika
import vn.aow.monika.ui.theme.MonikaTheme

/**
 * Trình duyệt nhúng trong app: mở Discord / Facebook khi máy chưa cài app của họ.
 * Giữ đăng nhập (cookie) giữa các lần mở. Link không phải web (discord://, intent://) → chuyển cho app ngoài.
 */
class InAppBrowserActivity : ComponentActivity() {
    private var web: WebView? = null
    private var pageTitle by mutableStateOf("")
    private var host by mutableStateOf("")
    private var loadProgress by mutableFloatStateOf(0f)

    // Chặn quảng cáo: trạng thái bộ lọc + số yêu cầu đã chặn trên trang này.
    private var adState by mutableStateOf(AdState.OFF)
    private var blocked by mutableIntStateOf(0)
    @Volatile private var adOn = true
    private lateinit var allow: List<String>

    private enum class AdState { OFF, LOADING, ON, FAILED }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL) ?: return finish()
        host = Uri.parse(url).host.orEmpty()

        val cfg = AppGraph.config.current.adblock
        allow = cfg.allow.map { it.lowercase() }
        if (cfg.enabled) {
            adState = if (AppGraph.adblock.ready) AdState.ON else AdState.LOADING
            // Lần đầu: tải bộ lọc về (vài giây); các lần sau: nạp bản đã lưu, quá hạn thì cập nhật ngầm.
            lifecycleScope.launch {
                val ok = AppGraph.adblock.ensure(cfg.lists, cfg.updateHours)
                adState = if (ok) AdState.ON else AdState.FAILED
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val w = web
                if (w != null && w.canGoBack()) w.goBack() else finish()
            }
        })

        setContent {
            MonikaTheme {
                val c = Monika.colors
                Column(Modifier.fillMaxSize().background(c.bg)) {
                    Row(
                        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircleButton(R.drawable.ic_fluent_dismiss_24_regular, "Đóng", { finish() })
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(pageTitle.ifBlank { host }, style = Monika.type.bodyStrong, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(host, style = Monika.type.caption, color = c.textSecondary, maxLines = 1)
                        }
                        if (adState != AdState.OFF) {
                            val label = when {
                                adState == AdState.LOADING -> "Đang tải chặn QC…"
                                adState == AdState.FAILED -> "Chặn QC lỗi"
                                !adOn -> "Chặn QC: tắt"
                                else -> "Đã chặn $blocked"
                            }
                            Row(
                                Modifier.padding(end = 8.dp).clip(Radius.pill)
                                    .background(if (adState == AdState.ON && adOn) primaryGradient() else Brush.linearGradient(listOf(c.surfaceSoft, c.surfaceSoft)))
                                    .clickable(enabled = adState == AdState.ON) { adOn = !adOn; blocked = 0; web?.reload() }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(painterResource(R.drawable.ic_fluent_shield_checkmark_24_regular), "Chặn quảng cáo", Modifier.size(18.dp),
                                    tint = if (adState == AdState.ON && adOn) Color.White else c.textSecondary)
                                Text(label, style = Monika.type.caption, color = if (adState == AdState.ON && adOn) Color.White else c.textSecondary,
                                    modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                        CircleButton(R.drawable.ic_fluent_open_24_regular, "Mở bằng trình duyệt", {
                            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(web?.url ?: url))) }
                        })
                    }
                    Box(Modifier.fillMaxWidth().height(3.dp)) {
                        if (loadProgress in 0.01f..0.99f) LinearProgressIndicator({ loadProgress }, Modifier.fillMaxWidth(), color = c.accentCoral, trackColor = c.track)
                    }
                    AndroidView(
                        factory = { ctx -> createWebView(ctx).also { web = it; if (savedInstanceState == null) it.loadUrl(url) } },
                        modifier = Modifier.fillMaxWidth().weight(1f).navigationBarsPadding(),
                    )
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(ctx: Context) = WebView(ctx).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true // Discord web cần localStorage để giữ đăng nhập.
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = true
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, p: Int) { this@InAppBrowserActivity.loadProgress = p / 100f }
            override fun onReceivedTitle(view: WebView, t: String?) { this@InAppBrowserActivity.pageTitle = t.orEmpty() }
        }
        webViewClient = object : WebViewClient() {
            // Chạy trên luồng nền của WebView: yêu cầu tới tên miền quảng cáo → trả về rỗng.
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (!adOn || adState != AdState.ON || request.isForMainFrame) return null
                val h = request.url.host?.lowercase() ?: return null
                if (allow.any { h == it || h.endsWith(".$it") }) return null
                if (!AppGraph.adblock.blocks(h)) return null
                runOnUiThread { blocked++ }
                return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                if (u.scheme == "http" || u.scheme == "https") return false
                // discord://, fb://, intent://… → để app tương ứng xử lý (nếu có).
                runCatching {
                    val i = if (u.scheme == "intent") Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME) else Intent(Intent.ACTION_VIEW, u)
                    startActivity(i)
                }
                return true
            }
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                blocked = 0
                this@InAppBrowserActivity.host = url?.let { Uri.parse(it).host }.orEmpty()
            }
        }
    }

    override fun onPause() {
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onDestroy() {
        web?.destroy()
        web = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_URL = "url"

        fun start(context: Context, url: String) {
            context.startActivity(
                Intent(context, InAppBrowserActivity::class.java).putExtra(EXTRA_URL, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
