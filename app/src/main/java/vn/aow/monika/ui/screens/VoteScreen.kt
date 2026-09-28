package vn.aow.monika.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import vn.aow.monika.AppGraph
import vn.aow.monika.R
import vn.aow.monika.account.VoteGame
import vn.aow.monika.ui.theme.CircleButton
import vn.aow.monika.ui.theme.DarkButton
import vn.aow.monika.ui.theme.DockClearance
import vn.aow.monika.ui.theme.EmptyState
import vn.aow.monika.ui.theme.GradientButton
import vn.aow.monika.ui.theme.Monika
import vn.aow.monika.ui.theme.MonikaCard
import vn.aow.monika.ui.theme.MonikaHeader
import vn.aow.monika.ui.theme.Radius
import vn.aow.monika.ui.theme.Screen
import vn.aow.monika.ui.theme.Spinner
import vn.aow.monika.ui.theme.Tag
import vn.aow.monika.ui.theme.ChipBar

/** Vote Việt hóa: game đang gây quỹ + donate bằng QR (cùng dữ liệu với aow.vn/p/vote-game.html). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoteScreen(onBack: () -> Unit) {
    val c = Monika.colors
    val context = LocalContext.current
    val list by produceState<Result<List<VoteGame>>?>(null) { value = runCatching { AppGraph.aow.votes() } }
    var donating by remember { mutableStateOf<VoteGame?>(null) }
    val session by AppGraph.account.session.collectAsState()

    Screen {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = DockClearance), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { MonikaHeader("Vote Việt hóa", subtitle = "Góp quỹ cho game bạn muốn", left = { CircleButton(R.drawable.ic_fluent_arrow_left_24_regular, "Quay lại", onBack) }) }
            val r = list
            when {
                r == null -> item { Box(Modifier.fillMaxWidth().height(240.dp), Alignment.Center) { Spinner() } }
                r.isFailure || r.getOrNull().isNullOrEmpty() -> item {
                    EmptyState(R.drawable.fluent3d_video_game, "Chưa có game đang vote", r.exceptionOrNull()?.message ?: "Quay lại sau nhé.")
                }
                else -> items(r.getOrThrow(), key = { it.id }) { g -> VoteRow(g) { donating = g } }
            }
        }
    }

    donating?.let { g ->
        ModalBottomSheet(onDismissRequest = { donating = null }, containerColor = c.surface) {
            val amounts = AppGraph.config.current.account.donate.amounts
            var amount by remember { mutableStateOf(amounts.getOrElse(1) { amounts.firstOrNull() ?: 20_000 }) }
            val memo = AppGraph.aow.donateMemo(g.id)
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Góp quỹ: ${g.title}", style = Monika.type.sectionTitle, color = c.text)
                if (session == null) Text("Chưa đăng nhập: khoản góp sẽ không gắn với tài khoản của bạn. Đăng nhập ở Cài đặt để được ghi nhận.",
                    style = Monika.type.caption, color = c.danger)
                ChipBar(amounts, amount, { vnd(it) }, { amount = it }, accent = true, contentPadding = PaddingValues(0.dp))
                AsyncImage(AppGraph.aow.donateQr(g.id, amount), "Mã QR chuyển khoản",
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(Radius.large).background(Color.White), contentScale = ContentScale.Fit)
                Text("Mở app ngân hàng → Quét QR. Giữ nguyên nội dung chuyển khoản để hệ thống tự ghi nhận:", style = Monika.type.caption, color = c.textSecondary)
                DarkButton(memo, {
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Nội dung CK", memo))
                    Toast.makeText(context, "Đã sao chép nội dung chuyển khoản", Toast.LENGTH_SHORT).show()
                }, Modifier.fillMaxWidth(), icon = R.drawable.ic_fluent_copy_24_regular)
            }
        }
    }
}

@Composable
private fun VoteRow(g: VoteGame, onDonate: () -> Unit) {
    val c = Monika.colors
    val done = g.status == "completed"
    MonikaCard(Modifier.fillMaxWidth(), shape = Radius.large, padding = PaddingValues(0.dp), onClick = if (done) null else onDonate) {
        Column {
            AsyncImage(g.thumbnail, null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 7f).background(c.surfaceSoft))
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(g.title, style = Monika.type.cardTitle, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Tag(if (done) "Đã đạt — đang Việt hóa" else "Đang gây quỹ")
                    Text("${vnd(g.totalDonated)} · ${g.donors} lượt góp", style = Monika.type.caption, color = c.textSecondary)
                }
                if (g.note.isNotBlank()) Text(g.note, style = Monika.type.caption, color = c.textSecondary)
                if (!done) GradientButton("Góp quỹ", onDonate, Modifier.fillMaxWidth(), icon = R.drawable.ic_fluent_gift_24_regular, height = 44.dp)
            }
        }
    }
}

private fun vnd(v: Long) = "%,d đ".format(v).replace(',', '.')
