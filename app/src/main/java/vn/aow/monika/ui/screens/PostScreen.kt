package vn.aow.monika.ui.screens

import vn.aow.monika.ui.theme.GradientProgress
import vn.aow.monika.download.DownloadWatch
import vn.aow.monika.download.DownloadState
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.LaunchedEffect
import android.app.DownloadManager
import vn.aow.monika.community.Community
import vn.aow.monika.community.CommunityLink
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import vn.aow.monika.AppGraph
import vn.aow.monika.R
import vn.aow.monika.download.DownloadLink
import vn.aow.monika.download.LinkResolver
import vn.aow.monika.feed.Post
import vn.aow.monika.library.GameMeta
import vn.aow.monika.ui.theme.CircleButton
import vn.aow.monika.ui.theme.CircleStyle
import vn.aow.monika.ui.theme.DarkButton
import vn.aow.monika.ui.theme.EmptyState
import vn.aow.monika.ui.theme.GradientButton
import vn.aow.monika.ui.theme.Monika
import vn.aow.monika.ui.theme.MonikaColors
import vn.aow.monika.ui.theme.Screen
import vn.aow.monika.ui.theme.Spinner
import vn.aow.monika.ui.theme.softShadow
import vn.aow.monika.ui.theme.Radius

@Composable
fun PostScreen(postId: String, onBack: () -> Unit) {
    val state by produceState<Result<Post>?>(null, postId) { value = runCatching { AppGraph.feed.fetchPost(postId) } }
    val result = state
    Screen {
        when {
            result == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { Spinner() }
            result.isFailure -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                EmptyState(R.drawable.fluent3d_newspaper, "Không mở được bài", result.exceptionOrNull()?.message.orEmpty()) {
                    DarkButton("Quay lại", onBack)
                }
            }
            else -> PostContent(result.getOrThrow(), onBack)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostContent(post: Post, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cfg by AppGraph.config.config.collectAsState()
    val c = Monika.colors
    val links = remember(post, cfg) { LinkResolver.extract(post.contentHtml, cfg.downloadHosts) }
    val html = remember(post, c) { renderHtml(post, c) }
    var showLinks by remember { mutableStateOf(false) }
    // Lượt tải của bài này: hiện tiến trình ngay trên thanh dưới (mở lại bài vẫn thấy).
    var downloadId by remember(post.id) { mutableStateOf<Long?>(null) }
    LaunchedEffect(post.id) { downloadId = withContext(Dispatchers.IO) { DownloadWatch.activeForPost(context, post.id) } }
    val dl by produceState<DownloadState?>(null, downloadId) {
        val id = downloadId ?: run { value = null; return@produceState }
        DownloadWatch.watch(context, id).collect { value = it }
    }
    val startDownload: (String) -> Unit = { url -> handleLink(context, scope, url, post) { id -> downloadId = id } }
    // Link FB / Discord trong bài (thường của nhóm dịch; bản dịch cần vào Discord để lấy file / báo lỗi).
    val community = remember(post) { Community.extract(post.contentHtml) }
    var showCommunity by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        // Nội dung đầy đủ (ảnh lớn + tiêu đề + bài) trong WebView, CSS theo hệ thiết kế Monika.
        AndroidView(
            factory = { ctx -> createPostWebView(ctx, scope, post) },
            update = { it.loadDataWithBaseURL("https://www.aow.vn/", html, "text/html", "utf-8", null) },
            modifier = Modifier.fillMaxSize(),
        )
        // Nút nổi trên ảnh: quay lại / chia sẻ / mở web.
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleButton(R.drawable.ic_fluent_arrow_left_24_regular, "Quay lại", onBack, style = CircleStyle.Glass)
            Box(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (community.isNotEmpty()) CircleButton(R.drawable.ic_fluent_people_community_24_regular, "Nhóm dịch", { showCommunity = true }, style = CircleStyle.Glass)
                CircleButton(R.drawable.ic_fluent_share_24_regular, "Chia sẻ", {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "${post.title}\n${post.url}"), "Chia sẻ"))
                }, style = CircleStyle.Glass)
                CircleButton(R.drawable.ic_fluent_open_24_regular, "Mở trên web", { openUrl(context, post.url) }, style = CircleStyle.Glass)
            }
        }
        // Thanh hành động nổi (vùng ngón cái): tải game.
        if (links.isNotEmpty()) {
            val direct = links.firstOrNull { it.directUrl != null }
            Row(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 18.dp)
                    .softShadow(Radius.pill, floating = true)
                    .clip(Radius.pill).background(Color(0xFF201F21)).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DarkButton("Liên kết", { showLinks = true }, icon = R.drawable.ic_fluent_open_24_regular, subtitle = "${links.size} link")
                val d = dl
                if (d != null) DownloadBar(d, Modifier.weight(1f)) else GradientButton(
                    if (direct != null) "Tải game" else "Xem link tải",
                    { if (direct != null) startDownload(direct.pageUrl) else showLinks = true },
                    Modifier.weight(1f),
                    icon = R.drawable.ic_fluent_arrow_download_24_regular,
                    subtitle = direct?.let { listOf(it.label, it.hostName).filter(String::isNotBlank).distinct().joinToString(" · ") } ?: "Mở trình duyệt",
                )
            }
        }
    }

    if (showCommunity) {
        ModalBottomSheet(onDismissRequest = { showCommunity = false }, containerColor = c.surface) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Nhóm dịch & cộng đồng", style = Monika.type.sectionTitle, color = c.text)
                Text("Link trong bài. Facebook mở ngay trong Aow Monika. Discord mở bằng app nếu máy đã cài.", style = Monika.type.caption, color = c.textSecondary)
                community.forEach { l ->
                    val title = "${l.kind.label} · ${Community.shortName(l)}"
                    val icon = if (l.kind == CommunityLink.Kind.DISCORD) R.drawable.ic_fluent_chat_multiple_24_regular else R.drawable.ic_fluent_people_community_24_regular
                    if (l.kind == CommunityLink.Kind.DISCORD) GradientButton(title, { showCommunity = false; Community.open(context, l.url) }, Modifier.fillMaxWidth(), icon = icon)
                    else DarkButton(title, { showCommunity = false; Community.open(context, l.url) }, Modifier.fillMaxWidth(), icon = icon)
                }
            }
        }
    }

    if (showLinks) {
        ModalBottomSheet(onDismissRequest = { showLinks = false }, containerColor = c.surface) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Liên kết tải trong bài", style = Monika.type.sectionTitle, color = c.text)
                Text("Nút gradient: tải thẳng trong app. Nút tối: mở trình duyệt, tải xong vào Giả lập → Thêm game.", style = Monika.type.caption, color = c.textSecondary)
                links.forEach { link -> LinkButton(link) { showLinks = false; startDownload(link.pageUrl) } }
            }
        }
    }
}

/** Tiến trình tải ngay trong bài: % + tốc độ; tải xong báo đang giải nén vào thư viện. */
@Composable
private fun DownloadBar(d: DownloadState, modifier: Modifier) {
    Column(modifier.height(56.dp).padding(horizontal = 12.dp), verticalArrangement = Arrangement.Center) {
        val title = when {
            d.status == DownloadManager.STATUS_SUCCESSFUL -> "Tải xong · đang giải nén vào Thư viện"
            d.status == DownloadManager.STATUS_FAILED -> "Tải lỗi · xem tab Tải xuống"
            d.status == DownloadManager.STATUS_PAUSED -> "Tạm dừng (chờ mạng) ${(d.progress * 100).toInt()}%"
            d.total <= 0 -> "Đang bắt đầu tải…"
            else -> "Đang tải ${(d.progress * 100).toInt()}%  ·  ${"%.1f".format(d.speed / 1_048_576.0).replace('.', ',')} MB/s"
        }
        Text(title, style = Monika.type.bodyStrong, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        GradientProgress(if (d.status == DownloadManager.STATUS_SUCCESSFUL) 1f else d.progress, height = 6.dp)
    }
}

@Composable
private fun LinkButton(link: DownloadLink, onClick: () -> Unit) {
    val title = listOf(link.label.ifBlank { "Tải" }, link.hostName).distinct().joinToString(" · ")
    if (link.directUrl != null) GradientButton(title, onClick, Modifier.fillMaxWidth(), icon = R.drawable.ic_fluent_arrow_download_24_regular)
    else DarkButton(title, onClick, Modifier.fillMaxWidth(), icon = R.drawable.ic_fluent_open_24_regular)
}

@SuppressLint("SetJavaScriptEnabled")
private fun createPostWebView(context: Context, scope: CoroutineScope, post: Post) = WebView(context).apply {
    setBackgroundColor(0)
    settings.javaScriptEnabled = true // Cho video YouTube nhúng trong bài.
    settings.domStorageEnabled = true
    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            handleLink(context, scope, request.url.toString(), post)
            return true
        }
    }
}

/** Bấm link: host tải thẳng → tải trong app (kèm tên + ảnh bìa từ bài); còn lại → trình duyệt. */
fun handleLink(context: Context, scope: CoroutineScope, url: String, post: Post? = null, onStarted: (Long) -> Unit = {}) {
    val direct = LinkResolver.resolve(url, AppGraph.config.current.downloadHosts)?.directUrl
    if (direct == null) {
        openUrl(context, url)
        return
    }
    val meta = post?.let {
        GameMeta(GameMeta.cleanTitle(it.title), GameMeta.largeCover(it.thumbnail), it.url, it.id, it.labels)
    }
    scope.launch {
        runCatching { AppGraph.downloader.enqueue(direct, meta = meta) }
            .onSuccess { id -> onStarted(id) }
            .onFailure { Toast.makeText(context, "Lỗi: ${it.message}", Toast.LENGTH_LONG).show() }
    }
}

private fun Color.css() = "#%06X".format(toArgb() and 0xFFFFFF)

private fun renderHtml(post: Post, c: MonikaColors): String {
    val hero = vn.aow.monika.feed.Thumbs.hero(post.thumbnail)
    return """
<!doctype html><html><head>
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>
  body{margin:0;background:${c.bg.css()};color:${c.text.css()};font-family:Manrope,system-ui,-apple-system,Roboto,sans-serif;
       font-size:16px;line-height:1.6;word-wrap:break-word}
  .hero{position:relative;height:62vw;max-height:420px;background:#1E1D1F center/cover no-repeat;${if (hero != null) "background-image:url('$hero');" else ""}}
  .hero:after{content:"";position:absolute;inset:0;background:linear-gradient(180deg,rgba(0,0,0,.25),rgba(0,0,0,0) 35%,${c.bg.css()} 100%)}
  .wrap{padding:0 16px 140px;margin-top:-40px;position:relative}
  .badges span{display:inline-block;font-size:12px;font-weight:600;color:#fff;padding:4px 12px;border-radius:999px;margin:0 6px 6px 0;
       background:linear-gradient(110deg,#FFB052,#FF7F78 50%,#E95CC8)}
  h1{font-size:26px;line-height:1.25;font-weight:700;margin:6px 0 6px}
  .meta{color:${c.textSecondary.css()};font-size:13px;margin-bottom:18px}
  h2,h3,h4{font-weight:700;line-height:1.3;margin:24px 0 8px}
  a{color:${c.accentCoral.css()};text-decoration:none}
  img{max-width:100%!important;height:auto!important;border-radius:20px}
  iframe{max-width:100%;border:0;border-radius:20px}
  .separator a{margin:0!important}
  blockquote{margin:14px 0;padding:14px;background:${c.surface.css()};border-radius:20px;box-shadow:0 8px 24px rgba(30,24,24,.08)}
  pre,code{background:${c.surfaceSoft.css()};border-radius:10px;padding:3px 8px;white-space:pre-wrap;font-size:14px}
  table{max-width:100%;border-collapse:collapse}
  td,th{border:1px solid ${c.track.css()};padding:6px}
  details{background:${c.surface.css()};border-radius:16px;padding:10px 14px;margin:10px 0}
  ul.button{list-style:none;padding:0;display:flex;flex-wrap:wrap;gap:8px;justify-content:center}
  ul.button li a{display:inline-block;padding:12px 20px;border-radius:999px;font-weight:600;font-size:14px;color:#fff;
       background:linear-gradient(110deg,#FFB052,#FF7F78 50%,#E95CC8)}
  ul.button li a.demo{background:#1E1D1F}
</style></head><body>
<div class="hero"></div>
<div class="wrap">
<div class="badges">${post.labels.take(3).joinToString("") { "<span>${shortLabel(it).htmlEscape()}</span>" }}</div>
<h1>${post.title.htmlEscape()}</h1>
<div class="meta">AowVN · ${formatDate(post.published)}</div>
${post.contentHtml}
</div>
</body></html>
"""
}

private fun String.htmlEscape() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        .onFailure { Toast.makeText(context, "Không mở được link", Toast.LENGTH_SHORT).show() }
}
