package vn.aow.monika.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vn.aow.monika.ui.theme.Monika
import vn.aow.monika.ui.theme.Radius
import vn.aow.monika.ui.theme.Tag
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import vn.aow.monika.library.GameTask

/** Ô game đang tải / giải nén: ảnh bìa (nếu có) + vòng tiến độ + %. */
@Composable
internal fun TaskTile(t: GameTask) {
    val c = Monika.colors
    Column {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.8f).clip(Radius.medium).background(Brush.linearGradient(listOf(Color(0xFF2A292D), Color(0xFF1C1B1E)))),
            contentAlignment = Alignment.Center,
        ) {
            if (t.cover != null) {
                AsyncImage(t.cover, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Color(0xA0181719)))
            }
            Box(contentAlignment = Alignment.Center) {
                val p = t.percent
                if (p == null) androidx.compose.material3.CircularProgressIndicator(Modifier.size(72.dp), color = Color(0xFFFF7F78), trackColor = Color(0x33FFFFFF), strokeWidth = 6.dp)
                else androidx.compose.material3.CircularProgressIndicator({ p / 100f }, Modifier.size(72.dp), color = Color(0xFFFF7F78), trackColor = Color(0x33FFFFFF), strokeWidth = 6.dp)
                Text(p?.let { "$it%" } ?: "…", style = Monika.type.cardTitle, color = Color.White)
            }
            Box(Modifier.align(Alignment.BottomCenter).padding(10.dp)) { Tag(t.phase, onDark = true) }
        }
        Spacer(Modifier.height(8.dp))
        Text(t.title, style = Monika.type.bodyStrong, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(t.phase + (t.percent?.let { " · $it%" } ?: ""), style = Monika.type.caption, color = c.textSecondary)
    }
}
