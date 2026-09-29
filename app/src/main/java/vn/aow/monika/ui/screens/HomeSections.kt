package vn.aow.monika.ui.screens

import vn.aow.monika.ui.theme.MonikaIcon
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import vn.aow.monika.AppGraph
import vn.aow.monika.R
import vn.aow.monika.community.Community
import vn.aow.monika.forum.ForumRepository
import vn.aow.monika.forum.ForumTopic
import vn.aow.monika.ui.theme.Monika
import vn.aow.monika.ui.theme.MonikaCard
import vn.aow.monika.ui.theme.Radius
import vn.aow.monika.ui.theme.SectionHeader
import vn.aow.monika.ui.theme.primaryGradient

/** Nhớ hệ máy người dùng chạm ở Trang chủ để màn "Game" mở sẵn đúng bộ lọc. */
object GamesFilter {
    @Volatile var pending: String? = null
    fun take(): String? = pending.also { pending = null }
}

/** Emoji 3D theo tên drawable (`fluent3d_*`) trong config; không thấy thì dùng bong bóng chat. */
@Composable
internal fun Emoji(name: String, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val id = remember(name) { context.resources.getIdentifier(name, "drawable", context.packageName).takeIf { it != 0 } ?: R.drawable.fluent3d_speech_balloon }
    Image(painterResource(id), null, modifier.size(size))
}

/** "Cộng đồng AowVN": mỗi kênh (Facebook, Discord, Forum, TikTok…) một thẻ có emoji 3D Fluent; danh sách kênh nằm trong config. */
@Composable
internal fun CommunitySection() {
    val cfg by AppGraph.config.config.collectAsState()
    val context = LocalContext.current
    val channels = cfg.community.shown()
    if (channels.isEmpty()) return
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Cộng đồng AowVN", style = Monika.type.sectionTitle, color = Monika.colors.text)
        channels.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { ch ->
                    MonikaCard(Modifier.weight(1f), dark = true, shape = Radius.large, padding = PaddingValues(12.dp), onClick = { Community.open(context, ch.url) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(46.dp).clip(Radius.thumb).background(Color(0x14FFFFFF)), contentAlignment = Alignment.Center) { Emoji(ch.emoji, 36.dp) }
                            Column(Modifier.padding(start = 10.dp)) {
                                Text(ch.name, style = Monika.type.bodyStrong, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (ch.subtitle.isNotBlank()) Text(ch.subtitle, style = Monika.type.caption, color = Monika.colors.textOnDarkSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** "Duyệt theo hệ máy": chạm 1 hệ → sang màn Game đã lọc sẵn. */
@Composable
internal fun SystemChips(onPick: (String) -> Unit) {
    val cfg by AppGraph.config.config.collectAsState()
    val labels = cfg.feedLabels()
    if (labels.isEmpty()) return
    val c = Monika.colors
    Column(Modifier.padding(top = 4.dp)) {
        SectionHeader("Duyệt theo hệ máy")
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(labels, key = { it }) { label ->
                Row(
                    Modifier.clip(Radius.pill).background(c.chip).clickable { onPick(label) }.padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Emoji("fluent3d_video_game", 18.dp)
                    Text(shortLabel(label), style = Monika.type.caption.copy(fontWeight = Monika.type.bodyStrong.fontWeight), color = c.text, maxLines = 1)
                }
            }
        }
    }
}

/** Diễn đàn AowVN (Flarum): chuyên mục + chủ đề mới nhất; chạm mở ngay trong trình duyệt của Monika. Lỗi mạng và chưa có bản lưu → ẩn khối. */
@Composable
internal fun ForumSection() {
    val cfg by AppGraph.config.config.collectAsState()
    if (!cfg.forum.enabled) return
    val context = LocalContext.current
    val repo = AppGraph.forum
    val topics by produceState<List<ForumTopic>?>(repo.cachedTopics(), cfg.forum.baseUrl) {
        repo.topics().onSuccess { value = it }
    }
    val tags by produceState(repo.cachedTags().orEmpty(), cfg.forum.baseUrl) {
        repo.tags().onSuccess { value = it }
    }
    val list = topics
    if (list.isNullOrEmpty()) return
    val c = Monika.colors
    Column(Modifier.padding(top = 8.dp)) {
        SectionHeader("Diễn đàn AowVN", "Mở diễn đàn", { Community.open(context, cfg.forum.baseUrl) })
        if (tags.isNotEmpty()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tags.take(10), key = { it.slug }) { t ->
                    Row(
                        Modifier.clip(Radius.pill).background(c.chip).clickable { Community.open(context, t.url) }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Emoji(ForumRepository.emojiFor(listOf(t.name)), 20.dp)
                        Text(t.name, style = Monika.type.caption.copy(fontWeight = Monika.type.bodyStrong.fontWeight), color = c.text, maxLines = 1)
                        Text("${t.topics}", style = Monika.type.caption, color = c.textSecondary)
                    }
                }
            }
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            list.forEach { t -> ForumRow(t) { Community.open(context, t.url) } }
        }
    }
}

@Composable
private fun ForumRow(t: ForumTopic, onClick: () -> Unit) {
    val c = Monika.colors
    MonikaCard(Modifier.fillMaxWidth(), shape = Radius.large, padding = PaddingValues(12.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(44.dp).clip(Radius.thumb).background(c.surfaceSoft), contentAlignment = Alignment.Center) {
                Emoji(ForumRepository.emojiFor(t.tags, t.sticky), 32.dp)
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(t.title, style = Monika.type.bodyStrong, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat(R.drawable.ic_fluent_chat_multiple_24_regular, ForumRepository.compact(t.replies))
                    Stat(R.drawable.ic_fluent_eye_24_regular, ForumRepository.compact(t.views))
                    Text(ForumRepository.relative(t.lastPostedAt), style = Monika.type.caption, color = c.textSecondary, maxLines = 1)
                }
                if (t.tags.isNotEmpty() || t.author.isNotBlank()) {
                    Text(
                        listOfNotNull(t.tags.firstOrNull(), t.author.takeIf { it.isNotBlank() }?.let { "bởi $it" }).joinToString(" · "),
                        style = Monika.type.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun Stat(icon: Int, text: String) {
    val c = Monika.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        MonikaIcon(icon, null, Modifier.size(14.dp), tint = c.textSecondary)
        Text(text, style = Monika.type.caption, color = c.textSecondary)
    }
}

/** Điểm danh ngay ở Trang chủ (không phải vào Cài đặt/trang cá nhân): hiện tức thì từ bản lưu, bấm là xong. Đã điểm danh hoặc chưa đăng nhập → ẩn. */
@Composable
internal fun CheckinStrip() {
    val session by AppGraph.account.session.collectAsState()
    val cached by AppGraph.aow.profileState.collectAsState()
    val uid = session?.uid ?: return
    val profile = cached?.takeIf { it.first == uid }?.second ?: AppGraph.aow.cachedProfile()
    androidx.compose.runtime.LaunchedEffect(uid) { runCatching { AppGraph.aow.profile() } }
    if (profile == null || profile.checkedInToday()) return
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val c = Monika.colors
    MonikaCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(), shape = Radius.large, padding = PaddingValues(12.dp), onClick = {
        scope.launch {
            runCatching { AppGraph.aow.checkinFast() }
                .onSuccess { r -> android.widget.Toast.makeText(context, if (r.pointAwarded) "Hoàn thành chuỗi 14 ngày! +1 điểm" else "Điểm danh thành công! Ngày ${r.profile.progress}/14", android.widget.Toast.LENGTH_SHORT).show() }
                .onFailure { android.widget.Toast.makeText(context, "Không điểm danh được: ${it.message}", android.widget.Toast.LENGTH_LONG).show() }
        }
    }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Emoji("fluent3d_fire", 32.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Điểm danh hôm nay", style = Monika.type.bodyStrong, color = c.text)
                Text("Chuỗi ${profile.streak} ngày · ${profile.progress}/14 — chạm để nhận", style = Monika.type.caption, color = c.textSecondary)
            }
            Emoji("fluent3d_check_mark_button", 28.dp)
        }
    }
}
