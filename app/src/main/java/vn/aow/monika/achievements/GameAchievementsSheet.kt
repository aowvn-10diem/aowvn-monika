package vn.aow.monika.achievements

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import vn.aow.monika.AppGraph
import vn.aow.monika.ui.theme.GradientProgress
import vn.aow.monika.ui.theme.Monika
import vn.aow.monika.ui.theme.MonikaMenuSheet
import vn.aow.monika.ui.theme.Radius
import vn.aow.monika.ui.theme.SheetColors
import vn.aow.monika.ui.theme.SheetAction
import vn.aow.monika.R

/** Nhãn loại thành tựu của RA, dịch sang tiếng Việt. */
fun raTypeVi(type: String?): String? = when (type) {
    "progression" -> "Cốt truyện"
    "win_condition" -> "Phá đảo"
    "missable" -> "Dễ lỡ"
    else -> null
}

/** Danh sách thành tựu 1 game (đã mở màu / chưa mở xám) + tiến độ + bản dịch tiếng Việt nếu có. */
@Composable
fun androidx.compose.foundation.layout.BoxScope.GameAchievementsSheet(gameId: Int?, onDismiss: () -> Unit) {
    val creds by AppGraph.ra.creds.collectAsState()
    var last by remember { mutableStateOf<Int?>(null) }
    if (gameId != null) last = gameId
    var game by remember { mutableStateOf<RaGameProgress?>(null) }
    var vi by remember { mutableStateOf<Map<String, RaViText>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    var original by remember { mutableStateOf(false) }
    LaunchedEffect(gameId) {
        val id = gameId ?: return@LaunchedEffect
        val cr = creds ?: return@LaunchedEffect
        game = null; error = null; vi = emptyMap()
        runCatching { AppGraph.raApi.gameProgress(id, AppGraph.ra.idForApi ?: cr.user, cr.key) }
            .onSuccess { game = it }.onFailure { error = "Không tải được thành tựu của game này" }
        vi = runCatching { AppGraph.raVi.forGame(id) }.getOrDefault(emptyMap())
    }
    val g = game
    MonikaMenuSheet(
        gameId != null, onDismiss,
        title = g?.title ?: "Thành tựu",
        subtitle = g?.let { "${it.consoleName} · ${it.numAwardedToUser}/${it.numAchievements} đã mở${it.userCompletion?.let { c -> " · $c" } ?: ""}" },
        header = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (g == null) Text(error ?: "Đang tải…", style = Monika.type.caption, color = SheetColors.textSecondary)
                else {
                    if (g.numAchievements > 0) GradientProgress(g.numAwardedToUser / g.numAchievements.toFloat())
                    if (vi.isNotEmpty()) Text(
                        if (original) "Đang xem bản gốc (tiếng Anh). Chạm vào đây để xem bản tiếng Việt." else "Đang xem bản dịch tiếng Việt. Chạm vào đây để xem bản gốc.",
                        style = Monika.type.caption, color = SheetColors.textSecondary, modifier = Modifier.clickableNoRipple { original = !original },
                    )
                    g.ordered.forEach { a ->
                        val tr = if (original) null else vi[a.id.toString()]
                        Row(Modifier.fillMaxWidth().clip(Radius.medium).background(SheetColors.row).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(RaApi.badge(a.badgeName, locked = !a.earned), null, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp).clip(Radius.small))
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(tr?.t?.ifBlank { null } ?: a.title, style = Monika.type.cardTitle, color = SheetColors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(tr?.d?.ifBlank { null } ?: a.description, style = Monika.type.caption, color = SheetColors.textSecondary, maxLines = 4, overflow = TextOverflow.Ellipsis)
                                val meta = listOfNotNull(
                                    "${a.points} điểm", raTypeVi(a.type),
                                    if (a.earned) "Đã mở ${raDateVn(a.dateEarnedHardcore ?: a.dateEarned)}" else "Chưa mở",
                                ).joinToString(" · ")
                                Text(meta, style = Monika.type.caption, color = SheetColors.textSecondary)
                            }
                        }
                    }
                }
            }
        },
        actions = emptyList<SheetAction>(),
    )
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.then(
    Modifier.clickable(indication = null, interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(), onClick = onClick)
)
