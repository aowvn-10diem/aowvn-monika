package vn.aow.monika.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.aow.monika.AppGraph
import vn.aow.monika.R
import vn.aow.monika.config.ExternalApp
import vn.aow.monika.library.Game
import vn.aow.monika.library.GameInfoResolver
import vn.aow.monika.runner.LaunchResult
import vn.aow.monika.ui.MainActivity
import vn.aow.monika.ui.theme.CircleButton
import vn.aow.monika.ui.theme.DarkButton
import vn.aow.monika.ui.theme.DockClearance
import vn.aow.monika.ui.theme.EmptyState
import vn.aow.monika.ui.theme.GradientButton
import vn.aow.monika.ui.theme.Illustration
import vn.aow.monika.ui.theme.Monika
import vn.aow.monika.ui.theme.MonikaCard
import vn.aow.monika.ui.theme.MonikaHeader
import vn.aow.monika.ui.theme.MonikaIcon
import vn.aow.monika.ui.theme.Radius
import vn.aow.monika.ui.theme.Screen
import vn.aow.monika.ui.theme.Spinner
import vn.aow.monika.ui.theme.Tag
import vn.aow.monika.ui.theme.artworkScrim
import vn.aow.monika.ui.theme.pressable
import vn.aow.monika.ui.theme.primaryGradient
import java.util.Locale

/**
 * Mở trang chi tiết của game (V139): chuyển sang MainActivity kèm khóa game.
 * Dùng lại đường điều hướng sẵn có nên không phải đụng tới màn Thư viện đang sửa ở #428.
 */
internal fun openGamePage(context: Context, game: Game) {
    context.startActivity(
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_GAME_KEY, game.key)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
    )
}

/** Trang chi tiết 1 game: ảnh bìa, tên, hệ máy, dung lượng, đường dẫn + thanh Tác vụ (Chơi · Save · Bản vá · Xóa). */
@Composable
internal fun GameDetailScreen(game: Game, onPlay: (Game) -> Unit, onBack: () -> Unit, onDelete: (Game) -> Unit = {}) {
    val c = Monika.colors
    var confirmDelete by remember(game.key) { mutableStateOf(false) }
    var size by remember(game.key) { mutableStateOf<Long?>(null) }
    LaunchedEffect(game.key) { size = withContext(Dispatchers.IO) { gameSizeBytes(game) } }
    Screen {
        Column(Modifier.fillMaxSize()) {
            MonikaHeader(
                "Trang game",
                left = { CircleButton(R.drawable.ic_fluent_arrow_left_24_regular, "Quay lại", onBack) },
            )
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = DockClearance),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                GameArtwork(game)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(game.name, style = Monika.type.pageTitle, color = c.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Tag(game.system?.name ?: if (game.needsExtract) "Chưa giải nén" else "Chưa nhận diện")
                        if (game.external) Tag("Có sẵn trong máy")
                        ExperimentalTag(gameIsExperimental(game))
                    }
                }
                MonikaCard(Modifier.fillMaxWidth(), shape = Radius.large, padding = PaddingValues(16.dp)) {
                    DetailRow("Hệ máy", game.system?.name ?: if (game.needsExtract) "Chưa giải nén" else "Chưa nhận diện")
                    DetailRow("Dung lượng", size?.let { formatGameSize(it) } ?: "Đang tính…")
                    DetailRow("Đường dẫn", (game.entry ?: game.dir).path)
                }
                GameTaskBar(
                    onPlay = { onPlay(game) },
                    onDelete = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Save và Bản vá sẽ có ở bản cập nhật sau. Xóa game không xóa dữ liệu lưu trong máy.",
                    style = Monika.type.caption, color = c.textSecondary,
                )
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(if (game.external) "Ẩn khỏi Thư viện?" else "Xóa game?", style = Monika.type.cardTitle, color = c.text) },
        text = {
            Text(
                if (game.external) "Ẩn \"${game.name}\" khỏi Thư viện. File game trong máy KHÔNG bị xóa:\n${game.entry?.path.orEmpty()}"
                else "Xóa \"${game.name}\" khỏi máy. Dữ liệu lưu game vẫn giữ.",
                style = Monika.type.body, color = c.textSecondary,
            )
        },
        confirmButton = {
            GradientButton(if (game.external) "Ẩn" else "Xóa game", { confirmDelete = false; onDelete(game) }, height = 44.dp)
        },
        dismissButton = { DarkButton("Hủy", { confirmDelete = false }) },
        containerColor = c.surface, shape = Radius.large,
    )
}

/** Ảnh bìa từ bài viết, icon đọc trong file game, hoặc minh họa 3D khi chưa có ảnh. */
@Composable
private fun GameArtwork(game: Game) {
    val c = Monika.colors
    val cover = game.meta?.cover
    val icon = cover != null && GameInfoResolver.isIcon(cover)
    Box(
        Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(Radius.hero)
            .background(if (icon) Brush.linearGradient(listOf(c.surfaceSoft, c.track)) else primaryGradient()),
        contentAlignment = Alignment.Center,
    ) {
        when {
            icon -> AsyncImage(
                cover, null, contentScale = ContentScale.Fit,
                filterQuality = androidx.compose.ui.graphics.FilterQuality.None,
                modifier = Modifier.size(112.dp).clip(Radius.small),
            )
            cover != null -> {
                AsyncImage(cover, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(artworkScrim(0.4f)))
            }
            else -> Illustration(R.drawable.fluent3d_joystick, Modifier.size(96.dp))
        }
    }
}

/** Một dòng thông tin: nhãn cố định bên trái, giá trị co giãn bên phải. */
@Composable
private fun DetailRow(label: String, value: String) {
    val c = Monika.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, style = Monika.type.caption, color = c.textSecondary, modifier = Modifier.width(96.dp))
        Text(value, style = Monika.type.body, color = c.text, modifier = Modifier.weight(1f), maxLines = 4, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Thanh Tác vụ của trang game: Chơi · Save · Bản vá · Xóa.
 * V139 chỉ mở Chơi và Xóa; Save/Bản vá để nút + nhãn "Sắp có" (V140/V143 làm nội dung).
 */
@Composable
internal fun GameTaskBar(onPlay: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    MonikaCard(modifier, shape = Radius.large, padding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TaskAction("Chơi", R.drawable.ic_fluent_play_24_filled, onPlay, primary = true)
            TaskAction("Save", R.drawable.ic_fluent_save_24_regular, onClick = null, soon = true)
            TaskAction("Bản vá", R.drawable.ic_fluent_archive_24_regular, onClick = null, soon = true)
            TaskAction("Xóa", R.drawable.ic_fluent_delete_24_regular, onDelete, danger = true)
        }
    }
}

@Composable
private fun RowScope.TaskAction(
    label: String,
    @DrawableRes icon: Int,
    onClick: (() -> Unit)?,
    primary: Boolean = false,
    danger: Boolean = false,
    soon: Boolean = false,
) {
    val c = Monika.colors
    val interaction = remember { MutableInteractionSource() }
    val background = when {
        primary -> primaryGradient()
        soon -> Brush.linearGradient(listOf(c.track, c.track))
        else -> Brush.linearGradient(listOf(c.surfaceSoft, c.surfaceSoft))
    }
    val tint = when {
        primary -> Color.White
        danger -> c.danger
        soon -> c.textTertiary
        else -> c.text
    }
    Column(
        Modifier.weight(1f).then(if (onClick != null) Modifier.pressable(interaction, onClick) else Modifier).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(56.dp).clip(Radius.pill).background(background), contentAlignment = Alignment.Center) {
            MonikaIcon(icon, null, Modifier.size(24.dp), tint = tint)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label, style = Monika.type.caption.copy(fontWeight = Monika.type.bodyStrong.fontWeight),
            color = if (soon) c.textTertiary else c.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (soon) Text("Sắp có", style = Monika.type.caption, color = c.textTertiary, maxLines = 1)
    }
}

/** Dung lượng game: file lẻ của game quét được, hoặc cả thư mục game của Monika. Lỗi đọc → 0. */
internal fun gameSizeBytes(game: Game): Long = runCatching {
    val entry = game.entry
    if (game.external && entry != null && entry.isFile) entry.length()
    else game.dir.walkTopDown().maxDepth(6).filter { it.isFile }.sumOf { it.length() }
}.getOrDefault(0L)

/** "1,2 GB" / "640 MB" / "512 B"; chưa đọc được thì "Chưa rõ". */
internal fun formatGameSize(bytes: Long): String {
    if (bytes <= 0L) return "Chưa rõ"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) { value /= 1024; unit++ }
    val pattern = if (unit == 0 || value >= 100) "%.0f" else "%.1f"
    return pattern.format(Locale.US, value).replace('.', ',') + " " + units[unit]
}

/** Màn điều hướng của trang chi tiết: tìm game theo khóa, giữ luồng chạy/xóa hiện có. */
@Composable
fun GameDetailRoute(gameKey: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var game by remember(gameKey) { mutableStateOf(AppGraph.library.cached?.firstOrNull { it.key == gameKey }) }
    var loading by remember(gameKey) { mutableStateOf(game == null) }
    var message by remember { mutableStateOf<String?>(null) }
    var needApp by remember { mutableStateOf<ExternalApp?>(null) }
    LaunchedEffect(gameKey) {
        if (game == null) {
            game = withContext(Dispatchers.IO) { AppGraph.library.list().firstOrNull { it.key == gameKey } }
            loading = false
        }
    }
    // Cùng cửa chạy game của Thư viện: xác nhận hệ thử nghiệm, cần app ngoài, báo lỗi.
    val play = rememberGameLaunch { launched, result ->
        when (result) {
            is LaunchResult.NeedApp -> needApp = result.app
            is LaunchResult.OpenedApp -> message = "Đã mở ${result.app.name}. Trong app đó, chọn thư mục:\n${result.gamePath}"
            is LaunchResult.Failed -> message = result.message
            LaunchResult.Started -> AppGraph.prefs.markPlayed(launched.key)
            is LaunchResult.NeedExperimentalConfirmation -> Unit // thành phần chung đã hỏi trước khi gọi callback
        }
    }
    val shown = game
    when {
        shown != null -> GameDetailScreen(shown, play, onBack, onDelete = { target ->
            scope.launch {
                val ok = withContext(Dispatchers.IO) { AppGraph.library.delete(target) }
                if (ok) {
                    Toast.makeText(context, if (target.external) "Đã ẩn khỏi Thư viện" else "Đã xóa game", Toast.LENGTH_SHORT).show()
                    onBack()
                } else message = "Chưa xóa hết \"${target.name}\": một số file do lần cài app trước tạo ra nên Android không cho xóa.\n\n" +
                    "Cấp quyền \"Truy cập mọi tệp\" rồi xóa lại, hoặc xóa thư mục bằng trình quản lý file:\n${target.dir.absolutePath}"
            }
        })
        loading -> Screen { Box(Modifier.fillMaxSize(), Alignment.Center) { Spinner() } }
        else -> Screen {
            Column(Modifier.fillMaxSize()) {
                MonikaHeader("Trang game", left = { CircleButton(R.drawable.ic_fluent_arrow_left_24_regular, "Quay lại", onBack) })
                EmptyState(R.drawable.fluent3d_package, "Không tìm thấy game", "Game này đã bị xóa hoặc chuyển khỏi máy.")
            }
        }
    }
    needApp?.let { app ->
        AlertDialog(
            onDismissRequest = { needApp = null },
            confirmButton = { DarkButton("Đóng", { needApp = null }) },
            title = { Text("Cần cài ${app.name}", style = Monika.type.sectionTitle) },
            text = { ExternalAppDetails(app, installed = false) },
            containerColor = Monika.colors.surface, shape = Radius.large,
        )
    }
    message?.let {
        AlertDialog(
            onDismissRequest = { message = null },
            confirmButton = { GradientButton("OK", { message = null }, height = 44.dp) },
            text = { Text(it, style = Monika.type.body, color = Monika.colors.text) },
            containerColor = Monika.colors.surface, shape = Radius.large,
        )
    }
}

/** Khóa của game trong tham số điều hướng: đường dẫn có dấu "/" nên phải mã hóa. */
internal fun gameRoute(gameKey: String): String = "game/" + android.net.Uri.encode(gameKey)
