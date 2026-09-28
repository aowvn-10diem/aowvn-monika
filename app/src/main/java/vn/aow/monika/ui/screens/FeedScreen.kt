package vn.aow.monika.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import vn.aow.monika.feed.Post

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
    LaunchedEffect(Unit) { if (vm.posts.isEmpty() && !vm.loading) vm.load(reset = true) }

    Column(Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { FilterChip(vm.label == null, { vm.selectLabel(null) }, { Text("Tất cả") }) }
            items(cfg.feedLabels()) { l ->
                FilterChip(vm.label == l, { vm.selectLabel(l) }, { Text(l) })
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(vm.posts, key = { it.id }) { post -> PostCard(post) { onOpen(post) } }
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    when {
                        vm.loading -> CircularProgressIndicator()
                        vm.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Không tải được bài viết: ${vm.error}", color = MaterialTheme.colorScheme.error)
                            OutlinedButton({ vm.load(reset = vm.posts.isEmpty()) }) { Text("Thử lại") }
                        }
                        !vm.endReached -> OutlinedButton({ vm.load(reset = false) }) { Text("Tải thêm") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PostCard(post: Post, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        post.thumbnail?.let {
            AsyncImage(
                model = it, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            )
        }
        Column(Modifier.padding(12.dp)) {
            Text(post.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                post.published.take(10) + "  ·  " + post.labels.take(3).joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
