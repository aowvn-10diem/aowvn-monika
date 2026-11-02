package vn.aow.monika.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import vn.aow.monika.AppGraph
import vn.aow.monika.feed.Post
import vn.aow.monika.ui.screens.LibraryScreen
import vn.aow.monika.ui.screens.OfficialBuildWarning
import vn.aow.monika.ui.screens.SettingsScreen
import vn.aow.monika.ui.theme.*

/** Bản Play mở vào Thư viện; tệp được người dùng đưa vào qua SAF. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        acceptFiles(intent)
        setContent {
            MonikaTheme {
                var page by rememberSaveable { mutableStateOf("library") }
                // [A78] V154: chứng thư Play App Signing khác bản GitHub; catalog Play để trống = bỏ kiểm.
                val config by AppGraph.config.config.collectAsState()
                BackHandler(page != "library") { page = "library" }
                Column(Modifier.fillMaxSize()) {
                    MonikaHeader("Aow Monika", right = { SoftPillButton("Bài viết", { page = "articles" }) })
                    // Bản Play mở vào Thư viện (trang chủ của flavor này): cảnh báo đỏ, không tắt được.
                    if (page == "library") OfficialBuildWarning(config.security.officialCertSha256)
                    Box(Modifier.weight(1f)) {
                        when (page) {
                            "settings" -> SettingsScreen(onBack = { page = "library" })
                            "articles" -> PlayArticles { page = "library" }
                            else -> LibraryScreen(onSettings = { page = "settings" })
                        }
                    }
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); acceptFiles(intent) }
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
    companion object {
        const val EXTRA_POST_ID = "post_id"
        const val EXTRA_OPEN_LIBRARY = "open_library"
    }
}

/** Trình đọc bài dạng văn bản: không WebView, không link tải hay đường sang trang bên ngoài. */
@Composable
private fun PlayArticles(onBack: () -> Unit) {
    var posts by remember { mutableStateOf<List<Post>>(emptyList()) }
    var selected by remember { mutableStateOf<Post?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        vn.aow.monika.diag.Diagnostics.opSuspend(AppGraph.app, vn.aow.monika.diag.Diagnostics.Op.FETCH_FEED) {
            AppGraph.feed.fetchList("play-articles")
        }.onSuccess { posts = it }.onFailure { error = "Không tải được bài viết. Hãy thử lại sau." }
    }
    BackHandler(selected != null) { selected = null }
    Screen {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { DarkButton(if (selected == null) "Về Thư viện" else "Danh sách bài", { if (selected != null) selected = null else onBack() }) }
            error?.let { item { Text(it, style = Monika.type.body, color = Monika.colors.textSecondary) } }
            val post = selected
            if (post != null) {
                item { Text(post.title, style = Monika.type.sectionTitle, color = Monika.colors.text) }
                item {
                    val text = remember(post) {
                        android.text.Html.fromHtml(post.contentHtml, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
                            .replace(Regex("https?://\\S+"), "")
                    }
                    Text(text, style = Monika.type.body, color = Monika.colors.text)
                }
            } else items(posts, key = { it.id }) { p ->
                DarkButton(p.title, { selected = p }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
