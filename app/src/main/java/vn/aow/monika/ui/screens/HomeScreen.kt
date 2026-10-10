package vn.aow.monika.ui.screens

import vn.aow.monika.ui.theme.MonikaIcon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
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
    var refresh by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val bottomClearance = vn.aow.monika.ui.theme.dockContentClearance()
    // Hiện ngay bản đã lưu (lần trước), rồi tải bản mới ở nền → quay lại Trang chủ không phải chờ.
    val posts by produceState<Result<List<Post>>?>(AppGraph.feed.cachedList(HOME_KEY)?.let { Result.success(it) }, refresh) {
        value = AppGraph.feed.cachedList(HOME_KEY)?.let { Result.success(it) }
        val fresh = runCatching { AppGraph.feed.fetchList(HOME_KEY, max = 12) }
        if (fresh.isSuccess || value == null) value = fresh
        vn.aow.monika.ui.Inbox.tick.value++ // Có bài mới → chấm đỏ trên nút Menu.
        fresh.getOrNull()?.let { prefetchImages(context, it) }
    }
    // Game đang chơi dở (quét thư viện ở nền nếu chưa có).
    val playedTick by AppGraph.prefs.playedTick.collectAsState()
    val continueGame by produceState(AppGraph.library.lastPlayed(AppGraph.prefs), playedTick) {
        value = withContext(Dispatchers.IO) { runCatching { AppGraph.library.lastPlayed(AppGraph.prefs, AppGraph.library.list()) }.getOrNull() }
    }
    val quick = remember {
        listOf(
            QuickAction("Game", "Khám phá game", R.drawable.ic_fluent_games_24_regular, primaryGradient(), Routes.GAMES),
            QuickAction("Thư viện", "Game của bạn", R.drawable.ic_fluent_library_24_regular, Brush.linearGradient(listOf(Color(0xFF9975FF), Color(0xFF638EFF))), Routes.EMULATOR),
            QuickAction("Tải xuống", "Quản lý file", R.drawable.ic_fluent_arrow_download_24_regular, Brush.linearGradient(listOf(Color(0xFF63D68A), Color(0xFF66CFF3))), Routes.DOWNLOADS),
        )
    }

    Screen {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomClearance)) {
            item {
                // Header gọn: logo Monika bên trái, avatar bên phải (menu + thông báo nằm ở nút Menu dưới đáy).
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MonikaWordmark(Modifier.height(44.dp))
                    Spacer(Modifier.weight(1f))
                    AvatarButton(onGo)
                }
            }
            val list = posts?.getOrNull()
            when {
                posts == null -> item { FeedStatus(true) { refresh++ } }
                list.isNullOrEmpty() -> item { FeedStatus(false) { refresh++ } }
                else -> item { HeroCarousel(list.take(3), onOpenPost) }
            }
            continueGame?.let { g ->
                item { SectionHeader("Đang chơi dở", "Thư viện", { onGo(Routes.EMULATOR) }) }
                item {
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        ContinueCard(g) {
                            val activity = context as? android.app.Activity
                            if (activity != null && AppGraph.launcher.launch(activity, g) == vn.aow.monika.runner.LaunchResult.Started) AppGraph.prefs.markPlayed(g.key)
                            else onGo(Routes.EMULATOR) // Cần app ngoài / lỗi → để tab Thư viện hướng dẫn.
                        }
                    }
                }
            }
            item { SupportStrip(Modifier.padding(horizontal = 16.dp)) }
            item { CheckinStrip() }
            item { SystemChips { label -> GamesFilter.pending = label; onGo(Routes.GAMES) } }
            if (!list.isNullOrEmpty() && list.size > 3) {
                item { SectionHeader("Game nổi bật", "Xem tất cả", { onGo(Routes.GAMES) }) }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(list.drop(3), key = { it.id }) { p -> FeaturedCard(p) { onOpenPost(p) } }
                    }
                }
            }
            item { ForumSection() }
            item { CommunitySection() }
            // Một thẻ SupportStrip ở đầu trang; không lặp thẻ Donate ở cuối.
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroCarousel(posts: List<Post>, onOpen: (Post) -> Unit) {
    val pager = rememberPagerState { posts.size }
    // Tự chuyển slide mỗi 4 giây; đang vuốt tay thì chờ, máy tắt hiệu ứng thì không tự chạy.
    val motion = Monika.motion
    val dragging by pager.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(posts.size, dragging, motion.enabled) {
        if (posts.size < 2 || dragging || !motion.enabled) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(4_000)
            val next = (pager.currentPage + 1) % posts.size
            pager.animateScrollToPage(next, animationSpec = androidx.compose.animation.core.tween(motion.slow * 2, easing = motion.easing))
        }
    }
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

internal const val HOME_KEY = "home"

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
            MonikaIcon(q.icon, null, Modifier.size(24.dp), tint = Color.White)
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            p.labels.firstOrNull()?.let { Tag(shortLabel(it)) }
            Text(formatDate(p.published), style = Monika.type.caption, color = c.textSecondary, maxLines = 1)
        }
    }
}

/** "Game NDS Việt Hóa" → "NDS". */
fun shortLabel(label: String): String =
    label.removePrefix("Game ").removeSuffix(" Việt Hóa").removeSuffix(" Việt Hoá").ifBlank { label }

/** "2026-09-03T05:49:26..." → "03/09/2026". */
fun formatDate(iso: String): String =
    iso.take(10).split("-").takeIf { it.size == 3 }?.let { (y, m, d) -> "$d/$m/$y" } ?: iso

/** Avatar góc phải: ảnh tài khoản AowVN (đã đăng nhập) hoặc emoji 3D. Bấm → tài khoản / đăng nhập. */
@Composable
private fun AvatarButton(onGo: (String) -> Unit) {
    val session by AppGraph.account.session.collectAsState()
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier.size(48.dp).clip(Radius.pill).background(primaryGradient()).padding(2.dp).clip(Radius.pill)
            .background(Monika.colors.surface)
            .pressable(interaction, {
                if (session != null) onGo(Routes.SETTINGS)
                else (context as? android.app.Activity)?.let { AppGraph.account.startLogin(it) }
            }),
        contentAlignment = Alignment.Center,
    ) {
        val photo = session?.photo?.takeIf { it.isNotBlank() }
        if (photo != null) AsyncImage(photo, session?.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(Radius.pill))
        else androidx.compose.foundation.Image(painterResource(R.drawable.fluent3d_smiling_face), "Đăng nhập", Modifier.size(34.dp))
    }
}

/** Thẻ kêu gọi ủng hộ AowVN (thay hàng lối tắt cũ) + nút Donate nổi bật mở hệ thống donate trên web. */
@Composable
private fun DonateBanner() {
    val context = LocalContext.current
    val cfg by AppGraph.config.config.collectAsState()
    val url = cfg.account.donateUrl.ifBlank { cfg.account.voteUrl }
    Box(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
        MonikaCard(Modifier.fillMaxWidth(), dark = true, shape = Radius.hero, padding = PaddingValues(18.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                androidx.compose.foundation.Image(painterResource(R.drawable.fluent3d_smiling_face), null, Modifier.size(44.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Monika miễn phí mãi mãi cho bạn yêu 💖", style = Monika.type.cardTitle, color = Color.White)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Nhưng chúng mình cũng phải nghĩ tới chi phí duy trì hệ thống và hỗ trợ các team Việt hóa. " +
                            "Vậy nên đừng quên ủng hộ cho AowVN ngay tại đây nha!",
                        style = Monika.type.body, color = Monika.colors.textOnDarkSecondary,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            GradientButton(
                "Ủng hộ AowVN", { vn.aow.monika.browser.InAppBrowserActivity.start(context, url) }, Modifier.fillMaxWidth(),
                icon = R.drawable.ic_fluent_heart_24_filled, height = 52.dp,
            )
        }
    }
}
