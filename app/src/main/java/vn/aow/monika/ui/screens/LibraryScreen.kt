package vn.aow.monika.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.aow.monika.AppGraph
import vn.aow.monika.config.ExternalApp
import vn.aow.monika.library.Game
import vn.aow.monika.library.GameStorage
import vn.aow.monika.library.Importer
import vn.aow.monika.runner.LaunchResult

@Composable
fun LibraryScreen() {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var games by remember { mutableStateOf<List<Game>>(emptyList()) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var importing by remember { mutableStateOf(false) }
    var needApp by remember { mutableStateOf<ExternalApp?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var toDelete by remember { mutableStateOf<Game?>(null) }

    // Tải lại danh sách mỗi khi quay lại màn hình (vd. vừa tải game xong).
    LaunchedEffect(reloadKey) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            games = withContext(Dispatchers.IO) { AppGraph.library.list() }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        importing = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { Importer.importUri(context, uri) } }
                .onSuccess { Toast.makeText(context, "Đã thêm: ${it.name}", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "Lỗi thêm game: ${it.message}", Toast.LENGTH_LONG).show() }
            importing = false
            reloadKey++
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Game của bạn", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Button({ picker.launch(arrayOf("*/*")) }, enabled = !importing) { Text("Thêm game từ máy") }
        }
        if (importing) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (games.isEmpty()) {
            Text(
                "Chưa có game. Tải game ở tab Bài viết, hoặc bấm \"Thêm game từ máy\".\n\nThư mục game: ${GameStorage.games(context).absolutePath}",
                Modifier.padding(16.dp),
            )
        }
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(games, key = { it.dir.path }) { game ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(game.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                game.system?.name ?: if (game.needsExtract) "Cần giải nén (.rar/.7z)" else "Chưa nhận diện",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        IconButton({ toDelete = game }) { Icon(Icons.Filled.Delete, "Xóa") }
                        Button({
                            when (val r = AppGraph.launcher.launch(activity, game)) {
                                is LaunchResult.NeedApp -> needApp = r.app
                                is LaunchResult.OpenedApp -> info = "Đã mở ${r.app.name}. Trong app đó, chọn thư mục:\n${r.gamePath}"
                                is LaunchResult.Failed -> info = r.message
                                LaunchResult.Started -> Unit
                            }
                        }) { Text("Chơi") }
                    }
                }
            }
        }
    }

    needApp?.let { app ->
        AlertDialog(
            onDismissRequest = { needApp = null },
            confirmButton = { TextButton({ needApp = null }) { Text("Đóng") } },
            title = { Text("Cần cài ${app.name}") },
            text = { ExternalAppDetails(app, installed = false) },
        )
    }
    info?.let {
        AlertDialog(
            onDismissRequest = { info = null },
            confirmButton = { TextButton({ info = null }) { Text("OK") } },
            text = { Text(it) },
        )
    }
    toDelete?.let { game ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Xóa game?") },
            text = { Text("Xóa \"${game.name}\" khỏi máy. Dữ liệu lưu game trong app vẫn giữ.") },
            confirmButton = {
                TextButton({
                    scope.launch {
                        withContext(Dispatchers.IO) { AppGraph.library.delete(game) }
                        toDelete = null
                        reloadKey++
                    }
                }) { Text("Xóa") }
            },
            dismissButton = { TextButton({ toDelete = null }) { Text("Hủy") } },
        )
    }
}
