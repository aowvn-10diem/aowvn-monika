package vn.aow.monika.ui.screens

import android.content.Intent
import androidx.compose.runtime.collectAsState
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import vn.aow.monika.AppGraph
import vn.aow.monika.achievements.GameAchievementsSheet
import vn.aow.monika.R
import vn.aow.monika.library.Game
import vn.aow.monika.ui.theme.Monika
import vn.aow.monika.ui.theme.MonikaMenuSheet
import vn.aow.monika.ui.theme.SheetAction
import vn.aow.monika.ui.theme.SheetRow

/** Mở lại bài viết gốc (tải lại game đã bị dọn / xem bài). */
internal fun redownload(context: android.content.Context, g: Game) {
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
internal fun androidx.compose.foundation.layout.BoxScope.GameMenuSheet(
    game: Game?, onDismiss: () -> Unit, pinned: Set<String>, onPatched: () -> Unit,
    onPlay: (Game) -> Unit, onExtract: (Game) -> Unit, onDelete: (Game) -> Unit, onTogglePin: (Game) -> Unit, onRequestAccess: () -> Unit,
) {
    val context = LocalContext.current
    // Giữ game cuối cùng để menu vẫn có nội dung khi đang trượt xuống.
    var last by remember { mutableStateOf<Game?>(null) }
    if (game != null) last = game
    val g = last
    val saveActions = rememberGameSaveActions(g)
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
            if (g.locked) add(SheetAction("Cấp quyền", R.drawable.ic_fluent_lock_closed_24_regular, onClick = onRequestAccess))
            add(SheetAction(if (g.external) "Ẩn khỏi Thư viện" else "Xóa game", R.drawable.ic_fluent_delete_24_regular) { onDelete(g) })
        },
        header = if (g == null) null else ({
            Column {
                SheetRow(
                    (g.entry ?: g.dir).path, subtitle = if (g.external) "Game có sẵn trong máy" else "Thư mục game của Monika",
                    icon = R.drawable.ic_fluent_folder_open_24_regular,
                )
                GameSaveButtons(vn.aow.monika.library.GameSaveTransfer.supported(g, AppGraph.config.current),
                    saveActions.busy, saveActions.onExport, saveActions.onImport)
            }
        }),
    )
    GameAchievementsSheet(raOpen) { raOpen = null }
}
