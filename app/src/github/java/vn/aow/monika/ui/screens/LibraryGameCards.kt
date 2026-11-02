package vn.aow.monika.ui.screens

import vn.aow.monika.ui.theme.MonikaIcon
import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vn.aow.monika.AppGraph
import vn.aow.monika.R
import vn.aow.monika.library.Game
import vn.aow.monika.ui.theme.CircleButton
import vn.aow.monika.ui.theme.DarkButton
import vn.aow.monika.ui.theme.GradientButton
import vn.aow.monika.ui.theme.Illustration
import vn.aow.monika.ui.theme.Monika
import vn.aow.monika.ui.theme.MonikaCard
import vn.aow.monika.ui.theme.Radius
import vn.aow.monika.ui.theme.Tag
import vn.aow.monika.ui.theme.primaryGradient
import vn.aow.monika.ui.theme.artworkScrim
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput

/** Thẻ "Tiếp tục chơi": charcoal + minh họa 3D + nút play gradient. */
@Composable
internal fun ContinueCard(g: Game, onPlay: () -> Unit) {
    MonikaCard(Modifier.fillMaxWidth(), dark = true, shape = Radius.hero, padding = PaddingValues(0.dp), onClick = onPlay) {
      Box {
        val cover = g.meta?.cover?.takeUnless { vn.aow.monika.library.GameInfoResolver.isIcon(it) }
        cover?.let {
            AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            Box(Modifier.matchParentSize().background(artworkScrim()))
        }
        Row(Modifier.padding(20.dp).padding(top = if (cover != null) 90.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(g.name, style = Monika.type.sectionTitle, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    g.system?.let { Tag(it.name, onDark = true); ExperimentalTag(gameIsExperimental(g), onDark = true) }
                    val t = AppGraph.prefs.playTime(g.key)
                    Tag(if (t > 0) "⏱ ${formatPlayTime(t)}" else "Việt hóa", onDark = true)
                }
            }
            Box(Modifier.size(64.dp).clip(Radius.pill).background(primaryGradient()), contentAlignment = Alignment.Center) {
                MonikaIcon(R.drawable.ic_fluent_play_24_filled, "Chơi", Modifier.size(30.dp), tint = Color.White)
            }
        }
      }
    }
}

private val tileGradients = listOf(
    Brush.linearGradient(listOf(Color(0xFFFFB052), Color(0xFFE95CC8))),
    Brush.linearGradient(listOf(Color(0xFF8076FF), Color(0xFF628DF4))),
    Brush.linearGradient(listOf(Color(0xFF63D68A), Color(0xFF66CFF3))),
    Brush.linearGradient(listOf(Color(0xFFFF806E), Color(0xFFFFC95C))),
)

/** Ô game: ảnh bìa gradient + minh họa 3D (chưa có ảnh bìa thật), tên, hệ máy. */
@Composable
internal fun GameTile(g: Game, onPlay: () -> Unit, onExtract: () -> Unit, onMenu: () -> Unit, onRedownload: () -> Unit, onRequestAccess: () -> Unit) {
    val c = Monika.colors
    val waiting = g.needsExtract && g.system == null
    val playTime = AppGraph.prefs.playTime(g.key)
    Column {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.8f).clip(Radius.medium)
                // Giữ lâu ô game = mở menu của game.
                .pointerInput(g.key) { detectTapGestures(onLongPress = { onMenu() }) }
                .background(if (waiting) Brush.linearGradient(listOf(c.surfaceSoft, c.track)) else tileGradients[(g.name.hashCode() and 0x7fffffff) % tileGradients.size]),
            contentAlignment = Alignment.Center,
        ) {
            val cover = g.meta?.cover
            if (cover != null && vn.aow.monika.library.GameInfoResolver.isIcon(cover)) {
                // Icon nhỏ đọc từ file game (NDS/Java): vẽ giữa ô, giữ nét pixel.
                AsyncImage(cover, null, contentScale = ContentScale.Fit, filterQuality = androidx.compose.ui.graphics.FilterQuality.None, modifier = Modifier.size(88.dp).clip(Radius.small))
            } else if (cover != null) {
                // Ảnh bìa lấy từ bài viết aow.vn lúc tải.
                AsyncImage(cover, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(artworkScrim(0.4f)))
            } else {
                Illustration(if (waiting) R.drawable.fluent3d_package else R.drawable.fluent3d_joystick, Modifier.size(72.dp))
            }
            Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                CircleButton(R.drawable.ic_fluent_more_horizontal_24_regular, "Menu game", onMenu, style = vn.aow.monika.ui.theme.CircleStyle.Glass, size = 36.dp)
            }
            if (g.evicted) {
                Box(Modifier.fillMaxSize().background(Color(0x8C181719)))
                Box(Modifier.align(Alignment.Center)) { Tag("Đã dọn", onDark = true) }
            }
            Box(Modifier.align(Alignment.BottomCenter).padding(10.dp).fillMaxWidth()) {
                when {
                    g.evicted -> DarkButton("Tải lại", onRedownload, Modifier.fillMaxWidth(), icon = R.drawable.ic_fluent_arrow_download_24_regular)
                    g.locked -> DarkButton("Cấp quyền để mở", onRequestAccess, Modifier.fillMaxWidth())
                    waiting -> DarkButton("Giải nén", onExtract, Modifier.fillMaxWidth())
                    else -> GradientButton("Chơi", onPlay, Modifier.fillMaxWidth(), icon = R.drawable.ic_fluent_play_24_filled, height = 44.dp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        ExperimentalTag(gameIsExperimental(g))
        Text(g.name, style = Monika.type.bodyStrong, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            when {
                g.evicted -> "Đã dọn để tiết kiệm bộ nhớ · save vẫn giữ"
                g.locked -> "Của lần cài trước · cấp quyền để mở"
                else -> (g.system?.name ?: if (g.needsExtract) "Chưa giải nén" else "Chưa nhận diện") +
                    (if (playTime > 0) " · ⏱ ${formatPlayTime(playTime)}" else "")
            },
            style = Monika.type.caption, color = c.textSecondary,
        )
    }
}
