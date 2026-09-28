package vn.aow.monika.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import vn.aow.monika.AppGraph
import vn.aow.monika.download.DownloadLink
import vn.aow.monika.download.LinkResolver
import vn.aow.monika.feed.Post

@Composable
fun PostScreen(postId: String, onBack: () -> Unit) {
    val state by produceState<Result<Post>?>(null, postId) {
        value = runCatching { AppGraph.feed.fetchPost(postId) }
    }
    val result = state
    when {
        result == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        result.isFailure -> Column(Modifier.padding(16.dp)) {
            Text("Không mở được bài: ${result.exceptionOrNull()?.message}")
            TextButton(onBack) { Text("Quay lại") }
        }
        else -> PostContent(result.getOrThrow(), onBack)
    }
}

@Composable
private fun PostContent(post: Post, onBack: () -> Unit) {
    val context = LocalContext.current
    val cfg by AppGraph.config.config.collectAsState()
    val links = remember(post, cfg) { LinkResolver.extract(post.contentHtml, cfg.downloadHosts) }
    val summary = remember(post) {
        HtmlCompat.fromHtml(post.contentHtml, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim().take(1200)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        post.thumbnail?.let {
            AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onBack) { Text("← Quay lại") }
            Text(post.title, style = MaterialTheme.typography.titleLarge)
            Text(post.labels.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall)

            Text("Tải game", style = MaterialTheme.typography.titleMedium)
            if (links.isEmpty()) Text("Bài này chưa có link tải nhận diện được. Hãy mở bài trên web.")
            links.forEach { DownloadRow(context, it) }

            Card {
                Text(
                    "Mẹo: link mở bằng trình duyệt → tải xong vào tab Thư viện → \"Thêm game từ máy\" để chơi.",
                    Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedButton({ openUrl(context, post.url) }, Modifier.fillMaxWidth()) { Text("Xem bài đầy đủ trên aow.vn") }
            Text(summary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DownloadRow(context: Context, link: DownloadLink) {
    val scope = rememberCoroutineScope()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(link.hostName, Modifier.weight(1f))
        if (link.directUrl != null) {
            Button({
                scope.launch {
                    runCatching { AppGraph.downloader.enqueue(link.directUrl) }
                        .onSuccess { Toast.makeText(context, "Đang tải… xem tiến độ ở thanh thông báo", Toast.LENGTH_LONG).show() }
                        .onFailure { Toast.makeText(context, "Lỗi: ${it.message}", Toast.LENGTH_LONG).show() }
                }
            }) { Text("Tải trong app") }
        } else {
            OutlinedButton({ openUrl(context, link.pageUrl) }) { Text("Mở link") }
        }
    }
}

fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        .onFailure { Toast.makeText(context, "Không mở được link", Toast.LENGTH_SHORT).show() }
}
