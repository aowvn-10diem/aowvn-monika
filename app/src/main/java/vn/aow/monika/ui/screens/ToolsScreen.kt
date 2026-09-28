package vn.aow.monika.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import kotlinx.coroutines.launch
import vn.aow.monika.AppGraph
import vn.aow.monika.BuildConfig
import vn.aow.monika.config.ExternalApp
import vn.aow.monika.runner.ExternalApps

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToolsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cfg by AppGraph.config.config.collectAsState()
    var refreshTick by remember { mutableIntStateOf(0) }
    var subscribed by remember { mutableStateOf(AppGraph.prefs.subscribedLabels) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1. Cập nhật app
        if (cfg.app.latestVersionCode > BuildConfig.VERSION_CODE) {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Có bản mới ${cfg.app.latestVersionName}", style = MaterialTheme.typography.titleMedium)
                    Text(cfg.app.changelog)
                    if (cfg.app.apkUrl.isNotBlank()) Button({
                        scope.launch { runCatching { AppGraph.downloader.enqueue(cfg.app.apkUrl, isTool = true) } }
                        Toast.makeText(context, "Đang tải bản mới…", Toast.LENGTH_SHORT).show()
                    }) { Text("Tải bản mới") }
                }
            }
        }

        // 2. App ngoài
        Text("App chạy game bổ sung", style = MaterialTheme.typography.titleLarge)
        cfg.externalApps.forEach { app ->
            val installed = remember(app, refreshTick) { ExternalApps.installedPackage(context, app) != null }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) { ExternalAppDetails(app, installed) }
            }
        }
        TextButton({ refreshTick++ }) { Text("Kiểm tra lại trạng thái cài đặt") }

        // 3. Thông báo
        Text("Nhận thông báo bài mới", style = MaterialTheme.typography.titleLarge)
        Text(
            if (subscribed.isEmpty()) "Đang nhận tất cả bài mới. Chọn nhãn để chỉ nhận loại game bạn thích."
            else "Chỉ nhận bài có nhãn đã chọn.",
            style = MaterialTheme.typography.bodySmall,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            cfg.feedLabels().forEach { label ->
                FilterChip(label in subscribed, {
                    subscribed = if (label in subscribed) subscribed - label else subscribed + label
                    AppGraph.prefs.subscribedLabels = subscribed
                }, { Text(label) })
            }
        }

        // 4. Lõi giả lập
        Text("Lõi giả lập", style = MaterialTheme.typography.titleLarge)
        cfg.cores.forEach { (id, def) ->
            val installed = remember(id, refreshTick) { AppGraph.cores.installedVersion(id) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(id, Modifier.weight(1f))
                Text(
                    when (installed) { null -> "Tải khi chơi lần đầu"; def.version -> "Đã có"; else -> "Có bản mới" },
                    style = MaterialTheme.typography.bodySmall,
                )
                if (installed != null) TextButton({ AppGraph.cores.delete(id); refreshTick++ }) { Text("Xóa") }
            }
        }

        // 5. Thông tin
        Text("Thông tin", style = MaterialTheme.typography.titleLarge)
        Text("Phiên bản app: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\nPhiên bản cấu hình: ${cfg.configVersion}")
        OutlinedButton({
            scope.launch {
                val r = AppGraph.config.refresh()
                Toast.makeText(
                    context,
                    if (r.isSuccess) "Đã cập nhật cấu hình" else "Chưa cập nhật được: ${r.exceptionOrNull()?.message}",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }) { Text("Cập nhật cấu hình") }
    }
}

/** Thông tin 1 app ngoài: trạng thái, nút tải (app + plugin), hướng dẫn cài. Dùng chung cho màn Trình chạy và hộp thoại "Cần cài". */
@Composable
fun ExternalAppDetails(app: ExternalApp, installed: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun download(url: String) {
        if (url.endsWith(".apk", ignoreCase = true)) {
            scope.launch {
                runCatching { AppGraph.downloader.enqueue(url, isTool = true) }
                    .onSuccess { Toast.makeText(context, "Đang tải… tải xong bấm thông báo để cài", Toast.LENGTH_LONG).show() }
                    .onFailure { openUrl(context, url) }
            }
        } else openUrl(context, url)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(app.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(if (installed) "✅ Đã cài" else "⬇️ Chưa cài", style = MaterialTheme.typography.bodySmall)
        }
        if (app.description.isNotBlank()) Text(app.description, style = MaterialTheme.typography.bodySmall)
        if (app.downloadUrl.isBlank()) {
            Text("Link tải đang được cập nhật.", style = MaterialTheme.typography.bodySmall)
        } else {
            Button({ download(app.downloadUrl) }) { Text(if (installed) "Tải lại / cập nhật" else "Tải ${app.name}") }
        }
        app.plugins.forEach { plugin ->
            OutlinedButton({ download(plugin.downloadUrl) }, enabled = plugin.downloadUrl.isNotBlank()) {
                Text(if (plugin.downloadUrl.isBlank()) "${plugin.name} (đang cập nhật link)" else "Tải ${plugin.name}")
            }
        }
        if (app.guide.isNotEmpty()) {
            Text("Hướng dẫn cài:", style = MaterialTheme.typography.labelLarge)
            app.guide.forEachIndexed { i, step -> Text("${i + 1}. $step", style = MaterialTheme.typography.bodySmall) }
        }
    }
}
