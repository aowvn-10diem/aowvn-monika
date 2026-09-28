package vn.aow.monika.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import vn.aow.monika.AppGraph
import vn.aow.monika.R
import vn.aow.monika.account.Review
import vn.aow.monika.ui.theme.ChipBar
import vn.aow.monika.ui.theme.GradientButton
import vn.aow.monika.ui.theme.Monika
import vn.aow.monika.ui.theme.Radius
import vn.aow.monika.ui.theme.Spinner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Đánh giá bản dịch của 1 bài (post_reviews/{mã bài}) — cùng dữ liệu với phần đánh giá trên web. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewsSheet(postId: String, onDismiss: () -> Unit) {
    val c = Monika.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session by AppGraph.account.session.collectAsState()
    var reviews by remember { mutableStateOf<List<Review>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(postId, reload) { reviews = runCatching { AppGraph.aow.reviews(postId) }.getOrDefault(emptyList()) }
    val mine = reviews?.firstOrNull { it.uid == session?.uid }
    var recommended by remember(mine) { mutableStateOf(mine?.recommended ?: true) }
    var text by remember(mine) { mutableStateOf(mine?.content.orEmpty()) }
    var saving by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val list = reviews
            val pos = list?.count { it.recommended } ?: 0
            Text("Đánh giá bản dịch", style = Monika.type.sectionTitle, color = c.text)
            if (!list.isNullOrEmpty()) Text("${pos * 100 / list.size}% đề xuất · ${list.size} đánh giá", style = Monika.type.body, color = c.textSecondary)

            if (session == null) {
                GradientButton("Đăng nhập để đánh giá", { (context as? Activity)?.let { AppGraph.account.startLogin(it) } },
                    Modifier.fillMaxWidth(), icon = R.drawable.ic_fluent_person_24_regular, height = 46.dp)
            } else {
                ChipBar(listOf(true, false), recommended, { if (it) "👍 Đề xuất" else "👎 Không đề xuất" }, { recommended = it },
                    accent = true, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp))
                OutlinedTextField(text, { if (it.length <= 1000) text = it }, Modifier.fillMaxWidth().heightIn(min = 96.dp),
                    label = { Text("Nhận xét về bản dịch (font, lỗi, độ mượt…)") }, shape = Radius.small)
                GradientButton(if (saving) "Đang lưu…" else if (mine != null) "Cập nhật đánh giá" else "Đăng đánh giá", {
                    if (saving) return@GradientButton
                    if (text.trim().length < 5) { Toast.makeText(context, "Viết ít nhất vài chữ nhé", Toast.LENGTH_SHORT).show(); return@GradientButton }
                    saving = true
                    scope.launch {
                        runCatching { AppGraph.aow.saveReview(postId, recommended, text, mine?.createdAt) }
                            .onSuccess { Toast.makeText(context, "Đã lưu đánh giá", Toast.LENGTH_SHORT).show(); reload++ }
                            .onFailure { Toast.makeText(context, "Không lưu được: ${it.message}", Toast.LENGTH_LONG).show() }
                        saving = false
                    }
                }, Modifier.fillMaxWidth(), icon = R.drawable.ic_fluent_star_24_filled, height = 46.dp)
            }

            when {
                list == null -> Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { Spinner() }
                list.isEmpty() -> Text("Chưa có đánh giá nào. Hãy là người đầu tiên!", style = Monika.type.body, color = c.textSecondary)
                else -> LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(list, key = { it.uid }) { r -> ReviewRow(r) }
                }
            }
        }
    }
}

@Composable
private fun ReviewRow(r: Review) {
    val c = Monika.colors
    Column(Modifier.fillMaxWidth().clip(Radius.medium).background(c.surfaceSoft).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(r.photo.ifBlank { null }, null, contentScale = ContentScale.Crop, modifier = Modifier.size(32.dp).clip(Radius.pill).background(c.track))
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(r.name, style = Monika.type.bodyStrong, color = c.text)
                Text(SimpleDateFormat("dd/MM/yyyy", Locale("vi")).format(Date(r.updatedAt)), style = Monika.type.caption, color = c.textSecondary)
            }
            Icon(painterResource(if (r.recommended) R.drawable.ic_fluent_thumb_like_24_filled else R.drawable.ic_fluent_thumb_dislike_24_filled), null,
                Modifier.size(22.dp), tint = if (r.recommended) Color(0xFF34A853) else Color(0xFFD93025))
        }
        if (r.content.isNotBlank()) Text(r.content, style = Monika.type.body, color = c.text, modifier = Modifier.padding(top = 8.dp))
    }
}
