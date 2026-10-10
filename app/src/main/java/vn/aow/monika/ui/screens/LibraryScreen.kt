package vn.aow.monika.ui.screens

import vn.aow.monika.ui.theme.MonikaIcon
import vn.aow.monika.ui.theme.SoftPillButton
import android.net.Uri
import android.content.Intent
import android.provider.Settings
import android.os.Environment
import android.os.Build
import vn.aow.monika.ui.theme.Spinner
import androidx.compose.runtime.collectAsState
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
import androidx.compose.foundation.clickable
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
import vn.aow.monika.achievements.GameAchievementsSheet
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
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
    LaunchedEffect(reloadKey, playedTick, infoTick) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            scanned = withContext(Dispatchers.IO) { AppGraph.library.list() }
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
            legacyAccess.launch(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE))
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

    fun play(game: Game) {
        when (val r = AppGraph.launcher.launch(activity, game)) {
            is LaunchResult.NeedApp -> needApp = r.app
            is LaunchResult.OpenedApp -> { AppGraph.prefs.markPlayed(game.key); info = "Đã mở ${r.app.name}. Trong app đó, chọn thư mục:\n${r.gamePath}" }
            is LaunchResult.Failed -> info = r.message
            LaunchResult.Started -> AppGraph.prefs.markPlayed(game.key)
        }
    }
    playRef[0] = ::play

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
                else -> "${games.size} game trong máy"
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
            } else if (games.isEmpty()) item(span = { GridItemSpan(2) }) {
                EmptyState(
                    R.drawable.fluent3d_video_game, "Chưa có game",
                    "Tải game ở tab Game, hoặc thêm file (.zip .rar .7z .nds .gba .iso…) có sẵn trong máy.",
                ) {
                    LibraryFolderLabel(GameStorage.games(context).absolutePath)
                    GradientButton("Thêm game từ máy", { picker.launch(arrayOf("*/*")) }, icon = R.drawable.ic_fluent_folder_add_24_regular)
                }
            } else item(span = { GridItemSpan(2) }) {
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
                            name, subtitle = "$count game", icon = R.drawable.ic_fluent_xbox_controller_24_regular,
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
                            Importer.extractInPlace(target.dir, listOf(password) + AppGraph.config.current.archivePasswords, ExtractProgress { p -> GameTasks.progress(taskId, p) })
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

/** Thẻ "Tiếp tục chơi": charcoal + minh họa 3D + nút play gradient. */
@Composable
internal fun ContinueCard(g: Game, onPlay: () -> Unit) {
    MonikaCard(Modifier.fillMaxWidth(), dark = true, shape = Radius.hero, padding = PaddingValues(0.dp), onClick = onPlay) {
      Box {
        val cover = g.meta?.cover?.takeUnless { vn.aow.monika.library.GameInfoResolver.isIcon(it) }
        cover?.let {
            AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            Box(Modifier.matchParentSize().background(artworkScrim()))
        }
        Row(Modifier.padding(20.dp).padding(top = if (cover != null) 90.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(g.name, style = Monika.type.sectionTitle, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    g.system?.let { Tag(it.name, onDark = true) }
                    val t = AppGraph.prefs.playTime(g.key)
                    Tag(if (t > 0) "⏱ ${formatPlayTime(t)}" else "Việt hóa", onDark = true)
                }
            }
            Box(Modifier.size(64.dp).clip(Radius.pill).background(primaryGradient()), contentAlignment = Alignment.Center) {
                MonikaIcon(R.drawable.ic_fluent_play_24_filled, "Chơi", Modifier.size(30.dp), tint = Color.White)
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
private fun GameTile(g: Game, onPlay: () -> Unit, onExtract: () -> Unit, onMenu: () -> Unit, onRedownload: () -> Unit, onRequestAccess: () -> Unit) {
    val c = Monika.colors
    val waiting = g.needsExtract && g.system == null
    val playTime = AppGraph.prefs.playTime(g.key)
    Column {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.8f).clip(Radius.medium)
                // Giữ lâu ô game = mở menu của game.
                .pointerInput(g.key) { detectTapGestures(onLongPress = { onMenu() }) }
                .background(if (waiting) Brush.linearGradient(listOf(c.surfaceSoft, c.track)) else tileGradients[(g.name.hashCode() and 0x7fffffff) % tileGradients.size]),
            contentAlignment = Alignment.Center,
        ) {
            val cover = g.meta?.cover
            if (cover != null && vn.aow.monika.library.GameInfoResolver.isIcon(cover)) {
                // Icon nhỏ đọc từ file game (NDS/Java): vẽ giữa ô, giữ nét pixel.
                AsyncImage(cover, null, contentScale = ContentScale.Fit, filterQuality = androidx.compose.ui.graphics.FilterQuality.None, modifier = Modifier.size(88.dp).clip(Radius.small))
            } else if (cover != null) {
                // Ảnh bìa lấy từ bài viết aow.vn lúc tải.
                AsyncImage(cover, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(artworkScrim(0.4f)))
            } else {
                Illustration(if (waiting) R.drawable.fluent3d_package else R.drawable.fluent3d_joystick, Modifier.size(72.dp))
            }
            Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                CircleButton(R.drawable.ic_fluent_more_horizontal_24_regular, "Menu game", onMenu, style = vn.aow.monika.ui.theme.CircleStyle.Glass, size = 36.dp)
            }
            if (g.evicted) {
                Box(Modifier.fillMaxSize().background(Color(0x8C181719)))
                Box(Modifier.align(Alignment.Center)) { Tag("Đã dọn", onDark = true) }
            }
            Box(Modifier.align(Alignment.BottomCenter).padding(10.dp).fillMaxWidth()) {
                when {
                    g.evicted -> DarkButton("Tải lại", onRedownload, Modifier.fillMaxWidth(), icon = R.drawable.ic_fluent_arrow_download_24_regular)
                    g.locked -> DarkButton("Cấp quyền để mở", onRequestAccess, Modifier.fillMaxWidth())
                    waiting -> DarkButton("Giải nén", onExtract, Modifier.fillMaxWidth())
                    else -> GradientButton("Chơi", onPlay, Modifier.fillMaxWidth(), icon = R.drawable.ic_fluent_play_24_filled, height = 44.dp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(g.name, style = Monika.type.bodyStrong, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            when {
                g.evicted -> "Đã dọn để tiết kiệm bộ nhớ · save vẫn giữ"
                g.locked -> "Của lần cài trước · cấp quyền để mở"
                else -> (g.system?.name ?: if (g.needsExtract) "Chưa giải nén" else "Chưa nhận diện") +
                    (if (playTime > 0) " · ⏱ ${formatPlayTime(playTime)}" else "")
            },
            style = Monika.type.caption, color = c.textSecondary,
        )
    }
}

/** Android quyết định quyền; không dùng resultCode để giả định người dùng đã cho phép. */
internal fun allFilesAccessIntent(context: android.content.Context, appSpecific: Boolean = true): Intent =
    if (appSpecific) Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + context.packageName))
    else Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)

private const val FILTER_ALL = "Tất cả"
private const val FILTER_RECENT = "Chơi gần đây"
private const val FILTER_FREQUENT = "Chơi thường xuyên"
private const val FILTER_SYSTEM = "__system"

/** "2 giờ 5 phút" / "12 phút" / "dưới 1 phút". */
internal fun formatPlayTime(ms: Long): String {
    val min = ms / 60_000
    return when {
        min < 1 -> "dưới 1 phút"
        min < 60 -> "$min phút"
        min % 60 == 0L -> "${min / 60} giờ"
        else -> "${min / 60} giờ ${min % 60} phút"
    }
}

/** Mở lại bài viết gốc (tải lại game đã bị dọn / xem bài). */
private fun redownload(context: android.content.Context, g: Game) {
    val meta = g.meta
    when {
        meta?.postId != null -> context.startActivity(
            Intent(context, vn.aow.monika.ui.MainActivity::class.java)
                .putExtra(vn.aow.monika.ui.MainActivity.EXTRA_POST_ID, meta.postId)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        meta?.postUrl != null -> openUrl(context, meta.postUrl)
    }
}

/** Menu của 1 game (nút ⋯ trên ô hoặc giữ lâu): chơi, mở bài, giữ lại, giải nén, xóa/ẩn + thời gian đã chơi. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.GameMenuSheet(
    game: Game?, onDismiss: () -> Unit, pinned: Set<String>, onPatched: () -> Unit,
    onPlay: (Game) -> Unit, onExtract: (Game) -> Unit, onDelete: (Game) -> Unit, onTogglePin: (Game) -> Unit,
) {
    val context = LocalContext.current
    // Giữ game cuối cùng để menu vẫn có nội dung khi đang trượt xuống.
    var last by remember { mutableStateOf<Game?>(null) }
    if (game != null) last = game
    val g = last
    val prefs = AppGraph.prefs
    // Thành tựu RetroAchievements: chỉ khi đã đăng nhập và băm được game này (xem achievements/RaGameIndex).
    val raCreds by AppGraph.ra.creds.collectAsState()
    var raGameId by remember(g?.key, raCreds) { mutableStateOf<Int?>(null) }
    LaunchedEffect(g?.key, raCreds) { raGameId = if (g == null || raCreds == null) null else runCatching { AppGraph.raIndex.gameIdFor(g) }.getOrNull() }
    var raOpen by remember { mutableStateOf<Int?>(null) }
    // Vá Việt hóa: chọn file bản vá (.ips/.bps/.ups) → tạo bản "(Việt hóa)" cạnh ROM gốc, không đụng ROM gốc.
    val scope = rememberCoroutineScope()
    var patchTarget by remember { mutableStateOf<Game?>(null) }
    val patchPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = patchTarget; patchTarget = null
        if (uri != null && target != null) scope.launch {
            val msg = vn.aow.monika.patch.PatchFlow.run(context, target.entry!!, uri)
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            onPatched()
        }
    }
    MonikaMenuSheet(
        game != null, onDismiss,
        title = g?.name,
        subtitle = g?.let { x ->
            listOfNotNull(
                x.system?.name,
                prefs.playTime(x.key).takeIf { it > 0 }?.let { "Đã chơi ${formatPlayTime(it)}" },
                prefs.playCount(x.key).takeIf { it > 0 }?.let { "$it lần" },
            ).joinToString(" · ").ifBlank { null }
        },
        actions = if (g == null) emptyList() else buildList {
            if (g.system != null && !g.locked && !g.evicted) add(SheetAction("Chơi", R.drawable.ic_fluent_play_24_filled, highlight = true) { onPlay(g) })
            if (g.needsExtract) add(SheetAction("Giải nén", R.drawable.ic_fluent_archive_24_regular) { onExtract(g) })
            if (g.entry?.isFile == true && !g.needsExtract && g.system?.runner == "libretro")
                add(SheetAction("Vá Việt hóa (IPS/BPS/UPS)", R.drawable.ic_fluent_archive_24_regular) { patchTarget = g; patchPicker.launch(arrayOf("*/*")) })
            if (g.evicted) add(SheetAction("Tải lại", R.drawable.ic_fluent_arrow_download_24_regular, highlight = true) { redownload(context, g) })
            if (!g.evicted && (g.meta?.postId != null || g.meta?.postUrl != null)) add(SheetAction("Xem bài viết", R.drawable.ic_fluent_news_24_regular) { redownload(context, g) })
            if (g.meta != null && !g.external && !g.evicted) add(
                SheetAction(if (g.key in pinned) "Bỏ giữ lại" else "Giữ lại", if (g.key in pinned) R.drawable.ic_fluent_heart_24_filled else R.drawable.ic_fluent_heart_24_regular) { onTogglePin(g) }
            )
            raGameId?.let { id -> add(SheetAction("Thành tựu RetroAchievements", R.drawable.ic_fluent_star_24_regular) { raOpen = id }) }
            if (g.locked) add(SheetAction("Cấp quyền", R.drawable.ic_fluent_lock_closed_24_regular) { requestScanAccess() })
            add(SheetAction(if (g.external) "Ẩn khỏi Thư viện" else "Xóa game", R.drawable.ic_fluent_delete_24_regular) { onDelete(g) })
        },
        header = if (g == null) null else ({
            SheetRow(
                (g.entry ?: g.dir).path, subtitle = if (g.external) "Game có sẵn trong máy" else "Thư mục game của Monika",
                icon = R.drawable.ic_fluent_folder_open_24_regular,
            )
        }),
    )
    GameAchievementsSheet(raOpen) { raOpen = null }
}

/** Ô game đang tải / giải nén: ảnh bìa (nếu có) + vòng tiến độ + %. */
@Composable
private fun TaskTile(t: GameTask) {
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

/** Lõi libretro cần cho các game (theo lõi user chọn hoặc lõi mặc định của hệ). */
internal fun coreIdsOf(games: List<Game>): List<String> = games.mapNotNull { g ->
    val sys = g.system?.takeIf { it.runner == "libretro" } ?: return@mapNotNull null
    AppGraph.prefs.coreOverride(sys.id)?.takeIf { it in AppGraph.config.current.cores } ?: sys.core
}.distinct()
