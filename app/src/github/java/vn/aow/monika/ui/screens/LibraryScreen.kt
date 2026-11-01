package vn.aow.monika.ui.screens

import android.net.Uri
import android.os.Build
import vn.aow.monika.ui.theme.Spinner
import androidx.compose.runtime.collectAsState
import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import vn.aow.monika.library.LibraryDocument
import vn.aow.monika.library.documentViewIntent
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
import vn.aow.monika.ui.theme.Monika
import vn.aow.monika.ui.theme.MonikaCard
import vn.aow.monika.ui.theme.MonikaHeader
import vn.aow.monika.ui.theme.Radius
import vn.aow.monika.ui.theme.Screen
import androidx.compose.runtime.produceState
import vn.aow.monika.library.GameTask
import vn.aow.monika.library.GameTasks
import vn.aow.monika.ui.theme.MonikaMenuSheet
import vn.aow.monika.ui.theme.SheetAction
import vn.aow.monika.ui.theme.SheetChip
import vn.aow.monika.ui.theme.SheetRow

/** Màn "Giả lập": thư viện game trong máy, lọc theo hệ, tiếp tục chơi. */
@Composable
fun LibraryScreen(onSettings: () -> Unit) {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val c = Monika.colors
    var scanned by remember { mutableStateOf(AppGraph.library.cached) } // null = đang quét lần đầu
    var documents by remember { mutableStateOf(AppGraph.library.cachedDocuments) }
    var documentToDelete by remember { mutableStateOf<LibraryDocument?>(null) }
    val games = scanned.orEmpty()
    var reloadKey by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(FILTER_ALL) }
    var systemFilter by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var systemSheet by remember { mutableStateOf(false) }
    var libMenu by remember { mutableStateOf(false) }
    var gameMenu by remember { mutableStateOf<Game?>(null) }
    // Game đang tải / giải nén: mỗi game 1 ô riêng có tiến độ (không còn thanh chung trên đầu).
    val extracting by GameTasks.running.collectAsState()
    val downloading by produceState(emptyList<GameTask>()) {
        while (true) {
            value = withContext(Dispatchers.IO) { GameTasks.downloads(context) }
            // Có lượt tải đang chạy → cập nhật nhanh; không có → hỏi thưa.
            kotlinx.coroutines.delay(if (value.isEmpty()) 4_000 else 1_000)
        }
    }
    val tasks = downloading + extracting.values
    var needApp by remember { mutableStateOf<ExternalApp?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var toDelete by remember { mutableStateOf<Game?>(null) }
    var toExtract by remember { mutableStateOf<Game?>(null) }
    var password by remember { mutableStateOf("") }
    var pinned by remember { mutableStateOf(AppGraph.prefs.pinnedGames) }

    val playedTick by AppGraph.prefs.playedTick.collectAsState()
    val infoTick by AppGraph.gameInfo.updated.collectAsState()
    // V132: cấu hình từ xa tăng bản → quét lại danh sách, không cần khởi động lại app.
    val config by AppGraph.config.config.collectAsState()
    LaunchedEffect(reloadKey, playedTick, infoTick, config.configVersion) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val listed = withContext(Dispatchers.IO) { AppGraph.library.list() to AppGraph.library.cachedDocuments }
            documents = listed.second
            scanned = listed.first
            // Tải sẵn lõi giả lập cho các hệ đang có game (ngầm) → bấm Chơi vào game ngay.
            AppGraph.app.let { app -> (app as? vn.aow.monika.MonikaApp)?.scope?.launch(Dispatchers.IO) { AppGraph.cores.prefetch(coreIdsOf(scanned.orEmpty())) } }
            // Tự tìm tên + ảnh cho game chưa có (đọc trong file game, rồi tra aow.vn) — xong thì infoTick đổi → vẽ lại.
            (AppGraph.app as? vn.aow.monika.MonikaApp)?.scope?.launch(Dispatchers.IO) { AppGraph.gameInfo.resolveAll(scanned.orEmpty()) }
        }
    }

    // Giữ tham chiếu hàm play (khai báo phía dưới) — mảng thường, không phải state, để không gây vẽ lại.
    val playRef = remember { arrayOfNulls<(Game) -> Unit>(1) }

    /** Nhận file vào thư viện. [autoPlay]: mở từ app khác → nhận diện xong thì chạy luôn đúng giả lập. */
    fun importAll(uris: List<android.net.Uri>, autoPlay: Boolean) {
        if (uris.isEmpty()) return
        busy = true
        scope.launch {
            val messages = mutableListOf<String>()
            var added: java.io.File? = null
            for (uri in uris) {
                val taskId = "add:$uri"
                val name = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Game mới"
                GameTasks.put(GameTask(taskId, name, null, "Đang thêm vào Thư viện", null))
                runCatching {
                    withContext(Dispatchers.IO) {
                        Importer.importUri(context, uri, AppGraph.config.current.archivePasswords, ExtractProgress { p -> GameTasks.put(GameTask(taskId, name, null, "Đang giải nén", p)) })
                    }
                }.also { GameTasks.remove(taskId) }
                    .onSuccess { r ->
                        when {
                            r.pending -> messages += r.error.orEmpty()
                            r.error == null -> { messages += "Đã thêm: ${r.dir.name}"; added = r.dir }
                            else -> messages += "\"${r.dir.name}\" chưa giải nén được: ${r.error}\nBấm \"Giải nén\" ở game đó để thử lại."
                        }
                    }
                    .onFailure { messages += "Lỗi thêm game: ${it.message}" }
            }
            busy = false
            reloadKey++
            val game = added?.let { d -> withContext(Dispatchers.IO) { AppGraph.library.list().firstOrNull { it.dir == d } } }
            if (autoPlay && uris.size == 1 && game?.system != null) { playRef[0]?.invoke(game); return@launch }
            // Chỉ báo dòng cuối cho mỗi bộ (tránh lặp "đang chờ phần" khi đã đủ).
            val last = messages.lastOrNull().orEmpty()
            if (uris.size == 1 && last.startsWith("Đã thêm")) Toast.makeText(context, last, Toast.LENGTH_SHORT).show()
            else info = messages.distinct().takeLast(4).joinToString("\n\n")
        }
    }

    // Chọn được nhiều file cùng lúc (vd. đủ các phần part1/part2 của 1 game).
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> importAll(uris, autoPlay = false) }

    var scanAccess by remember { mutableStateOf(AppGraph.library.scanner.canScanAll()) }
    var deviceScanning by remember { mutableStateOf(false) }
    var scanDirs by remember { mutableStateOf(0) }
    fun scanDevice() {
        if (deviceScanning) return
        deviceScanning = true
        scope.launch {
            val n = withContext(Dispatchers.IO) {
                runCatching { AppGraph.library.scanner.scan(AppGraph.config.current) { d -> scanDirs = d } }.getOrDefault(0)
            }
            deviceScanning = false
            reloadKey++
            Toast.makeText(context, if (n > 0) "Tìm thấy $n game trong máy" else "Không tìm thấy game nào khác trong máy", Toast.LENGTH_SHORT).show()
        }
    }
    fun accessReturned() {
        scanAccess = AppGraph.library.scanner.canScanAll()
        if (scanAccess) scanDevice()
    }
    val settingsAccess = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { accessReturned() }
    val legacyAccess = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { accessReturned() }
    fun requestScanAccess() {
        if (AppGraph.library.scanner.canScanAll()) { accessReturned(); return }
        if (Build.VERSION.SDK_INT >= 30) {
            runCatching { settingsAccess.launch(allFilesAccessIntent(context)) }.onFailure {
                runCatching { settingsAccess.launch(allFilesAccessIntent(context, appSpecific = false)) }.onFailure {
                    Toast.makeText(context, "Không mở được cài đặt quyền. Bạn vẫn có thể thêm game từ máy.", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            legacyAccess.launch(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE))
        }
    }
    // Đọc lại quyền mỗi khi quay từ cài đặt Android; chỉ quét khi quyền thực sự đã được cấp.
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val sc = AppGraph.library.scanner
            scanAccess = sc.canScanAll()
            if (scanAccess && System.currentTimeMillis() - sc.lastScan > 24 * 3_600_000L) scanDevice()
            kotlinx.coroutines.awaitCancellation()
        }
    }

    // File mở từ app khác ("Mở bằng Aow Monika").
    val pending by AppGraph.pendingImports.collectAsState()
    LaunchedEffect(pending) {
        if (pending.isNotEmpty()) {
            val list = pending
            AppGraph.pendingImports.value = emptyList()
            importAll(list, autoPlay = true)
        }
    }

    val play = rememberGameLaunch { game, r ->
        when (r) {
            is LaunchResult.NeedApp -> needApp = r.app
            is LaunchResult.OpenedApp -> { AppGraph.prefs.markPlayed(game.key); info = "Đã mở ${r.app.name}. Trong app đó, chọn thư mục:\n${r.gamePath}" }
            is LaunchResult.Failed -> info = r.message
            LaunchResult.Started -> AppGraph.prefs.markPlayed(game.key)
            is LaunchResult.NeedExperimentalConfirmation -> Unit // thành phần chung đã giữ lại trước khi gọi callback
        }
    }
    playRef[0] = play

    // Bộ lọc: Tất cả · Chơi gần đây · Chơi thường xuyên · Theo hệ máy (bấm → menu chọn hệ).
    val prefs = AppGraph.prefs
    val systems = games.mapNotNull { it.system?.name }.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }
    val byFilter = when (filter) {
        FILTER_RECENT -> games.filter { prefs.lastPlayed(it.key) > 0 }.sortedByDescending { prefs.lastPlayed(it.key) }
        FILTER_FREQUENT -> games.filter { prefs.playCount(it.key) >= 2 || prefs.playTime(it.key) > 10 * 60_000 }
            .sortedWith(compareByDescending<Game> { prefs.playTime(it.key) }.thenByDescending { prefs.playCount(it.key) })
        FILTER_SYSTEM -> games.filter { it.system?.name == systemFilter }
        else -> games
    }
    val shown = vn.aow.monika.library.GameSearch.filter(byFilter, query)
    val lastPlayed = AppGraph.library.lastPlayed(AppGraph.prefs, games)
    // Tải trước: game "Tiếp tục chơi" là game có khả năng được bấm nhất.
    LaunchedEffect(lastPlayed?.key) { lastPlayed?.let { vn.aow.monika.runner.GamePreload.warm(context, it) } }

    Screen {
      Column(Modifier.fillMaxSize()) {
        // Mọi nút của Thư viện gom vào 1 menu popup (cùng kiểu menu trung tâm).
        MonikaHeader(
            "Thư viện",
            subtitle = when {
                scanned == null -> "Đang quét…"
                deviceScanning -> "Đang quét máy… ($scanDirs thư mục)"
                else -> "${games.size} game trong máy" + if (documents.isNotEmpty()) " · ${documents.size} tài liệu" else ""
            },
            right = { CircleButton(R.drawable.ic_fluent_grid_24_regular, "Menu thư viện", { libMenu = true }) },
        )
        LazyVerticalGrid(
            GridCells.Fixed(2), Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = DockClearance),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(2) }) {
                LibraryStorageAccess(scanAccess, AppGraph.prefs, ::requestScanAccess,
                    onManual = { picker.launch(arrayOf("*/*")) })
            }
            // Game của lần cài trước chưa đọc được → xin quyền "Truy cập mọi tệp" (Android 11+).
            val lockedCount = games.count { it.locked }
            if (lockedCount > 0 && !scanAccess) item(span = { GridItemSpan(2) }) {
                MonikaCard(Modifier.fillMaxWidth(), shape = Radius.large, padding = PaddingValues(16.dp)) {
                    Text("$lockedCount game từ lần cài trước chưa mở được", style = Monika.type.cardTitle, color = c.text)
                    Text("Do bạn gỡ app rồi cài lại, Android chặn đọc game cũ. Cho phép \"Truy cập mọi tệp\" để chơi tiếp mà không phải tải lại.",
                        style = Monika.type.caption, color = c.textSecondary, modifier = Modifier.padding(top = 4.dp))
                    GradientButton("Cấp quyền", { requestScanAccess() }, Modifier.fillMaxWidth().padding(top = 12.dp), height = 44.dp)
                }
            }
            if (games.isNotEmpty()) item(span = { GridItemSpan(2) }) {
                OutlinedTextField(
                    query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, shape = Radius.pill,
                    placeholder = { Text("Tìm game trong máy", style = Monika.type.body, color = c.textSecondary) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_fluent_search_24_regular), null, Modifier.size(20.dp), tint = c.textSecondary) },
                    trailingIcon = if (query.isNotEmpty()) ({
                        Icon(painterResource(R.drawable.ic_fluent_dismiss_24_regular), "Xóa", Modifier.size(20.dp).clickable { query = "" }, tint = c.textSecondary)
                    }) else null,
                )
            }
            if (games.isNotEmpty()) item(span = { GridItemSpan(2) }) {
                ChipBar(
                    listOf(FILTER_ALL, FILTER_RECENT, FILTER_FREQUENT, FILTER_SYSTEM), filter,
                    { if (it == FILTER_SYSTEM) (if (filter == FILTER_SYSTEM) systemFilter else null)?.let { s -> "$s ▾" } ?: "Theo hệ máy ▾" else it },
                    { if (it == FILTER_SYSTEM) systemSheet = true else filter = it },
                    accent = true, contentPadding = PaddingValues(0.dp),
                )
            }
            // Game đang tải / giải nén — tiến độ ngay trên ô của game đó.
            if (tasks.isNotEmpty()) items(tasks, key = { it.id }) { t -> TaskTile(t) }
            lastPlayed?.takeIf { query.isBlank() }?.let { g ->
                item(span = { GridItemSpan(2) }) { Text("Tiếp tục chơi", style = Monika.type.sectionTitle, color = c.text) }
                item(span = { GridItemSpan(2) }) { ContinueCard(g) { play(g) } }
            }
            if (scanned == null) item(span = { GridItemSpan(2) }) {
                Box(Modifier.fillMaxWidth().height(240.dp), Alignment.Center) { Spinner() }
            } else if (games.isEmpty() && documents.isEmpty()) item(span = { GridItemSpan(2) }) {
                EmptyState(
                    R.drawable.fluent3d_video_game, "Chưa có game",
                    "Tải game ở tab Game, hoặc thêm file (.zip .rar .7z .nds .gba .iso…) có sẵn trong máy.",
                ) {
                    LibraryFolderLabel(GameStorage.games(context).absolutePath)
                    GradientButton("Thêm game từ máy", { picker.launch(arrayOf("*/*")) }, icon = R.drawable.ic_fluent_folder_add_24_regular)
                }
            } else if (games.isNotEmpty()) item(span = { GridItemSpan(2) }) {
                Text("Thư viện", style = Monika.type.sectionTitle, color = c.text, modifier = Modifier.padding(top = 4.dp))
            }
            if (shown.isEmpty() && games.isNotEmpty()) item(span = { GridItemSpan(2) }) {
                Text(
                    when { query.isNotBlank() -> "Không có game nào khớp \"$query\"."; filter == FILTER_RECENT -> "Chưa chơi game nào."; filter == FILTER_FREQUENT -> "Chơi một game vài lần là nó hiện ở đây."; else -> "Không có game." },
                    style = Monika.type.body, color = c.textSecondary, modifier = Modifier.padding(vertical = 24.dp),
                )
            }
            items(shown, key = { it.key }) { g ->
                GameTile(
                    g, onPlay = { play(g) }, onExtract = { password = ""; toExtract = g }, onMenu = { vn.aow.monika.runner.GamePreload.warm(context, g); gameMenu = g },
                    onRedownload = { redownload(context, g) }, onRequestAccess = ::requestScanAccess,
                )
            }
            val shownDocuments = documents.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
            if (shownDocuments.isNotEmpty()) item(span = { GridItemSpan(2) }) {
                Text("Tài liệu", style = Monika.type.sectionTitle, color = c.text, modifier = Modifier.padding(top = 4.dp))
            }
            items(shownDocuments, key = { "document:${it.key}" }, span = { GridItemSpan(2) }) { document ->
                LibraryDocumentCard(document, onOpen = {
                    try {
                        context.startActivity(documentViewIntent(context, document))
                    } catch (_: android.content.ActivityNotFoundException) {
                        info = "Chưa có ứng dụng mở loại tài liệu này. Hãy cài ứng dụng đọc tài liệu rồi thử lại."
                    } catch (_: Exception) {
                        info = "Chưa mở được tài liệu. Kiểm tra quyền đọc tệp rồi thử lại."
                    }
                }, onDelete = { documentToDelete = document })
            }
        }
      }

        // ---- Menu popup (cùng thiết kế menu trung tâm) ----
        MonikaMenuSheet(
            systemSheet, { systemSheet = false }, emptyList(),
            title = "Chọn hệ máy", subtitle = "${systems.size} hệ máy trong Thư viện",
            header = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    systems.forEach { (name, count) ->
                        SheetRow(
                            name + (if (games.any { it.system?.name == name && gameIsExperimental(it) }) " · Thử nghiệm" else ""),
                            subtitle = "$count game", icon = R.drawable.ic_fluent_xbox_controller_24_regular,
                            trailing = if (filter == FILTER_SYSTEM && systemFilter == name) ({ SheetChip("Đang xem", true) {} }) else null,
                            onClick = { systemFilter = name; filter = FILTER_SYSTEM; systemSheet = false },
                        )
                    }
                }
            },
        )
        val scanAll = scanAccess
        MonikaMenuSheet(
            libMenu, { libMenu = false },
            title = "Thư viện", subtitle = "${games.size} game · ${systems.size} hệ máy",
            actions = buildList {
                add(SheetAction("Thêm game từ máy", R.drawable.ic_fluent_folder_add_24_regular, highlight = true) { picker.launch(arrayOf("*/*")) })
                add(SheetAction(if (deviceScanning) "Đang quét…" else "Quét cả máy", R.drawable.ic_fluent_search_24_regular, enabled = !deviceScanning) {
                    if (!scanAll) requestScanAccess() else scanDevice()
                })
                add(SheetAction("Cài đặt giả lập", R.drawable.ic_fluent_settings_24_regular, onClick = onSettings))
                add(SheetAction("Tải lại danh sách", R.drawable.ic_fluent_arrow_clockwise_24_regular) { reloadKey++ })
                if (!scanAll) add(SheetAction("Cho phép đọc mọi tệp", R.drawable.ic_fluent_lock_closed_24_regular, badge = "") { requestScanAccess() })
            },
            header = {
                SheetRow(
                    if (scanAll) "Tự quét máy mỗi ngày" else "Chưa có quyền đọc mọi tệp",
                    subtitle = if (scanAll) "Game tìm thấy chơi thẳng từ chỗ cũ, không chép thêm."
                    else "Cấp quyền để thấy game trong Zalo, Download, ZArchiver…",
                    icon = if (scanAll) R.drawable.ic_fluent_shield_checkmark_24_regular else R.drawable.ic_fluent_info_24_regular,
                )
            },
        )
        GameMenuSheet(gameMenu, { gameMenu = null }, pinned,
            onPatched = { reloadKey++ },
            onRequestAccess = ::requestScanAccess,
            onPlay = { play(it) },
            onExtract = { password = ""; toExtract = it },
            onDelete = { toDelete = it },
            onTogglePin = { g ->
                pinned = if (g.key in pinned) pinned - g.key else pinned + g.key
                AppGraph.prefs.pinnedGames = pinned
                Toast.makeText(context, if (g.key in pinned) "Đã giữ lại: không tự dọn game này" else "Đã bỏ giữ lại", Toast.LENGTH_SHORT).show()
            },
        )
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
    documentToDelete?.let { document ->
        AlertDialog(
            onDismissRequest = { documentToDelete = null },
            title = { Text("Xóa tài liệu?", style = Monika.type.cardTitle) },
            text = { Text("Xóa \"${document.name}\" khỏi máy. Các tệp khác trong thư mục được giữ lại.", style = Monika.type.body, color = c.text) },
            confirmButton = { GradientButton("Xóa", {
                documentToDelete = null
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { AppGraph.library.deleteDocument(document) }
                    if (!ok) info = "Chưa xóa được tài liệu. Kiểm tra quyền đọc tệp rồi thử lại."
                    reloadKey++
                }
            }, height = 44.dp) },
            dismissButton = { DarkButton("Hủy", { documentToDelete = null }) },
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
                    busy = true
                    val taskId = "extract:${target.key}"
                    GameTasks.put(GameTask(taskId, target.name, target.meta?.cover, "Đang giải nén", null))
                    scope.launch {
                        val r = withContext(Dispatchers.IO) {
                            Importer.extractInPlace(target.dir, listOf(password) + AppGraph.config.current.archivePasswords, ExtractProgress { p -> GameTasks.progress(taskId, p) }, context.applicationContext)
                        }
                        GameTasks.remove(taskId)
                        busy = false
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
            title = { Text(if (game.external) "Ẩn khỏi Thư viện?" else "Xóa game?", style = Monika.type.cardTitle) },
            text = {
                Text(
                    if (game.external) "Ẩn \"${game.name}\" khỏi Thư viện. File game trong máy KHÔNG bị xóa:\n${game.entry?.path}"
                    else "Xóa \"${game.name}\" khỏi máy. Dữ liệu lưu game vẫn giữ.",
                    style = Monika.type.body, color = c.textSecondary,
                )
            },
            confirmButton = {
                GradientButton(if (game.external) "Ẩn" else "Xóa", {
                    toDelete = null
                    scanned = scanned?.filterNot { it.key == game.key } // Ẩn ngay, không chờ quét lại.
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { AppGraph.library.delete(game) }
                        if (!ok) info = "Chưa xóa hết \"${game.name}\": một số file do lần cài app trước tạo ra nên Android không cho xóa.\n\n" +
                            "Cấp quyền \"Truy cập mọi tệp\" (thẻ đầu Thư viện) rồi xóa lại, hoặc xóa thư mục bằng trình quản lý file:\n${game.dir.absolutePath}"
                        reloadKey++
                    }
                }, height = 44.dp)
            },
            dismissButton = { DarkButton("Hủy", { toDelete = null }) },
            containerColor = c.surface, shape = Radius.large,
        )
    }
}
