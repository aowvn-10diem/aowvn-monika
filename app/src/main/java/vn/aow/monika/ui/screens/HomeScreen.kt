package vn.aow.monika.ui.screens

import vn.aow.monika.ui.theme.MonikaWordmark
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import vn.aow.monika.feed.Thumbs
import vn.aow.monika.community.Community
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import vn.aow.monika.AppGraph
import vn.aow.monika.R
import vn.aow.monika.feed.Post
import vn.aow.monika.ui.Routes
import vn.aow.monika.ui.theme.CircleButton
import vn.aow.monika.ui.theme.DockClearance
import vn.aow.monika.ui.theme.EmptyState
import vn.aow.monika.ui.theme.GradientButton
import vn.aow.monika.ui.theme.Monika
import vn.aow.monika.ui.theme.MonikaCard
import vn.aow.monika.ui.theme.MonikaHeader
import vn.aow.monika.ui.theme.Radius
import vn.aow.monika.ui.theme.Screen
import vn.aow.monika.ui.theme.SectionHeader
import vn.aow.monika.ui.theme.Spinner
import vn.aow.monika.ui.theme.Tag
import vn.aow.monika.ui.theme.artworkScrim
import vn.aow.monika.ui.theme.pressable
import vn.aow.monika.ui.theme.primaryGradient
import vn.aow.monika.ui.theme.secondaryGradient

private data class QuickAction(val title: String, val subtitle: String, @DrawableRes val icon: Int, val brush: Brush, val route: String)

@Composable
fun HomeScreen(onOpenPost: (Post) -> Unit, onGo: (String) -> Unit) {
    val c = Monika.colors
    val context = LocalContext.current
    // Hiện ngay bản đã lưu (lần trước), rồi tải bản mới ở nền → quay lại Trang chủ không phải chờ.
    val posts by produceState<Result<List<Post>>?>(AppGraph.feed.cachedList(HOME_KEY)?.let { Result.success(it) }) {
        val fresh = runCatching { AppGraph.feed.fetchList(HOME_KEY, max = 12) }
        if (fresh.isSuccess || value == null) value = fresh
        fresh.getOrNull()?.let { prefetchImages(context, it) }
    }
    // Game đang chơi dở (quét thư viện ở nền nếu chưa có).
    val continueGame by produceState(AppGraph.library.lastPlayed(AppGraph.prefs)) {
        value = withContext(Dispatchers.IO) { AppGraph.library.lastPlayed(AppGraph.prefs, AppGraph.library.list()) }
    }
    val quick = remember {
        listOf(
            QuickAction("Vote", "Góp quỹ Việt hóa", R.drawable.ic_fluent_vote_24_regular, secondaryGradient(), Routes.VOTE),
            QuickAction("Game", "Khám phá game", R.drawable.ic_fluent_games_24_regular, primaryGradient(), Routes.GAMES),
            QuickAction("Thư viện", "Game của bạn", R.drawable.ic_fluent_library_24_regular, Brush.linearGradient(listOf(Color(0xFF9975FF), Color(0xFF638EFF))), Routes.EMULATOR),
            QuickAction("Tải xuống", "Quản lý file", R.drawable.ic_fluent_arrow_download_24_regular, Brush.linearGradient(listOf(Color(0xFF63D68A), Color(0xFF66CFF3))), Routes.DOWNLOADS),
        )
    }

    Screen {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = DockClearance)) {
            item {
                MonikaHeader(
                    title = "Aow Monika", titleContent = { MonikaWordmark() },
                    left = { CircleButton(R.drawable.ic_fluent_navigation_24_regular, "Cài đặt", { onGo(Routes.SETTINGS) }) },
                    right = { CircleButton(R.drawable.ic_fluent_alert_24_regular, "Thông báo", { onGo(Routes.SETTINGS) }) },
                )
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Khám phá", style = Monika.type.pageTitle, color = c.text)
                        Text("Thế giới game Việt hóa trong tầm tay ✨", style = Monika.type.body, color = c.textSecondary)
                    }
                }
            }
            val list = posts?.getOrNull()
            when {
                posts == null -> item { Box(Modifier.fillMaxWidth().height(330.dp), Alignment.Center) { Spinner() } }
                list.isNullOrEmpty() -> item {
                    EmptyState(R.drawable.fluent3d_newspaper, "Chưa tải được bài viết", posts?.exceptionOrNull()?.message ?: "Kiểm tra kết nối mạng.")
                }
                else -> item { HeroCarousel(list.take(3), onOpenPost) }
            }
            continueGame?.let { g ->
                item { SectionHeader("Đang chơi dở", "Thư viện", { onGo(Routes.EMULATOR) }) }
                item {
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        ContinueCard(g) {
                            val activity = context as? android.app.Activity
                            if (activity != null && AppGraph.launcher.launch(activity, g) == vn.aow.monika.runner.LaunchResult.Started) AppGraph.prefs.markPlayed(g.dir.path)
                            else onGo(Routes.EMULATOR) // Cần app ngoài / lỗi → để tab Thư viện hướng dẫn.
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    quick.forEach { q -> QuickActionCard(q, Modifier.weight(1f)) { onGo(q.route) } }
                }
            }
            communityItem()
            if (!list.isNullOrEmpty() && list.size > 3) {
                item { SectionHeader("Game nổi bật", "Xem tất cả", { onGo(Routes.GAMES) }) }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(list.drop(3), key = { it.id }) { p -> FeaturedCard(p) { onOpenPost(p) } }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroCarousel(posts: List<Post>, onOpen: (Post) -> Unit) {
    val pager = rememberPagerState { posts.size }
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        HorizontalPager(pager, pageSpacing = 12.dp) { i ->
            val p = posts[i]
            val interaction = remember { MutableInteractionSource() }
            Box(Modifier.fillMaxWidth().height(330.dp).clip(Radius.hero).background(Monika.colors.surfaceDark).pressable(interaction, { onOpen(p) }, target = 0.98f)) {
                AsyncImage(Thumbs.hero(p.thumbnail), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(artworkScrim()))
                Column(Modifier.align(Alignment.BottomStart).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tag(if (i == 0) "Mới nhất" else "Nổi bật", accent = true)
                    Text(p.title, style = Monika.type.sectionTitle, color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(p.labels.take(2).joinToString(" · "), style = Monika.type.caption, color = Color.White.copy(alpha = 0.85f), maxLines = 1)
                    GradientButton("Xem ngay", { onOpen(p) }, height = 48.dp)
                }
            }
        }
        Row(Modifier.align(Alignment.BottomEnd).padding(24.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(posts.size) { i ->
                Box(Modifier.size(if (i == pager.currentPage) 8.dp else 6.dp).clip(Radius.pill).background(Color.White.copy(alpha = if (i == pager.currentPage) 1f else 0.5f)))
            }
        }
    }
}

/** Khối "Cộng đồng AowVN": group Facebook + Discord (link trong config `community`, trống = ẩn). */
private fun androidx.compose.foundation.lazy.LazyListScope.communityItem() = item {
    val cfg by AppGraph.config.config.collectAsState()
    val context = LocalContext.current
    val links = listOf(
        Triple("Group Facebook", "Hỏi đáp, xin game", cfg.community.facebookGroup) to Brush.linearGradient(listOf(Color(0xFF4F8BFF), Color(0xFF6C63FF))),
        Triple("Discord", "Chat, nhóm dịch", cfg.community.discord) to Brush.linearGradient(listOf(Color(0xFF7289FF), Color(0xFFA06CFF))),
    ).filter { it.first.third.isNotBlank() }
    if (links.isEmpty()) return@item
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Cộng đồng AowVN", style = Monika.type.sectionTitle, color = Monika.colors.text)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            links.forEach { (t, brush) ->
                MonikaCard(Modifier.weight(1f), dark = true, shape = Radius.large, padding = PaddingValues(12.dp), onClick = { Community.open(context, t.third) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(Radius.thumb).background(brush), contentAlignment = Alignment.Center) {
                            Icon(
                                painterResource(if (t.first == "Discord") R.drawable.ic_fluent_chat_multiple_24_regular else R.drawable.ic_fluent_people_community_24_regular),
                                null, Modifier.size(22.dp), tint = Color.White,
                            )
                        }
                        Column(Modifier.padding(start = 10.dp)) {
                            Text(t.first, style = Monika.type.bodyStrong, color = Color.White, maxLines = 1)
                            Text(t.second, style = Monika.type.caption, color = Monika.colors.textOnDarkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

private const val HOME_KEY = "home"

/** Tải trước ảnh (thẻ lớn + ảnh đầu bài) vào bộ đệm → vuốt thẻ / mở bài hiện ảnh ngay. */
private fun prefetchImages(context: android.content.Context, posts: List<Post>) {
    val loader = coil.Coil.imageLoader(context)
    posts.take(3).forEach { loader.enqueue(coil.request.ImageRequest.Builder(context).data(Thumbs.hero(it.thumbnail)).build()) }
    posts.drop(3).forEach { loader.enqueue(coil.request.ImageRequest.Builder(context).data(Thumbs.cover(it.thumbnail)).build()) }
}

@Composable
private fun QuickActionCard(q: QuickAction, modifier: Modifier, onClick: () -> Unit) {
    MonikaCard(modifier, dark = true, shape = Radius.large, padding = PaddingValues(12.dp), onClick = onClick) {
        Box(Modifier.size(44.dp).clip(Radius.thumb).background(q.brush), contentAlignment = Alignment.Center) {
            Icon(painterResource(q.icon), null, Modifier.size(24.dp), tint = Color.White)
        }
        Spacer(Modifier.height(10.dp))
        Text(q.title, style = Monika.type.bodyStrong, color = Color.White, maxLines = 1)
        Text(q.subtitle, style = Monika.type.caption, color = Monika.colors.textOnDarkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FeaturedCard(p: Post, onClick: () -> Unit) {
    val c = Monika.colors
    val interaction = remember { MutableInteractionSource() }
    Column(Modifier.width(170.dp).pressable(interaction, onClick, target = 0.97f)) {
        AsyncImage(
            Thumbs.cover(p.thumbnail), null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(0.8f).clip(Radius.medium).background(c.surfaceSoft),
        )
        Spacer(Modifier.height(8.dp))
        Text(p.title, style = Monika.type.bodyStrong, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        p.labels.firstOrNull()?.let { Tag(shortLabel(it)) }
    }
}

/** "Game NDS Việt Hóa" → "NDS". */
fun shortLabel(label: String): String =
    label.removePrefix("Game ").removeSuffix(" Việt Hóa").removeSuffix(" Việt Hoá").ifBlank { label }

/** "2026-09-03T05:49:26..." → "03/09/2026". */
fun formatDate(iso: String): String =
    iso.take(10).split("-").takeIf { it.size == 3 }?.let { (y, m, d) -> "$d/$m/$y" } ?: iso
