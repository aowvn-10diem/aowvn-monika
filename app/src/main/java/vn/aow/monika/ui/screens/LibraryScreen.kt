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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import vn.aow.monika.library.Importer
import vn.aow.monika.runner.LaunchResult
import vn.aow.monika.ui.theme.Fluent
import vn.aow.monika.ui.theme.FluentButton
import vn.aow.monika.ui.theme.FluentButtonStyle
import vn.aow.monika.ui.theme.FluentCard
import vn.aow.monika.ui.theme.FluentEmptyState
import vn.aow.monika.ui.theme.FluentIconButton
import vn.aow.monika.ui.theme.FluentRadius
import vn.aow.monika.ui.theme.FluentTopBar

@Composable
fun LibraryScreen() {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val c = Fluent.colors
    var games by remember { mutableStateOf<List<Game>>(emptyList()) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var needApp by remember { mutableStateOf<ExternalApp?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var toDelete by remember { mutableStateOf<Game?>(null) }
    var toExtract by remember { mutableStateOf<Game?>(null) }
    var password by remember { mutableStateOf("") }

    // Tải lại danh sách mỗi khi quay lại màn hình (vd. vừa tải game xong).
    LaunchedEffect(reloadKey) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            games = withContext(Dispatchers.IO) { AppGraph.library.list() }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { Importer.importUri(context, uri, AppGraph.config.current.archivePasswords) } }
                .onSuccess { r ->
                    if (r.error == null) Toast.makeText(context, "Đã thêm: ${r.dir.name}", Toast.LENGTH_SHORT).show()
                    else info = "Đã thêm \"${r.dir.name}\" nhưng chưa giải nén được:\n${r.error}\n\nBấm \"Giải nén\" ở game đó để thử lại."
                }
                .onFailure { Toast.makeText(context, "Lỗi thêm game: ${it.message}", Toast.LENGTH_LONG).show() }
            busy = false
            reloadKey++
        }
    }

    Column(Modifier.fillMaxSize().background(c.background2)) {
        FluentTopBar("Thư viện", subtitle = "${games.size} game", actions = {
            FluentIconButton(R.drawable.ic_fluent_folder_add_24_regular, "Thêm game từ máy", { picker.launch(arrayOf("*/*")) })
        })
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = c.brandForeground, trackColor = c.stroke2)
        if (games.isEmpty()) {
            FluentEmptyState(
                R.drawable.ic_fluent_games_24_regular, "Chưa có game",
                "Tải game ở tab Bài viết, hoặc thêm file game (.zip, .rar, .7z, .nds, .gba, .iso…) có sẵn trong máy.\n\nThư mục game: ${GameStorage.games(context).absolutePath}",
            ) { FluentButton("Thêm game từ máy", { picker.launch(arrayOf("*/*")) }, icon = R.drawable.ic_fluent_folder_add_24_regular) }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(games, key = { it.dir.path }) { game ->
                GameRow(
                    game = game,
                    enabled = !busy,
                    onPlay = {
                        when (val r = AppGraph.launcher.launch(activity, game)) {
                            is LaunchResult.NeedApp -> needApp = r.app
                            is LaunchResult.OpenedApp -> info = "Đã mở ${r.app.name}. Trong app đó, chọn thư mục:\n${r.gamePath}"
                            is LaunchResult.Failed -> info = r.message
                            LaunchResult.Started -> Unit
                        }
                    },
                    onExtract = { password = ""; toExtract = game },
                    onDelete = { toDelete = game },
                )
            }
        }
    }

    needApp?.let { app ->
        AlertDialog(
            onDismissRequest = { needApp = null },
            confirmButton = { FluentButton("Đóng", { needApp = null }, style = FluentButtonStyle.Subtle) },
            title = { Text("Cần cài ${app.name}", style = Fluent.type.title3) },
            text = { ExternalAppDetails(app, installed = false) },
        )
    }
    info?.let {
        AlertDialog(
            onDismissRequest = { info = null },
            confirmButton = { FluentButton("OK", { info = null }) },
            text = { Text(it, style = Fluent.type.body2) },
        )
    }
    toExtract?.let { game ->
        AlertDialog(
            onDismissRequest = { toExtract = null },
            title = { Text("Giải nén \"${game.name}\"", style = Fluent.type.title3) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nhập mật khẩu nếu có (thường ghi cuối bài viết). App đã tự thử mật khẩu quen dùng của AowVN.", style = Fluent.type.body2)
                    OutlinedTextField(password, { password = it }, label = { Text("Mật khẩu") }, singleLine = true)
                }
            },
            confirmButton = {
                FluentButton("Giải nén", {
                    val target = game
                    toExtract = null
                    busy = true
                    scope.launch {
                        val passwords = listOf(password) + AppGraph.config.current.archivePasswords
                        val r = withContext(Dispatchers.IO) { Importer.extractInPlace(target.dir, passwords) }
                        busy = false
                        if (r.error == null) Toast.makeText(context, "Giải nén xong", Toast.LENGTH_SHORT).show()
                        else info = r.error
                        reloadKey++
                    }
                })
            },
            dismissButton = { FluentButton("Hủy", { toExtract = null }, style = FluentButtonStyle.Subtle) },
        )
    }
    toDelete?.let { game ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Xóa game?", style = Fluent.type.title3) },
            text = { Text("Xóa \"${game.name}\" khỏi máy. Dữ liệu lưu game trong app vẫn giữ.", style = Fluent.type.body2) },
            confirmButton = {
                FluentButton("Xóa", {
                    scope.launch {
                        withContext(Dispatchers.IO) { AppGraph.library.delete(game) }
                        toDelete = null
                        reloadKey++
                    }
                })
            },
            dismissButton = { FluentButton("Hủy", { toDelete = null }, style = FluentButtonStyle.Subtle) },
        )
    }
}

@Composable
private fun GameRow(game: Game, enabled: Boolean, onPlay: () -> Unit, onExtract: () -> Unit, onDelete: () -> Unit) {
    val c = Fluent.colors
    val waitingExtract = game.needsExtract && game.system == null
    FluentCard(Modifier.fillMaxWidth(), padding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(FluentRadius.card).background(if (waitingExtract) c.background3 else c.brandSubtle), contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(if (waitingExtract) R.drawable.ic_fluent_archive_24_regular else R.drawable.ic_fluent_games_24_filled),
                    null, Modifier.size(24.dp), tint = if (waitingExtract) c.foreground2 else c.brandForeground,
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(game.name, style = Fluent.type.body1Strong, color = c.foreground1, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    game.system?.name ?: if (game.needsExtract) "Chưa giải nén" else "Chưa nhận diện",
                    style = Fluent.type.caption1, color = c.foreground3,
                )
            }
            FluentIconButton(R.drawable.ic_fluent_delete_24_regular, "Xóa", onDelete, tint = c.foreground3)
            if (waitingExtract) FluentButton("Giải nén", onExtract, style = FluentButtonStyle.Outline, enabled = enabled)
            else FluentButton("Chơi", onPlay, icon = R.drawable.ic_fluent_play_24_filled, enabled = enabled)
        }
    }
}
