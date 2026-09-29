package vn.aow.monika.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import vn.aow.monika.AppGraph
import vn.aow.monika.R
import vn.aow.monika.community.Community
import vn.aow.monika.feed.Post
import vn.aow.monika.feed.Thumbs
import vn.aow.monika.ui.screens.HOME_KEY
import vn.aow.monika.ui.screens.formatDate
import vn.aow.monika.ui.theme.Monika
import vn.aow.monika.ui.theme.MonikaMenuSheet
import vn.aow.monika.ui.theme.Radius
import vn.aow.monika.ui.theme.SheetAction
import vn.aow.monika.ui.theme.SheetColors
import vn.aow.monika.ui.theme.SheetRow
import vn.aow.monika.ui.theme.primaryGradient

/**
 * Hộp thư trong app (thay chuông ở góc trên Trang chủ): bài mới trên aow.vn thuộc nhãn đang theo dõi,
 * đăng SAU lần cuối mở menu. Dữ liệu lấy từ danh sách Trang chủ đã lưu → không tốn thêm mạng.
 */
object Inbox {
    /** Tăng khi danh sách bài / trạng thái đã xem đổi → dock cập nhật chấm đỏ. */
    val tick = MutableStateFlow(0)

    fun latest(): List<Post> {
        val subscribed = AppGraph.prefs.subscribedLabels
        return AppGraph.feed.cachedList(HOME_KEY).orEmpty()
            .filter { subscribed.isEmpty() || it.labels.any(subscribed::contains) }
    }

    /** Bài chưa xem. Lần đầu dùng app: coi như đã xem hết (không báo cả chục bài cũ). */
    fun unread(): List<Post> {
        val list = latest()
        val seen = AppGraph.prefs.inboxSeen
        if (seen.isEmpty()) { list.maxOfOrNull { it.published }?.let { AppGraph.prefs.inboxSeen = it }; return emptyList() }
        return list.filter { it.published > seen }
    }

    fun markAllSeen() {
        latest().maxOfOrNull { it.published }?.let { if (it > AppGraph.prefs.inboxSeen) AppGraph.prefs.inboxSeen = it }
        tick.value++
    }
}

/**
 * Menu popup của app (nút cuối menu nổi): thông báo bài mới + lối tắt Tải xuống, Cài đặt, Vote, Cộng đồng.
 * Cùng thiết kế với menu trong game ([MonikaMenuSheet]).
 */
@Composable
fun BoxScope.AppMenuSheet(visible: Boolean, onDismiss: () -> Unit, onGo: (String) -> Unit, onOpenPost: (String) -> Unit) {
    val context = LocalContext.current
    val cfg by AppGraph.config.config.collectAsState()
    val tick by Inbox.tick.collectAsState()
    // Chụp lại danh sách chưa xem LÚC MỞ menu (để vẫn hiện nhãn "Mới"), rồi đánh dấu đã xem.
    val unreadIds = remember(visible) { if (visible) Inbox.unread().map { it.id }.toSet() else emptySet() }
    val posts = remember(visible, tick) { Inbox.latest().take(3) }
    LaunchedEffect(visible) { if (visible) Inbox.markAllSeen() }

    val actions = buildList {
        add(SheetAction("Tải xuống", R.drawable.ic_fluent_arrow_download_24_regular) { onGo(Routes.DOWNLOADS) })
        add(SheetAction("Cài đặt", R.drawable.ic_fluent_settings_24_regular) { onGo(Routes.SETTINGS) })
        add(SheetAction("Vote dịch", R.drawable.ic_fluent_vote_24_regular) { onGo(Routes.VOTE) })
        add(SheetAction("Nhận thông báo", R.drawable.ic_fluent_alert_24_regular) { onGo(Routes.SETTINGS) })
        if (cfg.community.facebookGroup.isNotBlank()) add(SheetAction("Group Facebook", R.drawable.ic_fluent_people_community_24_regular) { Community.open(context, cfg.community.facebookGroup) })
        if (cfg.community.discord.isNotBlank()) add(SheetAction("Discord", R.drawable.ic_fluent_chat_multiple_24_regular) { Community.open(context, cfg.community.discord) })
        add(SheetAction("Web aow.vn", R.drawable.ic_fluent_globe_24_regular) { vn.aow.monika.browser.InAppBrowserActivity.start(context, "https://www.aow.vn/") })
    }
    MonikaMenuSheet(
        visible, onDismiss, actions,
        title = "Aow Monika",
        subtitle = if (unreadIds.isEmpty()) "Không có thông báo mới" else "${unreadIds.size} bài mới trên aow.vn",
        footer = if (posts.isEmpty()) null else ({
            Text("Thông báo bài mới", style = Monika.type.caption, color = SheetColors.textSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                posts.forEach { p ->
                    SheetRow(
                        title = vn.aow.monika.library.GameMeta.cleanTitle(p.title), subtitle = formatDate(p.published),
                        leading = {
                            AsyncImage(Thumbs.cover(p.thumbnail), null, contentScale = ContentScale.Crop, modifier = Modifier.size(44.dp).clip(Radius.thumb).background(SheetColors.tile))
                        },
                        trailing = if (p.id in unreadIds) ({
                            Box(Modifier.padding(start = 8.dp).clip(Radius.pill).background(primaryGradient()).padding(horizontal = 8.dp, vertical = 3.dp), contentAlignment = Alignment.Center) {
                                Text("Mới", style = Monika.type.caption, color = SheetColors.text)
                            }
                        }) else null,
                        onClick = { onDismiss(); onOpenPost(p.id) },
                    )
                }
            }
        }),
    )
}
