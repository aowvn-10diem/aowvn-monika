package vn.aow.monika.ui.screens

import vn.aow.monika.ui.theme.MonikaIcon
import vn.aow.monika.feed.Thumbs
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
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
import vn.aow.monika.ui.theme.ChipBar
import vn.aow.monika.ui.theme.DarkButton
import vn.aow.monika.ui.theme.DockClearance
import vn.aow.monika.ui.theme.EmptyState
import vn.aow.monika.ui.theme.Monika
import vn.aow.monika.ui.theme.MonikaCard
import vn.aow.monika.ui.theme.Radius
import vn.aow.monika.ui.theme.Screen
import vn.aow.monika.ui.theme.SoftPillButton
import vn.aow.monika.ui.theme.Spinner
import vn.aow.monika.ui.theme.Tag
import vn.aow.monika.ui.theme.artworkScrim

/** Giữ danh sách bài, bộ lọc và từ khóa khi chuyển tab. */
class FeedViewModel : ViewModel() {
    val posts = mutableStateListOf<Post>()
    var label by mutableStateOf<String?>(null); private set
    var query by mutableStateOf(""); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var endReached by mutableStateOf(false); private set

    // Hiện ngay trang đầu đã lưu (nếu có), rồi tải bản mới.
    init {
        // Từ Trang chủ chạm 1 hệ máy → mở sẵn đúng bộ lọc.
        GamesFilter.take()?.let { label = it }
        AppGraph.feed.cachedList(key())?.let { posts.addAll(it) }; load(reset = true)
    }

    private fun key() = "games:" + (label ?: "")

    fun selectLabel(value: String?) {
        if (value == label) return
        label = value
        load(reset = true)
    }

    fun search(value: String) {
        query = value
        load(reset = true)
    }

    fun load(reset: Boolean) {
        if (loading) return
        loading = true
        error = null
        viewModelScope.launch {
            runCatching {
                if (reset && query.isBlank()) AppGraph.feed.fetchList(key(), label = label)
                else AppGraph.feed.fetch(label = label, startIndex = if (reset) 1 else posts.size + 1, query = query)
            }
                .onSuccess {
                    if (reset) posts.clear()
                    posts.addAll(it.filter { p -> posts.none { old -> old.id == p.id } })
                    endReached = it.isEmpty()
                }
                .onFailure { error = it.message ?: "Lỗi mạng" }
            loading = false
        }
    }
}

@Composable
fun GamesScreen(onOpen: (Post) -> Unit, focusSearch: Boolean = false, vm: FeedViewModel = viewModel()) {
    // Màn còn sống khi đổi tab: nhận bộ lọc hệ máy mà Trang chủ vừa chọn.
    androidx.compose.runtime.LaunchedEffect(Unit) { GamesFilter.take()?.let(vm::selectLabel) }
    val c = Monika.colors
    val cfg by AppGraph.config.config.collectAsState()
    var text by remember { mutableStateOf(vm.query) }
    LaunchedEffect(Unit) { if (vm.posts.isEmpty() && !vm.loading) vm.load(reset = true) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(focusSearch) { if (focusSearch) runCatching { focus.requestFocus() } }

    Screen {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = DockClearance), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Đầu trang tối kiểu "Tìm kiếm" của design: ô tìm + bộ lọc.
            item {
                Column(
                    Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                        .background(c.surfaceDark).statusBarsPadding().padding(bottom = 16.dp),
                ) {
                    Text("Game & bài viết", style = Monika.type.sectionTitle, color = Color.White, modifier = Modifier.padding(16.dp))
                    Row(
                        Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(52.dp).clip(Radius.pill).background(c.surface).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MonikaIcon(R.drawable.ic_fluent_search_24_regular, null, Modifier.size(22.dp), tint = c.text)
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.weight(1f)) {
                            if (text.isEmpty()) Text("Tìm game, hướng dẫn, tin tức…", style = Monika.type.body, color = c.textTertiary)
                            BasicTextField(
                                text, { text = it }, singleLine = true,
                                textStyle = Monika.type.body.copy(color = c.text), cursorBrush = SolidColor(c.accentCoral),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { vm.search(text.trim()) }),
                                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                            )
                        }
                        if (text.isNotEmpty()) {
                            Icon(
                                painterResource(R.drawable.ic_fluent_dismiss_24_regular), "Xóa",
                                Modifier.size(28.dp).clip(Radius.pill).clickable { text = ""; vm.search("") }.padding(4.dp),
                                tint = c.textSecondary,
                            )
                        }
                    }
                }
            }
            item {
                ChipBar(listOf<String?>(null) + cfg.feedLabels(), vm.label, { it?.let(::shortLabel) ?: "Tất cả" }, vm::selectLabel, accent = true)
            }
            if (vm.query.isNotBlank()) item {
                Text("Kết quả cho \"${vm.query}\"", style = Monika.type.body, color = c.textSecondary, modifier = Modifier.padding(horizontal = 16.dp))
            }
            vm.posts.firstOrNull()?.let { first -> item(key = "featured-" + first.id) { FeaturedArticle(first) { onOpen(first) } } }
            items(vm.posts.drop(1), key = { it.id }) { p -> ArticleRow(p) { onOpen(p) } }
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    when {
                        vm.loading -> FeedStatus(true) { vm.load(reset = vm.posts.isEmpty()) }
                        vm.error != null -> FeedStatus(false) { vm.load(reset = vm.posts.isEmpty()) }
                        vm.posts.isEmpty() -> EmptyState(R.drawable.fluent3d_newspaper, "Không có kết quả", "Thử từ khóa khác.")
                        !vm.endReached -> SoftPillButton("Xem thêm bài", { vm.load(reset = false) })
                    }
                }
            }
        }
    }
}

@Composable
private fun FeaturedArticle(p: Post, onClick: () -> Unit) {
    MonikaCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), dark = true, shape = Radius.hero, padding = PaddingValues(0.dp), onClick = onClick) {
        Box(Modifier.fillMaxWidth().height(260.dp)) {
            AsyncImage(Thumbs.card(p.thumbnail), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(artworkScrim()))
            Column(Modifier.align(Alignment.BottomStart).padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag("Nổi bật", accent = true)
                Text(p.title, style = Monika.type.sectionTitle, color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(formatDate(p.published), style = Monika.type.caption, color = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun ArticleRow(p: Post, onClick: () -> Unit) {
    val c = Monika.colors
    MonikaCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = Radius.medium, padding = PaddingValues(10.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                Thumbs.card(p.thumbnail), null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 120.dp, height = 90.dp).clip(Radius.thumb).background(c.surfaceSoft),
            )
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                p.labels.firstOrNull()?.let {
                    Text(shortLabel(it).uppercase(), style = Monika.type.caption, color = c.accentCoral, maxLines = 1)
                }
                Text(p.title, style = Monika.type.bodyStrong, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(formatDate(p.published), style = Monika.type.caption, color = c.textTertiary)
            }
        }
    }
}
