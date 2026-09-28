package vn.aow.monika.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import vn.aow.monika.AppGraph
import vn.aow.monika.R
import vn.aow.monika.feed.Post
import vn.aow.monika.ui.theme.Fluent
import vn.aow.monika.ui.theme.FluentButton
import vn.aow.monika.ui.theme.FluentButtonStyle
import vn.aow.monika.ui.theme.FluentCard
import vn.aow.monika.ui.theme.FluentEmptyState
import vn.aow.monika.ui.theme.FluentIconButton
import vn.aow.monika.ui.theme.FluentPillBar
import vn.aow.monika.ui.theme.FluentSpinner
import vn.aow.monika.ui.theme.FluentTag
import vn.aow.monika.ui.theme.FluentTopBar

/** Giữ danh sách bài khi chuyển qua lại giữa các màn hình. */
class FeedViewModel : ViewModel() {
    val posts = mutableStateListOf<Post>()
    var label by mutableStateOf<String?>(null); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var endReached by mutableStateOf(false); private set

    init { load(reset = true) }

    fun selectLabel(value: String?) {
        if (value == label) return
        label = value
        load(reset = true)
    }

    fun load(reset: Boolean) {
        if (loading) return
        loading = true
        error = null
        viewModelScope.launch {
            runCatching { AppGraph.feed.fetch(label = label, startIndex = if (reset) 1 else posts.size + 1) }
                .onSuccess {
                    if (reset) posts.clear()
                    posts.addAll(it)
                    endReached = it.isEmpty()
                }
                .onFailure { error = it.message ?: "Lỗi mạng" }
            loading = false
        }
    }
}

@Composable
fun FeedScreen(onOpen: (Post) -> Unit, vm: FeedViewModel = viewModel()) {
    val cfg by AppGraph.config.config.collectAsState()
    val c = Fluent.colors
    LaunchedEffect(Unit) { if (vm.posts.isEmpty() && !vm.loading) vm.load(reset = true) }

    Column(Modifier.fillMaxSize().background(c.background2)) {
        FluentTopBar("AowVN Monika", subtitle = "Game Việt hóa từ aow.vn", actions = {
            FluentIconButton(R.drawable.ic_fluent_arrow_clockwise_24_regular, "Tải lại", { vm.load(reset = true) })
        })
        FluentPillBar(listOf<String?>(null) + cfg.feedLabels(), vm.label, { it ?: "Tất cả" }, vm::selectLabel)
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(vm.posts, key = { it.id }) { post -> PostCard(post) { onOpen(post) } }
            item {
                Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                    when {
                        vm.loading -> FluentSpinner()
                        vm.error != null -> FluentEmptyState(
                            R.drawable.ic_fluent_alert_24_regular, "Không tải được bài viết", vm.error.orEmpty(),
                        ) { FluentButton("Thử lại", { vm.load(reset = vm.posts.isEmpty()) }, style = FluentButtonStyle.Outline) }
                        !vm.endReached && vm.posts.isNotEmpty() ->
                            FluentButton("Xem thêm bài", { vm.load(reset = false) }, style = FluentButtonStyle.Subtle)
                    }
                }
            }
        }
    }
}

@Composable
private fun PostCard(post: Post, onClick: () -> Unit) {
    val c = Fluent.colors
    FluentCard(Modifier.fillMaxWidth(), onClick = onClick, padding = PaddingValues(0.dp)) {
        post.thumbnail?.let {
            AsyncImage(
                model = it, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(c.background3),
            )
        }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(post.title, style = Fluent.type.body1Strong, color = c.foreground1, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(formatDate(post.published), style = Fluent.type.caption1, color = c.foreground3)
                post.labels.take(2).forEach { FluentTag(it.removeSuffix(" Việt Hóa").removeSuffix(" Việt Hoá"), brand = true) }
            }
        }
    }
}

/** "2026-09-03T05:49:26..." → "03/09/2026". */
fun formatDate(iso: String): String =
    iso.take(10).split("-").takeIf { it.size == 3 }?.let { (y, m, d) -> "$d/$m/$y" } ?: iso
