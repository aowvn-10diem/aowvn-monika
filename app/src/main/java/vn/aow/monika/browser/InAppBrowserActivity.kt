package vn.aow.monika.browser

import vn.aow.monika.ui.theme.MonikaIcon
import android.webkit.ValueCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import java.io.File
import vn.aow.monika.ui.theme.MonikaMenuSheet
import vn.aow.monika.ui.theme.SheetAction

import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.BasicTextField
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
    @Volatile private var cosmeticOn = true
    @Volatile private var popupsOn = true
    /** Tên miền người dùng chủ động mở — không bao giờ coi là quảng cáo. */
    @Volatile private var startHost = ""
    private lateinit var allow: List<String>

    private enum class AdState { OFF, LOADING, ON, FAILED }

    private var searching by mutableStateOf(false)
    private var query by mutableStateOf("")

    /** Tìm trong trang đang xem: Facebook → tìm trên Facebook; trang khác → tìm Google. Luôn mở ngay trong app. */
    private fun search(q: String) {
        val t = q.trim()
        if (t.isEmpty()) return
        val url = when {
            host.contains("facebook.com") -> "https://m.facebook.com/search/top/?q=" + Uri.encode(t)
            else -> "https://www.google.com/search?q=" + Uri.encode(t)
        }
        searching = false
        web?.loadUrl(url)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL) ?: return finish()
        host = Uri.parse(url).host.orEmpty()
        startHost = host.lowercase()

        val cfg = AppGraph.config.current.adblock
        allow = cfg.allow.map { it.lowercase() }
        cosmeticOn = cfg.cosmetic
        popupsOn = cfg.popups
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

        attachment = intent.getStringExtra(EXTRA_ATTACH)?.let(::File)?.takeIf { it.isFile }

        setContent {
            MonikaTheme {
                val c = Monika.colors
                var menu by remember { mutableStateOf(false) }
                Box(Modifier.fillMaxSize().background(c.bg)) {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxWidth().statusBarsPadding().height(3.dp)) {
                            if (loadProgress in 0.01f..0.99f) LinearProgressIndicator({ loadProgress }, Modifier.fillMaxWidth(), color = c.accentCoral, trackColor = c.track)
                        }
                        // Ảnh chụp game đang chờ đăng hỏi nhóm.
                        attachment?.let { f ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clip(Radius.medium).background(c.surfaceDark).padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                coil.compose.AsyncImage(f, null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(44.dp).clip(Radius.thumb))
                                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                    Text("Ảnh chụp game đã sẵn sàng", style = Monika.type.bodyStrong, color = Color.White)
                                    Text("Tạo bài viết trong nhóm → bấm Ảnh: app tự đính kèm. Lời nhắn đã chép sẵn, dán vào là xong.",
                                        style = Monika.type.caption, color = c.textOnDarkSecondary)
                                }
                                MonikaIcon(R.drawable.ic_fluent_dismiss_24_regular, "Bỏ ảnh",
                                    Modifier.size(32.dp).clip(Radius.pill).clickable { attachment = null }.padding(6.dp), tint = Color.White)
                            }
                        }
                        AndroidView(
                            factory = { ctx -> createWebView(ctx).also { web = it; if (savedInstanceState == null) it.loadUrl(url) } },
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                        // Chừa chỗ cho thanh điều khiển nổi dưới đáy.
                        Spacer(Modifier.navigationBarsPadding().height(84.dp))
                    }
                    BottomBar(Modifier.align(Alignment.BottomCenter), onMenu = { menu = true })
                    DownloadCorner(AppGraph.browserDownloads)
                    DownloadSheet(AppGraph.browserDownloads) { pickFolder.launch(null) }
                    val fb = isFacebookHost(host)
                    MonikaMenuSheet(
                        menu, { menu = false },
                        title = pageTitle.ifBlank { host }, subtitle = host,
                        actions = buildList {
                            add(SheetAction("Tiến", R.drawable.ic_fluent_chevron_right_24_regular, enabled = web?.canGoForward() == true) { web?.goForward() })
                            add(SheetAction("Tìm kiếm", R.drawable.ic_fluent_search_24_regular) { searching = true })
                            add(SheetAction("Tải lại", R.drawable.ic_fluent_arrow_clockwise_24_regular) { web?.reload() })
                            add(SheetAction("Về Monika", R.drawable.ic_fluent_home_24_regular, highlight = true) { finish() })
                            add(SheetAction("Chia sẻ link", R.drawable.ic_fluent_share_24_regular) {
                                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, web?.url.orEmpty()), "Chia sẻ"))
                            })
                            add(SheetAction("Sao chép link", R.drawable.ic_fluent_copy_24_regular) {
                                getSystemService(android.content.ClipboardManager::class.java)?.setPrimaryClip(android.content.ClipData.newPlainText("link", web?.url.orEmpty()))
                            })
                            // Facebook: không chặn quảng cáo (tránh lỗi đăng bài / tải ảnh).
                            if (adState == AdState.ON && !fb) add(SheetAction(if (adOn) "Chặn QC: bật ($blocked)" else "Chặn QC: tắt", R.drawable.ic_fluent_shield_checkmark_24_regular, highlight = adOn) {
                                adOn = !adOn; blocked = 0; web?.reload()
                            })
                        },
                    )
                }
            }
        }
    }

    /** Thanh điều khiển nổi dưới đáy (vùng ngón cái): ← · tên trang (bấm = tìm) · ⟳ · menu. */
    @Composable
    private fun BottomBar(modifier: Modifier, onMenu: () -> Unit) {
        val c = Monika.colors
        Row(
            modifier.navigationBarsPadding().padding(horizontal = 12.dp).padding(bottom = 12.dp).fillMaxWidth().height(64.dp)
                .clip(Radius.pill).background(Brush.verticalGradient(listOf(Color(0xFF34323A), Color(0xFF232227)))).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarIcon(R.drawable.ic_fluent_arrow_left_24_regular, "Quay lại") {
                val w = web
                if (w != null && w.canGoBack()) w.goBack() else finish()
            }
            if (searching) {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                BasicTextField(
                    query, { query = it }, singleLine = true,
                    textStyle = Monika.type.body.copy(color = Color.White), cursorBrush = SolidColor(c.accentCoral),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search(query) }),
                    decorationBox = { inner ->
                        Box(Modifier.clip(Radius.pill).background(Color(0x1FFFFFFF)).padding(horizontal = 14.dp, vertical = 10.dp)) {
                            if (query.isEmpty()) Text(if (isFacebookHost(host)) "Tìm trên Facebook…" else "Tìm kiếm Google…", style = Monika.type.body, color = Color(0x99FFFFFF))
                            inner()
                        }
                    },
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp).focusRequester(focus),
                )
                BarIcon(R.drawable.ic_fluent_dismiss_24_regular, "Đóng tìm kiếm") { searching = false }
            } else {
                Column(
                    Modifier.weight(1f).clip(Radius.pill).background(Color(0x14FFFFFF)).clickable { searching = true }.padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(pageTitle.ifBlank { host }, style = Monika.type.bodyStrong, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MonikaIcon(R.drawable.ic_fluent_search_24_regular, null, Modifier.size(12.dp), tint = Color(0xB3FFFFFF))
                        Text(" $host", style = Monika.type.caption, color = Color(0xB3FFFFFF), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                BarIcon(R.drawable.ic_fluent_arrow_clockwise_24_regular, "Tải lại") { web?.reload() }
                BarIcon(R.drawable.ic_fluent_grid_24_regular, "Menu", onMenu)
            }
        }
    }

    @Composable
    private fun BarIcon(@androidx.annotation.DrawableRes icon: Int, desc: String, onClick: () -> Unit) {
        Box(Modifier.size(48.dp).clip(Radius.pill).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            MonikaIcon(icon, desc, Modifier.size(22.dp), tint = Color.White)
        }
    }

    private fun isFacebookHost(h: String) = h.contains("facebook.com") || h.contains("fb.com") || h.contains("messenger.com")

    // ---- Đính kèm ảnh chụp game khi đăng bài hỏi nhóm ----
    private var attachment by mutableStateOf<File?>(null)

    // ---- Tải file: bắt đầu tải NGAY, popup chọn tên/nơi lưu; chọn thư mục riêng qua trình chọn của hệ thống ----
    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            val label = androidx.documentfile.provider.DocumentFile.fromTreeUri(this, uri)?.name ?: "Thư mục đã chọn"
            AppGraph.browserDownloads.pickedFolder = vn.aow.monika.download.SaveDest.Folder(uri.toString(), label)
        }
    }
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private val pickFile = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val uris = WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data)
        fileCallback?.onReceiveValue(uris)
        fileCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(ctx: Context) = WebView(ctx).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true // Discord web cần localStorage để giữ đăng nhập.
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = true
        setDownloadListener { url, ua, disposition, mime, _ ->
            if (url.startsWith("blob:") || url.startsWith("data:")) {
                android.widget.Toast.makeText(this@InAppBrowserActivity, "Trang này tạo file ngay trong trình duyệt, chưa tải được bằng Monika", android.widget.Toast.LENGTH_LONG).show()
            } else AppGraph.browserDownloads.start(url, ua, disposition, mime, referer = this.url)
        }
        settings.setSupportMultipleWindows(true) // để chủ động chặn cửa sổ bật lên (pop-up / pop-under) — xem onCreateWindow
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, p: Int) { this@InAppBrowserActivity.loadProgress = p / 100f }
            override fun onReceivedTitle(view: WebView, t: String?) { this@InAppBrowserActivity.pageTitle = t.orEmpty() }

            // Cửa sổ mới (target=_blank / window.open): chỉ cho khi người dùng vừa bấm, và mở NGAY trong trang này (không sinh cửa sổ ẩn).
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message): Boolean {
                if (!isUserGesture && popupsOn && adOn) { blocked++; return false }
                val tmp = WebView(view.context)
                tmp.webViewClient = object : WebViewClient() {
                    private var done = false
                    private fun redirect(u: String?) {
                        if (done || u.isNullOrBlank() || u == "about:blank") return
                        done = true
                        val h = Uri.parse(u).host.orEmpty().lowercase()
                        if (popupsOn && adOn && h.isNotEmpty() && AppGraph.adblock.blocks(h) && allow.none { h == it || h.endsWith(".$it") }) { blocked++ }
                        else view.post { view.loadUrl(u) }
                        view.post { tmp.destroy() }
                    }
                    override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean { redirect(r.url.toString()); return true }
                    override fun onPageStarted(v: WebView, url: String?, favicon: Bitmap?) { redirect(url) }
                }
                (resultMsg.obj as WebView.WebViewTransport).webView = tmp
                resultMsg.sendToTarget()
                return true
            }

            // Chọn ảnh để đăng: có ảnh chụp game đang chờ → đính kèm luôn; không thì mở trình chọn file.
            override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                fileCallback?.onReceiveValue(null)
                val att = attachment
                if (att != null) {
                    val uri = androidx.core.content.FileProvider.getUriForFile(this@InAppBrowserActivity, "$packageName.files", att)
                    callback.onReceiveValue(arrayOf(uri))
                    attachment = null
                    android.widget.Toast.makeText(this@InAppBrowserActivity, "Đã đính kèm ảnh chụp game", android.widget.Toast.LENGTH_SHORT).show()
                    return true
                }
                fileCallback = callback
                return runCatching { pickFile.launch(params.createIntent()); true }.getOrElse { fileCallback = null; false }
            }

            // Facebook / Discord xin camera, micro (gọi video, quay story): cho phép trên các trang đó.
            override fun onPermissionRequest(request: android.webkit.PermissionRequest) {
                val h = request.origin?.host.orEmpty()
                if (isFacebookHost(h) || h.contains("discord")) runOnUiThread { request.grant(request.resources) } else request.deny()
            }
        }
        webViewClient = object : WebViewClient() {
            // Chạy trên luồng nền của WebView: yêu cầu tới tên miền quảng cáo → trả về rỗng.
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (!adOn || adState != AdState.ON) return null
                // Không chặn gì trên Facebook (dễ hỏng đăng bài / tải ảnh).
                if (isFacebookHost(this@InAppBrowserActivity.host)) return null
                val h = request.url.host?.lowercase() ?: return null
                if (allow.any { h == it || h.endsWith(".$it") }) return null
                // Trang chính: chỉ chặn khi KHÔNG do người dùng bấm (chuyển hướng quảng cáo / pop-under).
                if (request.isForMainFrame && (request.hasGesture() || !popupsOn || h == startHost || !AppGraph.adblock.blocks(h))) return null
                val page = this@InAppBrowserActivity.host
                if (!request.isForMainFrame && !AppGraph.adblock.blocksRequest(request.url.toString(), h, page.ifBlank { h }, typeOf(request))) return null
                runOnUiThread { blocked++ }
                return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
            }

            override fun onPageFinished(view: WebView, url: String?) { hideAds(view) }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                if (u.scheme == "http" || u.scheme == "https") return false
                val intent = runCatching {
                    if (u.scheme == "intent") Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME) else Intent(Intent.ACTION_VIEW, u)
                }.getOrNull() ?: return true
                // Facebook: KHÔNG bao giờ nhảy sang app FB (bấm Quay lại sẽ kẹt trong app FB) → ở lại trong trình duyệt nhúng.
                if (isFacebookApp(u, intent)) {
                    intent.getStringExtra("browser_fallback_url")?.let { view.loadUrl(it) }
                    return true
                }
                // App khác (discord://, zalo…): mở app tương ứng nếu có.
                runCatching { startActivity(intent) }
                return true
            }
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                blocked = 0
                this@InAppBrowserActivity.host = url?.let { Uri.parse(it).host }.orEmpty()
                hideAds(view)
            }
        }
    }

    /** Loại tài nguyên đoán từ tiêu đề Accept / đuôi tệp (WebView không cho biết trực tiếp); 0 = không rõ. */
    private fun typeOf(r: WebResourceRequest): Int {
        val accept = r.requestHeaders["Accept"]?.lowercase().orEmpty()
        val path = r.url.path?.lowercase().orEmpty()
        return when {
            accept.startsWith("text/css") || path.endsWith(".css") -> FilterEngine.STYLE
            path.endsWith(".js") || path.endsWith(".mjs") -> FilterEngine.SCRIPT
            accept.contains("text/html") -> if (r.isForMainFrame) 0 else FilterEngine.SUBDOC
            accept.contains("image/") || Regex("""\.(png|jpe?g|gif|webp|avif|svg|ico)$""").containsMatchIn(path) -> FilterEngine.IMAGE
            Regex("""\.(woff2?|ttf|otf)$""").containsMatchIn(path) -> FilterEngine.FONT
            accept.startsWith("video/") || accept.startsWith("audio/") || Regex("""\.(mp4|webm|m3u8|mp3|ogg)$""").containsMatchIn(path) -> FilterEngine.MEDIA
            else -> 0
        }
    }

    /** Ẩn khung quảng cáo còn sót bằng CSS (luật ẩn phần tử theo trang) và giữ lại nếu trang gỡ đi. */
    private fun hideAds(view: WebView) {
        if (!adOn || adState != AdState.ON || !cosmeticOn) return
        val h = host
        if (h.isBlank() || isFacebookHost(h) || allow.any { h == it || h.endsWith(".$it") }) return
        val css = AppGraph.adblock.cosmeticCss(h)
        if (css.isEmpty()) return
        val js = "(function(){var id='__monika_ab';function add(){if(document.getElementById(id))return;var s=document.createElement('style');s.id=id;" +
            "s.textContent=" + org.json.JSONObject.quote(css) + ";(document.head||document.documentElement).appendChild(s);}" +
            "add();if(!window.__monika_ab_o){window.__monika_ab_o=new MutationObserver(add);window.__monika_ab_o.observe(document.documentElement,{childList:true});}})();"
        view.evaluateJavascript(js, null)
    }

    override fun onPause() {
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onDestroy() {
        // Đóng trình duyệt khi chưa chọn nơi lưu → lưu theo lựa chọn lần trước (file đã tải rồi, không để mất).
        AppGraph.browserDownloads.autoConfirmPending()
        web?.destroy()
        web = null
        super.onDestroy()
    }

    private fun isFacebookApp(u: Uri, i: Intent): Boolean {
        val scheme = u.scheme.orEmpty().lowercase()
        val target = (i.`package` ?: "") + " " + (i.data?.scheme ?: "") + " " + (i.data?.host ?: "")
        return scheme.startsWith("fb") || scheme == "messenger" || target.contains("facebook") || target.contains("fb") ||
            u.toString().contains("com.facebook", ignoreCase = true)
    }

    @Composable
    private fun ToolIcon(@androidx.annotation.DrawableRes icon: Int, desc: String, onClick: () -> Unit) {
        Box(Modifier.size(44.dp).clip(Radius.pill).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            MonikaIcon(icon, desc, Modifier.size(22.dp), tint = Monika.colors.text)
        }
    }

    companion object {
        private const val EXTRA_URL = "url"
        private const val EXTRA_ATTACH = "attach"

        /** @param attach ảnh (vd. chụp màn hình game) tự đính kèm khi trang mở hộp chọn ảnh. */
        fun start(context: Context, url: String, attach: File? = null) {
            context.startActivity(
                Intent(context, InAppBrowserActivity::class.java).putExtra(EXTRA_URL, url).putExtra(EXTRA_ATTACH, attach?.absolutePath)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
