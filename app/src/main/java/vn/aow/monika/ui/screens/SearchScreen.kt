package vn.aow.monika.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.aow.monika.AppGraph
import vn.aow.monika.R
import vn.aow.monika.feed.Post
import vn.aow.monika.feed.Thumbs
import vn.aow.monika.library.Game
import vn.aow.monika.library.GameMeta
import vn.aow.monika.ui.theme.DockClearance
import vn.aow.monika.ui.theme.Monika
import vn.aow.monika.ui.theme.Radius
import vn.aow.monika.ui.theme.Screen
import vn.aow.monika.ui.theme.Spinner
import vn.aow.monika.ui.theme.pressable
import java.text.Normalizer

/**
 * Tab Tìm kiếm (khác tab Game):
 * - Chưa gõ: lịch sử tìm, game/bài đã xem gần đây, gợi ý theo hệ máy.
 * - Đang gõ: gợi ý TỨC THÌ (không chờ mạng) từ Thư viện + danh mục bài aow.vn đã lưu trên máy.
 * - Bấm tìm: thêm kết quả tìm toàn văn trên aow.vn.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(onOpenPost: (Post) -> Unit, onOpenLibrary: () -> Unit) {
    val c = Monika.colors
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    var text by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    var history by remember { mutableStateOf(AppGraph.prefs.searchHistory) }
    val recent = remember { AppGraph.prefs.recentPosts }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val index by produceState<List<Post>?>(null) { value = withContext(Dispatchers.IO) { AppGraph.gameInfo.postIndex().orEmpty() } }
    val q = text.trim()
    // Gợi ý: đợi gõ xong 1 nhịp (120ms) để không lọc lại liên tục.
    val suggest by produceState(Suggest(), q, index) {
        if (q.isEmpty()) { value = Suggest(); return@produceState }
        delay(120)
        value = withContext(Dispatchers.Default) { suggestFor(q, index.orEmpty(), AppGraph.library.cached.orEmpty()) }
    }
    val remote by produceState<List<Post>?>(null, submitted) {
        value = null
        if (submitted.isNotBlank()) value = runCatching { AppGraph.feed.fetch(query = submitted, max = 20) }.getOrDefault(emptyList())
    }

    fun submit(query: String) {
        val t = query.trim()
        if (t.isEmpty()) return
        text = t
        submitted = t
        AppGraph.prefs.addSearch(t)
        history = AppGraph.prefs.searchHistory
        keyboard?.hide()
    }

    fun open(p: Post) {
        if (q.isNotEmpty()) { AppGraph.prefs.addSearch(q); history = AppGraph.prefs.searchHistory }
        onOpenPost(p)
    }

    Screen {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = DockClearance)) {
            item {
                Column(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp)) {
                    Text("Tìm kiếm", style = Monika.type.pageTitle, color = c.text)
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth().height(56.dp).clip(Radius.pill).background(c.surface).padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(painterResource(R.drawable.ic_fluent_search_24_regular), null, Modifier.size(22.dp), tint = c.accentCoral)
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.weight(1f)) {
                            if (text.isEmpty()) Text("Tên game, hệ máy, bài hướng dẫn…", style = Monika.type.body, color = c.textTertiary)
                            BasicTextField(
                                text, { text = it; if (it.isBlank()) submitted = "" }, singleLine = true,
                                textStyle = Monika.type.body.copy(color = c.text), cursorBrush = SolidColor(c.accentCoral),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { submit(text) }),
                                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                            )
                        }
                        if (text.isNotEmpty()) Icon(
                            painterResource(R.drawable.ic_fluent_dismiss_24_regular), "Xóa",
                            Modifier.size(30.dp).clip(Radius.pill).clickable { text = ""; submitted = "" }.padding(5.dp), tint = c.textSecondary,
                        )
                    }
                }
            }

            if (q.isEmpty()) {
                if (history.isNotEmpty()) {
                    item { SearchSection("Tìm gần đây", "Xóa") { AppGraph.prefs.searchHistory = emptyList(); history = emptyList() } }
                    item {
                        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            history.forEach { h -> Pill(h, R.drawable.ic_fluent_arrow_counterclockwise_24_regular) { submit(h) } }
                        }
                    }
                }
                if (recent.isNotEmpty()) {
                    item { SearchSection("Đã xem gần đây") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(recent, key = { it.id }) { p -> RecentCard(p) { onOpenPost(p) } }
                        }
                    }
                }
                item { SearchSection("Gợi ý") }
                item {
                    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Pokemon", "NDS", "GBA", "Game Java", "PSP", "Visual Novel", "RPG Maker", "Undertale").forEach { s -> Pill(s, R.drawable.ic_fluent_search_24_regular) { submit(s) } }
                    }
                }
            } else {
                val s = suggest
                if (s.games.isNotEmpty()) {
                    item { SearchSection("Trong Thư viện của bạn", "Mở Thư viện", onOpenLibrary) }
                    items(s.games, key = { "g" + it.key }) { g -> GameRow(g) {
                        AppGraph.prefs.addSearch(q)
                        val activity = context as? android.app.Activity ?: return@GameRow
                        if (AppGraph.launcher.launch(activity, g) == vn.aow.monika.runner.LaunchResult.Started) AppGraph.prefs.markPlayed(g.key) else onOpenLibrary()
                    } }
                }
                if (s.posts.isNotEmpty()) {
                    item { SearchSection("Game & bài viết") }
                    items(s.posts, key = { "p" + it.id }) { p -> PostRow(p) { open(p) } }
                }
                if (submitted.isNotBlank()) {
                    val extra = remote?.filter { r -> s.posts.none { it.id == r.id } }
                    item { SearchSection("Thêm trên aow.vn") }
                    when {
                        extra == null -> item { Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { Spinner() } }
                        extra.isEmpty() && s.posts.isEmpty() -> item {
                            Text("Không tìm thấy \"$submitted\". Thử tên tiếng Anh hoặc từ khóa ngắn hơn.", style = Monika.type.body, color = c.textSecondary, modifier = Modifier.padding(16.dp))
                        }
                        else -> items(extra, key = { "r" + it.id }) { p -> PostRow(p) { open(p) } }
                    }
                } else if (s.posts.isEmpty() && s.games.isEmpty() && index != null) item {
                    Text("Nhấn Tìm trên bàn phím để tìm toàn bộ aow.vn.", style = Monika.type.body, color = c.textSecondary, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

private data class Suggest(val games: List<Game> = emptyList(), val posts: List<Post> = emptyList())

private fun norm(s: String): String = Normalizer.normalize(s.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD)
    .replace(Regex("""\p{M}+"""), "").lowercase()

/** Mỗi từ đã gõ phải khớp ĐẦU 1 từ trong tên (gõ "poke pla" → "Pokemon Platinum"). Tên bắt đầu bằng từ khóa xếp trên. */
private fun rank(query: List<String>, title: String): Int? {
    val words = norm(title).split(Regex("""[^a-z0-9]+""")).filter { it.isNotEmpty() }
    if (query.any { q -> words.none { it.startsWith(q) } }) return null
    return if (words.firstOrNull()?.startsWith(query.first()) == true) 0 else 1
}

private fun suggestFor(q: String, index: List<Post>, games: List<Game>): Suggest {
    val words = norm(q).split(Regex("""[^a-z0-9]+""")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return Suggest()
    val g = games.mapNotNull { x -> rank(words, x.name)?.let { x to it } }.sortedBy { it.second }.map { it.first }.take(4)
    val p = index.mapNotNull { x -> rank(words, GameMeta.cleanTitle(x.title) + " " + x.labels.joinToString(" "))?.let { x to it } }
        .sortedBy { it.second }.map { it.first }.take(15)
    return Suggest(g, p)
}

@Composable
private fun SearchSection(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = Monika.type.cardTitle, color = Monika.colors.text, modifier = Modifier.weight(1f))
        if (action != null) Text(action, style = Monika.type.caption, color = Monika.colors.accentCoral, modifier = Modifier.clip(Radius.pill).clickable(onClick = onAction).padding(8.dp))
    }
}

@Composable
private fun Pill(text: String, icon: Int, onClick: () -> Unit) {
    val c = Monika.colors
    Row(
        Modifier.clip(Radius.pill).background(c.chip).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), null, Modifier.size(16.dp), tint = c.textSecondary)
        Spacer(Modifier.width(6.dp))
        Text(text, style = Monika.type.body, color = c.chipText, maxLines = 1)
    }
}

@Composable
private fun RecentCard(p: Post, onClick: () -> Unit) {
    val c = Monika.colors
    Column(Modifier.width(140.dp).pressable(remember { MutableInteractionSource() }, onClick)) {
        AsyncImage(Thumbs.cover(p.thumbnail), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(0.8f).clip(Radius.medium).background(c.surfaceSoft))
        Spacer(Modifier.height(6.dp))
        Text(GameMeta.cleanTitle(p.title), style = Monika.type.bodyStrong, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PostRow(p: Post, onClick: () -> Unit) {
    val c = Monika.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(Thumbs.cover(p.thumbnail), null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp).clip(Radius.thumb).background(c.surfaceSoft))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(GameMeta.cleanTitle(p.title), style = Monika.type.bodyStrong, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(p.labels.firstOrNull { it.startsWith("Game ") && !it.contains("Android") && !it.contains("PC") }?.let(::shortLabel) ?: formatDate(p.published),
                style = Monika.type.caption, color = c.textSecondary, maxLines = 1)
        }
        Icon(painterResource(R.drawable.ic_fluent_chevron_right_24_regular), null, Modifier.size(20.dp), tint = c.textTertiary)
    }
}

@Composable
private fun GameRow(g: Game, onPlay: () -> Unit) {
    val c = Monika.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp).clip(Radius.thumb).background(vn.aow.monika.ui.theme.primaryGradient()), contentAlignment = Alignment.Center) {
            val cover = g.meta?.cover
            if (cover != null) AsyncImage(cover, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Icon(painterResource(R.drawable.ic_fluent_games_24_regular), null, Modifier.size(26.dp), tint = Color.White)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(g.name, style = Monika.type.bodyStrong, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(g.system?.name ?: "Trong Thư viện", style = Monika.type.caption, color = c.textSecondary)
        }
        Box(Modifier.size(40.dp).clip(Radius.pill).background(vn.aow.monika.ui.theme.primaryGradient()), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_fluent_play_24_filled), "Chơi", Modifier.size(20.dp), tint = Color.White)
        }
    }
}
