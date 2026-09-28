package vn.aow.monika.ui.screens

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
    val posts by produceState<Result<List<Post>>?>(null) { value = runCatching { AppGraph.feed.fetch(max = 12) } }
    val quick = remember {
        listOf(
            QuickAction("Web", "Duyệt aow.vn", R.drawable.ic_fluent_globe_24_regular, secondaryGradient(), Routes.GAMES),
            QuickAction("Game", "Khám phá game", R.drawable.ic_fluent_games_24_regular, primaryGradient(), Routes.GAMES),
            QuickAction("Giả lập", "Đa hệ máy", R.drawable.ic_fluent_layer_24_regular, Brush.linearGradient(listOf(Color(0xFF9975FF), Color(0xFF638EFF))), Routes.EMULATOR),
            QuickAction("Tải xuống", "Quản lý file", R.drawable.ic_fluent_arrow_download_24_regular, Brush.linearGradient(listOf(Color(0xFF63D68A), Color(0xFF66CFF3))), Routes.DOWNLOADS),
        )
    }

    Screen {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = DockClearance)) {
            item {
                MonikaHeader(
                    title = "Aow Monika",
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
                    val interaction = remember { MutableInteractionSource() }
                    Row(
                        Modifier.clip(Radius.pill).background(c.surface).pressable(interaction, { onGo(Routes.GAMES) })
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(painterResource(R.drawable.ic_fluent_search_24_regular), null, Modifier.size(20.dp), tint = c.text)
                        Spacer(Modifier.width(8.dp))
                        Text("Tìm kiếm", style = Monika.type.body, color = c.text)
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
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    quick.forEach { q -> QuickActionCard(q, Modifier.weight(1f)) { onGo(q.route) } }
                }
            }
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
                AsyncImage(p.thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
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
            p.thumbnail, null, contentScale = ContentScale.Crop,
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
