package vn.aow.monika.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.aow.monika.AppGraph
import vn.aow.monika.R
import vn.aow.monika.config.ExternalApp
import vn.aow.monika.library.Game
import vn.aow.monika.library.GameStorage
import vn.aow.monika.library.ExtractProgress
import vn.aow.monika.library.Importer
import vn.aow.monika.runner.LaunchResult
import vn.aow.monika.ui.theme.ChipBar
import vn.aow.monika.ui.theme.CircleButton
import vn.aow.monika.ui.theme.DarkButton
import vn.aow.monika.ui.theme.DockClearance
import vn.aow.monika.ui.theme.EmptyState
import vn.aow.monika.ui.theme.GradientButton
import vn.aow.monika.ui.theme.Illustration
import vn.aow.monika.ui.theme.Monika
import vn.aow.monika.ui.theme.MonikaCard
import vn.aow.monika.ui.theme.MonikaHeader
import vn.aow.monika.ui.theme.Radius
import vn.aow.monika.ui.theme.Screen
import vn.aow.monika.ui.theme.Tag
import vn.aow.monika.ui.theme.primaryGradient
import vn.aow.monika.ui.theme.artworkScrim
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale

/** Màn "Giả lập": thư viện game trong máy, lọc theo hệ, tiếp tục chơi. */
@Composable
fun LibraryScreen(onSettings: () -> Unit) {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val c = Monika.colors
    var games by remember { mutableStateOf<List<Game>>(emptyList()) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var percent by remember { mutableStateOf<Int?>(null) } // % giải nén, null = chưa biết
    val onProgress = remember { ExtractProgress { percent = it } }
    var filter by remember { mutableStateOf<String?>(null) }
    var needApp by remember { mutableStateOf<ExternalApp?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var toDelete by remember { mutableStateOf<Game?>(null) }
    var toExtract by remember { mutableStateOf<Game?>(null) }
    var password by remember { mutableStateOf("") }
    var pinned by remember { mutableStateOf(AppGraph.prefs.pinnedGames) }

    LaunchedEffect(reloadKey) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            games = withContext(Dispatchers.IO) { AppGraph.library.list() }
        }
    }

    // Chọn được nhiều file cùng lúc (vd. đủ các phần part1/part2 của 1 game).
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        busy = true; percent = null
        scope.launch {
            val messages = mutableListOf<String>()
            for (uri in uris) {
                runCatching { withContext(Dispatchers.IO) { Importer.importUri(context, uri, AppGraph.config.current.archivePasswords, onProgress) } }
                    .onSuccess { r ->
                        when {
                            r.pending -> messages += r.error.orEmpty()
                            r.error == null -> messages += "Đã thêm: ${r.dir.name}"
                            else -> messages += "\"${r.dir.name}\" chưa giải nén được: ${r.error}\nBấm \"Giải nén\" ở game đó để thử lại."
                        }
                    }
                    .onFailure { messages += "Lỗi thêm game: ${it.message}" }
            }
            busy = false; percent = null
            reloadKey++
            // Chỉ báo dòng cuối cho mỗi bộ (tránh lặp "đang chờ phần" khi đã đủ).
            val last = messages.lastOrNull().orEmpty()
            if (uris.size == 1 && last.startsWith("Đã thêm")) Toast.makeText(context, last, Toast.LENGTH_SHORT).show()
            else info = messages.distinct().takeLast(4).joinToString("\n\n")
        }
    }

    fun play(game: Game) {
        when (val r = AppGraph.launcher.launch(activity, game)) {
            is LaunchResult.NeedApp -> needApp = r.app
            is LaunchResult.OpenedApp -> { AppGraph.prefs.markPlayed(game.dir.path); info = "Đã mở ${r.app.name}. Trong app đó, chọn thư mục:\n${r.gamePath}" }
            is LaunchResult.Failed -> info = r.message
            LaunchResult.Started -> AppGraph.prefs.markPlayed(game.dir.path)
        }
    }

    val systems = games.mapNotNull { it.system?.name }.distinct()
    val shown = games.filter { filter == null || it.system?.name == filter }
    val lastPlayed = games.filter { it.system != null }.maxByOrNull { AppGraph.prefs.lastPlayed(it.dir.path) }
        ?.takeIf { AppGraph.prefs.lastPlayed(it.dir.path) > 0 }

    Screen {
      Column(Modifier.fillMaxSize()) {
        MonikaHeader(
            "Giả lập", subtitle = "${games.size} game trong máy",
            left = { CircleButton(R.drawable.ic_fluent_settings_24_regular, "Cài đặt giả lập", onSettings) },
            right = { CircleButton(R.drawable.ic_fluent_folder_add_24_regular, "Thêm game từ máy", { picker.launch(arrayOf("*/*")) }) },
        )
        LazyVerticalGrid(
            GridCells.Fixed(2), Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = DockClearance),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (busy) item(span = { GridItemSpan(2) }) {
                val p = percent
                if (p == null) LinearProgressIndicator(Modifier.fillMaxWidth().clip(Radius.pill), color = c.accentCoral, trackColor = c.track)
                else Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Đang giải nén $p%", style = Monika.type.caption, color = c.textSecondary)
                    LinearProgressIndicator({ p / 100f }, Modifier.fillMaxWidth().clip(Radius.pill), color = c.accentCoral, trackColor = c.track)
                }
            }
            if (systems.size > 1) item(span = { GridItemSpan(2) }) {
                ChipBar(listOf<String?>(null) + systems, filter, { it ?: "Tất cả" }, { filter = it }, accent = true, contentPadding = PaddingValues(0.dp))
            }
            lastPlayed?.let { g ->
                item(span = { GridItemSpan(2) }) { Text("Tiếp tục chơi", style = Monika.type.sectionTitle, color = c.text) }
                item(span = { GridItemSpan(2) }) { ContinueCard(g) { play(g) } }
            }
            if (games.isEmpty()) item(span = { GridItemSpan(2) }) {
                EmptyState(
                    R.drawable.fluent3d_video_game, "Chưa có game",
                    "Tải game ở tab Game, hoặc thêm file (.zip .rar .7z .nds .gba .iso…) có sẵn trong máy.\n\nThư mục: ${GameStorage.games(context).absolutePath}",
                ) { GradientButton("Thêm game từ máy", { picker.launch(arrayOf("*/*")) }, icon = R.drawable.ic_fluent_folder_add_24_regular) }
            } else item(span = { GridItemSpan(2) }) {
                Text("Thư viện", style = Monika.type.sectionTitle, color = c.text, modifier = Modifier.padding(top = 4.dp))
            }
            items(shown, key = { it.dir.path }) { g ->
                GameTile(
                    g, enabled = !busy, onPlay = { play(g) }, onExtract = { password = ""; toExtract = g }, onDelete = { toDelete = g },
                    pinned = g.dir.path in pinned,
                    onTogglePin = {
                        pinned = if (g.dir.path in pinned) pinned - g.dir.path else pinned + g.dir.path
                        AppGraph.prefs.pinnedGames = pinned
                        Toast.makeText(context, if (g.dir.path in pinned) "Đã giữ lại: không tự dọn game này" else "Đã bỏ giữ lại", Toast.LENGTH_SHORT).show()
                    },
                    onRedownload = {
                        // Mở lại bài viết gốc để tải lại (bài có nút "Tải game").
                        val meta = g.meta
                        when {
                            meta?.postId != null -> context.startActivity(
                                android.content.Intent(context, vn.aow.monika.ui.MainActivity::class.java)
                                    .putExtra(vn.aow.monika.ui.MainActivity.EXTRA_POST_ID, meta.postId)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            )
                            meta?.postUrl != null -> openUrl(context, meta.postUrl)
                        }
                    },
                )
            }
        }
      }
    }

    needApp?.let { app ->
        AlertDialog(
            onDismissRequest = { needApp = null },
            confirmButton = { DarkButton("Đóng", { needApp = null }) },
            title = { Text("Cần cài ${app.name}", style = Monika.type.sectionTitle) },
            text = { ExternalAppDetails(app, installed = false) },
            containerColor = c.surface, shape = Radius.large,
        )
    }
    info?.let {
        AlertDialog(
            onDismissRequest = { info = null },
            confirmButton = { GradientButton("OK", { info = null }, height = 44.dp) },
            text = { Text(it, style = Monika.type.body, color = c.text) },
            containerColor = c.surface, shape = Radius.large,
        )
    }
    toExtract?.let { game ->
        AlertDialog(
            onDismissRequest = { toExtract = null },
            title = { Text("Giải nén \"${game.name}\"", style = Monika.type.cardTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nhập mật khẩu nếu có (thường ghi cuối bài). App đã tự thử mật khẩu quen dùng của AowVN.", style = Monika.type.body, color = c.textSecondary)
                    OutlinedTextField(password, { password = it }, label = { Text("Mật khẩu") }, singleLine = true, shape = Radius.small)
                }
            },
            confirmButton = {
                GradientButton("Giải nén", {
                    val target = game
                    toExtract = null
                    busy = true; percent = null
                    scope.launch {
                        val r = withContext(Dispatchers.IO) { Importer.extractInPlace(target.dir, listOf(password) + AppGraph.config.current.archivePasswords, onProgress) }
                        busy = false; percent = null
                        if (r.error == null) Toast.makeText(context, "Giải nén xong", Toast.LENGTH_SHORT).show() else info = r.error
                        reloadKey++
                    }
                }, height = 44.dp)
            },
            dismissButton = { DarkButton("Hủy", { toExtract = null }) },
            containerColor = c.surface, shape = Radius.large,
        )
    }
    toDelete?.let { game ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Xóa game?", style = Monika.type.cardTitle) },
            text = { Text("Xóa \"${game.name}\" khỏi máy. Dữ liệu lưu game vẫn giữ.", style = Monika.type.body, color = c.textSecondary) },
            confirmButton = {
                GradientButton("Xóa", {
                    scope.launch {
                        withContext(Dispatchers.IO) { AppGraph.library.delete(game) }
                        toDelete = null
                        reloadKey++
                    }
                }, height = 44.dp)
            },
            dismissButton = { DarkButton("Hủy", { toDelete = null }) },
            containerColor = c.surface, shape = Radius.large,
        )
    }
}

/** Thẻ "Tiếp tục chơi": charcoal + minh họa 3D + nút play gradient. */
@Composable
private fun ContinueCard(g: Game, onPlay: () -> Unit) {
    MonikaCard(Modifier.fillMaxWidth(), dark = true, shape = Radius.hero, padding = PaddingValues(0.dp), onClick = onPlay) {
      Box {
        g.meta?.cover?.let {
            AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            Box(Modifier.matchParentSize().background(artworkScrim()))
        }
        Row(Modifier.padding(20.dp).padding(top = if (g.meta?.cover != null) 90.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(g.name, style = Monika.type.sectionTitle, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    g.system?.let { Tag(it.name, onDark = true) }
                    Tag("Việt hóa", onDark = true)
                }
            }
            Box(Modifier.size(64.dp).clip(Radius.pill).background(primaryGradient()), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_fluent_play_24_filled), "Chơi", Modifier.size(30.dp), tint = Color.White)
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
private fun GameTile(
    g: Game, enabled: Boolean, onPlay: () -> Unit, onExtract: () -> Unit, onDelete: () -> Unit, onRedownload: () -> Unit,
    pinned: Boolean, onTogglePin: () -> Unit,
) {
    val c = Monika.colors
    val waiting = g.needsExtract && g.system == null
    Column {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.8f).clip(Radius.medium)
                .background(if (waiting) Brush.linearGradient(listOf(c.surfaceSoft, c.track)) else tileGradients[(g.name.hashCode() and 0x7fffffff) % tileGradients.size]),
            contentAlignment = Alignment.Center,
        ) {
            val cover = g.meta?.cover
            if (cover != null) {
                // Ảnh bìa lấy từ bài viết aow.vn lúc tải.
                AsyncImage(cover, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(artworkScrim(0.4f)))
            } else {
                Illustration(if (waiting) R.drawable.fluent3d_package else R.drawable.fluent3d_joystick, Modifier.size(72.dp))
            }
            Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                CircleButton(R.drawable.ic_fluent_delete_24_regular, "Xóa", onDelete, style = vn.aow.monika.ui.theme.CircleStyle.Glass, size = 36.dp)
            }
            if (g.evicted) {
                Box(Modifier.fillMaxSize().background(Color(0x8C181719)))
                Box(Modifier.align(Alignment.Center)) { Tag("Đã dọn", onDark = true) }
            }
            // Giữ lại: game ghim không bao giờ bị dọn bộ nhớ đệm.
            if (g.meta != null && !g.evicted) Box(Modifier.align(Alignment.TopStart).padding(8.dp)) {
                CircleButton(
                    if (pinned) R.drawable.ic_fluent_heart_24_filled else R.drawable.ic_fluent_heart_24_regular,
                    if (pinned) "Bỏ giữ lại" else "Giữ lại (không tự dọn)", onTogglePin,
                    style = vn.aow.monika.ui.theme.CircleStyle.Glass, size = 36.dp,
                )
            }
            Box(Modifier.align(Alignment.BottomCenter).padding(10.dp).fillMaxWidth()) {
                when {
                    g.evicted -> DarkButton("Tải lại", onRedownload, Modifier.fillMaxWidth(), icon = R.drawable.ic_fluent_arrow_download_24_regular)
                    waiting -> DarkButton("Giải nén", onExtract, Modifier.fillMaxWidth(), enabled = enabled)
                    else -> GradientButton("Chơi", onPlay, Modifier.fillMaxWidth(), icon = R.drawable.ic_fluent_play_24_filled, enabled = enabled, height = 44.dp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(g.name, style = Monika.type.bodyStrong, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            when {
                g.evicted -> "Đã dọn để tiết kiệm bộ nhớ · save vẫn giữ"
                else -> g.system?.name ?: if (g.needsExtract) "Chưa giải nén" else "Chưa nhận diện"
            },
            style = Monika.type.caption, color = c.textSecondary,
        )
    }
}
