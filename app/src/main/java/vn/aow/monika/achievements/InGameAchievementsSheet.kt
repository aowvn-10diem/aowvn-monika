package vn.aow.monika.achievements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.json.JSONObject
import vn.aow.monika.ui.theme.GradientProgress
import vn.aow.monika.ui.theme.Monika
import vn.aow.monika.ui.theme.MonikaMenuSheet
import vn.aow.monika.ui.theme.Radius
import vn.aow.monika.ui.theme.SheetAction
import vn.aow.monika.ui.theme.SheetColors

/** Một thành tựu lấy từ lõi rcheevos (JSON của RetroAchievements.describeJson()). */
data class InGameAch(
    val title: String, val description: String, val points: Int,
    val unlocked: Boolean, val bucket: String, val progress: String, val badge: String,
)

data class InGameSummary(val title: String, val hardcore: Boolean, val total: Int, val unlocked: Int, val points: Int, val pointsUnlocked: Int, val list: List<InGameAch>)

fun parseInGameAchievements(json: String): InGameSummary? = runCatching {
    val o = JSONObject(json)
    val arr = o.optJSONArray("achievements")
    val list = (0 until (arr?.length() ?: 0)).map { i ->
        val a = arr!!.getJSONObject(i)
        InGameAch(a.optString("title"), a.optString("description"), a.optInt("points"), a.optBoolean("unlocked"), a.optString("bucket"), a.optString("progress"), a.optString("badge"))
    }
    InGameSummary(o.optString("title"), o.optBoolean("hardcore"), o.optInt("total", list.size), o.optInt("unlocked"), o.optInt("points"), o.optInt("pointsUnlocked"), list)
}.getOrNull()

/** Danh sách thành tựu ngay trong game (đọc thẳng từ rcheevos, không cần mạng thêm). */
@Composable
fun androidx.compose.foundation.layout.BoxScope.InGameAchievementsSheet(summary: InGameSummary?, visible: Boolean, onDismiss: () -> Unit) {
    MonikaMenuSheet(
        visible, onDismiss,
        title = summary?.title?.ifBlank { null } ?: "Thành tựu",
        subtitle = summary?.let { "${it.unlocked}/${it.total} đã mở · ${it.pointsUnlocked}/${it.points} điểm${if (it.hardcore) " · Hardcore" else ""}" },
        header = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (summary == null || summary.list.isEmpty()) Text("Chưa có dữ liệu thành tựu cho game này.", style = Monika.type.caption, color = SheetColors.textSecondary)
                else {
                    if (summary.total > 0) GradientProgress(summary.unlocked / summary.total.toFloat())
                    summary.list.sortedByDescending { it.unlocked }.forEach { a ->
                        Row(Modifier.fillMaxWidth().clip(Radius.medium).background(SheetColors.row).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(a.badge.ifBlank { null }, null, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp).clip(Radius.small))
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(a.title, style = Monika.type.cardTitle, color = SheetColors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(a.description, style = Monika.type.caption, color = SheetColors.textSecondary, maxLines = 4, overflow = TextOverflow.Ellipsis)
                                val meta = listOfNotNull("${a.points} điểm", if (a.unlocked) "Đã mở" else "Chưa mở", a.progress.ifBlank { null }).joinToString(" · ")
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
