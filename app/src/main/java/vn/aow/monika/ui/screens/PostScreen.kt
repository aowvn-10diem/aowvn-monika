package vn.aow.monika.ui.screens

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import vn.aow.monika.ui.theme.Fluent
import vn.aow.monika.ui.theme.FluentButton
import vn.aow.monika.ui.theme.FluentButtonStyle
import vn.aow.monika.ui.theme.FluentColors
import vn.aow.monika.ui.theme.FluentDivider
import vn.aow.monika.ui.theme.FluentEmptyState
import vn.aow.monika.ui.theme.FluentIconButton
import vn.aow.monika.ui.theme.FluentSpinner
import vn.aow.monika.ui.theme.FluentTopBar

@Composable
fun PostScreen(postId: String, onBack: () -> Unit) {
    val state by produceState<Result<Post>?>(null, postId) {
        value = runCatching { AppGraph.feed.fetchPost(postId) }
    }
    val result = state
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().background(Fluent.colors.background1)) {
        FluentTopBar(
            title = result?.getOrNull()?.title ?: "Bài viết",
            navigation = { FluentIconButton(R.drawable.ic_fluent_arrow_left_24_regular, "Quay lại", onBack) },
            actions = {
                result?.getOrNull()?.let { post ->
                    FluentIconButton(R.drawable.ic_fluent_open_24_regular, "Mở trên web", { openUrl(context, post.url) })
                }
            },
        )
        when {
            result == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { FluentSpinner() }
            result.isFailure -> FluentEmptyState(
                R.drawable.ic_fluent_alert_24_regular, "Không mở được bài", result.exceptionOrNull()?.message.orEmpty(),
            ) { FluentButton("Quay lại", onBack, style = FluentButtonStyle.Outline) }
            else -> PostContent(result.getOrThrow())
        }
    }
}

@Composable
private fun PostContent(post: Post) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cfg by AppGraph.config.config.collectAsState()
    val c = Fluent.colors
    val links = remember(post, cfg) { LinkResolver.extract(post.contentHtml, cfg.downloadHosts) }
    val html = remember(post, c) { renderHtml(post, c) }

    Column(Modifier.fillMaxSize()) {
        if (links.isNotEmpty()) {
            // Thanh tải nhanh: luôn nằm trên, không phải cuộn tìm nút tải.
            Column(Modifier.fillMaxWidth().background(c.background2)) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(links) { link ->
                        val title = listOf(link.label.ifBlank { "Tải" }, link.hostName).distinct().joinToString(" · ")
                        FluentButton(
                            title,
                            { handleLink(context, scope, link.pageUrl) },
                            style = if (link.directUrl != null) FluentButtonStyle.Accent else FluentButtonStyle.Outline,
                            icon = if (link.directUrl != null) R.drawable.ic_fluent_arrow_download_24_regular else R.drawable.ic_fluent_open_24_regular,
                        )
                    }
                }
                Text(
                    "Nút đậm: tải thẳng trong app. Nút viền: mở trình duyệt, tải xong vào Thư viện → Thêm game.",
                    style = Fluent.type.caption2, color = c.foreground3,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
                FluentDivider()
            }
        }
        // Nội dung bài đầy đủ (feed trả nguyên bài), hiển thị bằng WebView với CSS theo Fluent.
        AndroidView(
            factory = { ctx -> createPostWebView(ctx, scope) },
            update = { it.loadDataWithBaseURL("https://www.aow.vn/", html, "text/html", "utf-8", null) },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createPostWebView(context: Context, scope: CoroutineScope) = WebView(context).apply {
    setBackgroundColor(0)
    settings.javaScriptEnabled = true // Cho video YouTube nhúng trong bài.
    settings.domStorageEnabled = true
    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            handleLink(context, scope, request.url.toString())
            return true
        }
    }
}

/** Bấm link trong bài: host tải thẳng → tải trong app; còn lại → trình duyệt. */
private fun handleLink(context: Context, scope: CoroutineScope, url: String) {
    val link: DownloadLink? = LinkResolver.resolve(url, AppGraph.config.current.downloadHosts)
    val direct = link?.directUrl
    if (direct == null) {
        openUrl(context, url)
        return
    }
    scope.launch {
        runCatching { AppGraph.downloader.enqueue(direct) }
            .onSuccess { Toast.makeText(context, "Đang tải… xem tiến độ ở thanh thông báo", Toast.LENGTH_LONG).show() }
            .onFailure { Toast.makeText(context, "Lỗi: ${it.message}", Toast.LENGTH_LONG).show() }
    }
}

private fun Color.css() = "#%06X".format(toArgb() and 0xFFFFFF)

private fun renderHtml(post: Post, c: FluentColors): String = """
<!doctype html><html><head>
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>
  body{margin:0;padding:16px;background:${c.background1.css()};color:${c.foreground1.css()};
       font-family:system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;font-size:16px;line-height:1.55;word-wrap:break-word}
  h1{font-size:22px;line-height:1.3;font-weight:600;margin:0 0 6px}
  .meta{color:${c.foreground3.css()};font-size:13px;margin-bottom:16px}
  h2,h3,h4{font-weight:600;line-height:1.3;margin:20px 0 8px}
  a{color:${c.brandForeground.css()};text-decoration:none}
  img{max-width:100%!important;height:auto!important;border-radius:8px}
  iframe{max-width:100%;border:0;border-radius:8px}
  .separator a{margin:0!important}
  blockquote{margin:12px 0;padding:12px;background:${c.background3.css()};border-radius:8px;border:1px solid ${c.stroke2.css()}}
  pre,code{background:${c.background3.css()};border-radius:4px;padding:2px 6px;white-space:pre-wrap;font-size:14px}
  table{max-width:100%;border-collapse:collapse}
  td,th{border:1px solid ${c.stroke2.css()};padding:6px}
  details{background:${c.background2.css()};border:1px solid ${c.stroke2.css()};border-radius:8px;padding:8px 12px;margin:8px 0}
  ul.button{list-style:none;padding:0;display:flex;flex-wrap:wrap;gap:8px;justify-content:center}
  ul.button li a{display:inline-block;padding:9px 16px;border-radius:4px;font-weight:600;font-size:14px;
       background:${c.brandBackground.css()};color:${c.onBrand.css()}}
  ul.button li a.demo{background:transparent;color:${c.foreground1.css()};border:1px solid ${c.stroke1.css()}}
</style></head><body>
<h1>${post.title.htmlEscape()}</h1>
<div class="meta">${formatDate(post.published)} · ${post.labels.joinToString(" · ").htmlEscape()}</div>
${post.contentHtml}
</body></html>
"""

private fun String.htmlEscape() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        .onFailure { Toast.makeText(context, "Không mở được link", Toast.LENGTH_SHORT).show() }
}
