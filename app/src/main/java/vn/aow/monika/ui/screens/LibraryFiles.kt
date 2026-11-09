package vn.aow.monika.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vn.aow.monika.R
import vn.aow.monika.AppGraph
import vn.aow.monika.library.Game
import vn.aow.monika.library.GameFolderBackup
import vn.aow.monika.library.GameLibrary
import vn.aow.monika.library.GameSaveBackup
import vn.aow.monika.library.LibraryDocument
import vn.aow.monika.ui.theme.EmptyState
import vn.aow.monika.ui.theme.GradientButton
import vn.aow.monika.ui.theme.Monika
import vn.aow.monika.ui.theme.MonikaCard
import vn.aow.monika.ui.theme.MonikaIcon
import vn.aow.monika.ui.theme.Radius
import vn.aow.monika.ui.theme.Tag
import vn.aow.monika.ui.theme.dockContentClearance
import vn.aow.monika.ui.theme.primaryGradient
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Mục Tệp (V141): mọi thứ người dùng đã đưa vào kho Monika, gộp game · tài liệu · tệp khác.
 * Nguồn dữ liệu là danh sách game + tài liệu đã có (GameLibrary) và đúng thư mục kho hiện tại,
 * không tạo kho mới.
 */
internal enum class LibraryFileKind(val label: String, @DrawableRes val icon: Int) {
    GAME("Game", R.drawable.ic_fluent_xbox_controller_24_regular),
    DOCUMENT("Tài liệu", R.drawable.ic_fluent_document_24_regular),
}

/**
 * Một dòng trong mục Tệp: chỉ những gì app quản lý (game đã nhận diện, tài liệu trong kho).
 * [game] hoặc [document] cho biết luồng xoá tương ứng.
 */
internal data class LibraryFileEntry(
    val key: String,
    val name: String,
    val kind: LibraryFileKind,
    val sizeBytes: Long,
    val addedAt: Long,
    val file: File,
    val game: Game? = null,
    val document: LibraryDocument? = null,
)

/**
 * Danh sách tệp của kho: game + tài liệu app đã nhận diện.
 * Thư mục lạ và tệp lẻ trong kho KHÔNG được liệt kê — app không quản lý nên không được phép xoá
 * (một thư mục save chưa nhận diện sẽ không bao giờ thành mục xoá được).
 * Mới thêm trước; cùng ngày thì theo tên. Không ném lỗi khi một tệp không đọc được.
 */
internal fun libraryFileEntries(
    games: List<Game>,
    documents: List<LibraryDocument>,
): List<LibraryFileEntry> {
    val entries = mutableListOf<LibraryFileEntry>()
    for (game in games) {
        val dir = game.dir.absolutePath
        // Game đã bị dọn bộ đệm (chỉ còn thông tin bài viết) không còn tệp nào để liệt kê.
        if (!game.dir.exists()) continue
        entries += LibraryFileEntry(
            // V207: game quét ngoài máy có thể dùng chung một thư mục (vd. Download);
            // khoá theo `game.key` (đường dẫn tệp chạy) để LazyColumn không trùng khoá.
            key = "game:${game.key}",
            name = game.name,
            kind = LibraryFileKind.GAME,
            // Game quét trong máy: chỉ tính đúng tệp chạy, không tính cả thư mục Download.
            sizeBytes = fileSize(if (game.external) game.entry ?: game.dir else game.dir),
            addedAt = game.dir.lastModified(),
            file = game.dir,
            game = game,
        )
    }
    for (document in documents) {
        entries += LibraryFileEntry(
            key = "doc:${document.key}",
            name = document.name,
            kind = LibraryFileKind.DOCUMENT,
            sizeBytes = fileSize(document.file),
            addedAt = document.file.lastModified(),
            file = document.file,
            document = document,
        )
    }
    return entries
        .sortedWith(compareByDescending<LibraryFileEntry> { it.addedAt }.thenBy { it.name.lowercase(Locale.ROOT) })
        // Chốt chặn cuối: khoá LazyColumn phải duy nhất kể cả khi kho có bản ghi trùng.
        .distinctBy { it.key }
}

/** Dung lượng tệp (thư mục: cộng dồn bên trong). Tệp không đọc được tính 0, không làm hỏng mục Tệp. */
internal fun fileSize(file: File): Long = runCatching {
    if (file.isDirectory) file.walkTopDown().maxDepth(6).filter { it.isFile }.sumOf { it.length() }
    else file.length()
// diag: bỏ qua lỗi đọc dung lượng vì đây chỉ là con số hiển thị (0 B); không ảnh hưởng xoá, sao lưu hay dữ liệu người chơi.
}.getOrDefault(0L)

/** "512 B" · "48 KB" · "1,4 MB" · "2,3 GB". */
internal fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        bytes < kb -> "$bytes B"
        bytes < mb -> "${(bytes / kb).toLong()} KB"
        bytes < gb -> oneDecimal(bytes / mb) + " MB"
        else -> oneDecimal(bytes / gb) + " GB"
    }
}

private fun oneDecimal(value: Double): String =
    String.format(Locale.ROOT, "%.1f", value).replace('.', ',')

/** Ngày thêm tệp: 02/11/2026. Không rõ thời điểm → "—". */
internal fun formatFileDate(addedAt: Long): String =
    if (addedAt <= 0L) "—" else SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).format(Date(addedAt))

// ---- Xoá an toàn (V141): chỉ xoá thứ app quản lý, sao lưu trước [A54] ----

/** Kết quả xoá một mục trong Tệp: có xoá được không, bản sao lưu (nếu có), lời báo cho người dùng. */
internal data class FileDeleteResult(val deleted: Boolean, val backup: File?, val message: String)

/**
 * Xoá game qua **một đường duy nhất**: [GameLibrary.deleteWithBackup] là nơi duy nhất chịu trách
 * nhiệm sao lưu (`GameSaveBackup.backupFolder`: quét toàn thư mục, kiểm dung lượng trống, tên zip
 * nguyên tử), kiểm thư mục có nằm trong kho Monika không, và chỉ xoá khi đã sao lưu trọn vẹn.
 *
 * Ở đây chỉ gọi lại rồi đổi kết quả sang [FileDeleteResult] cho mục Tệp. Trước V141-fix7 đường này
 * sao lưu trước rồi gọi `delete`, mà `delete` lại sao lưu lần nữa — **hai zip cho một lần xoá**,
 * tốn gấp đôi dung lượng và có thể làm thao tác xoá thất bại vì hết chỗ.
 */
internal fun deleteGameWithSaveBackup(
    context: Context,
    game: Game,
    library: GameLibrary = AppGraph.library,
    backup: (Context, Game) -> GameFolderBackup = { ctx, g -> GameSaveBackup.backupFolder(ctx, g) },
): FileDeleteResult {
    val outcome = library.deleteWithBackup(game) { backup(context, it) }
    return FileDeleteResult(outcome.deleted, outcome.backup, outcome.message)
}

/** Chia sẻ bản sao lưu qua FileProvider — cùng một cài đặt với [GameSaveBackup.share]. */
internal fun shareBackup(context: Context, backup: File): String? = GameSaveBackup.share(context, backup)

/** Intent chia sẻ bản sao lưu — cùng một cài đặt với [GameSaveBackup.shareIntent]. */
internal fun backupShareIntent(
    context: Context,
    backup: File,
    uriFor: (File) -> Uri = { file ->
        FileProvider.getUriForFile(context, context.packageName + ".files", file)
    },
): Intent = GameSaveBackup.shareIntent(context, backup, uriFor)


/** Xoá một tài liệu: đúng một tệp app đã nhận diện, không đụng tệp khác trong thư mục. */
internal fun deleteDocumentFile(document: LibraryDocument, library: GameLibrary = AppGraph.library): FileDeleteResult {
    val ok = library.deleteDocument(document)
    return FileDeleteResult(
        ok, null,
        if (ok) "Đã xoá tệp \"${document.name}\". Các tệp khác trong thư mục và save vẫn giữ nguyên."
        else "Chưa xoá được \"${document.name}\". Kiểm tra quyền đọc tệp rồi thử lại.",
    )
}

/**
 * Mục Tệp: danh sách tệp kèm loại, dung lượng, ngày thêm; bấm một dòng để mở đúng hành động.
 * Chưa có gì thì trạng thái rỗng + "+ Thêm" mở đúng luồng nhập sẵn có.
 */
@Composable
internal fun LibraryFilesPage(
    entries: List<LibraryFileEntry>,
    onOpen: (LibraryFileEntry) -> Unit,
    onDelete: (LibraryFileEntry) -> Unit,
    onAdd: () -> Unit,
) {
    if (entries.isEmpty()) {
        EmptyState(
            R.drawable.fluent3d_file_folder, "Chưa có tệp nào",
            "Game và tài liệu bạn thêm vào Thư viện sẽ hiện ở đây, kèm dung lượng và ngày thêm.",
        ) {
            GradientButton("+ Thêm", onAdd, icon = R.drawable.ic_fluent_folder_add_24_regular)
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = dockContentClearance()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "${entries.size} tệp · ${formatFileSize(entries.sumOf { it.sizeBytes })}",
                style = Monika.type.sectionTitle, color = Monika.colors.text,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
            )
        }
        items(entries, key = { it.key }) { entry ->
            LibraryFileRow(entry, onOpen = { onOpen(entry) }, onDelete = { onDelete(entry) })
        }
    }
}

/** Một tệp: biểu tượng theo loại, tên, loại · dung lượng · ngày thêm, nút xoá. */
@Composable
internal fun LibraryFileRow(entry: LibraryFileEntry, onOpen: () -> Unit, onDelete: () -> Unit) {
    val c = Monika.colors
    MonikaCard(
        Modifier.fillMaxWidth(), shape = Radius.large, padding = PaddingValues(14.dp),
        onClick = onOpen,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(Radius.thumb).background(primaryGradient()),
                contentAlignment = Alignment.Center,
            ) {
                MonikaIcon(entry.kind.icon, null, Modifier.size(22.dp), tint = c.textOnDark)
            }
            Column(
                Modifier.weight(1f).padding(start = 12.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    entry.name, style = Monika.type.cardTitle, color = c.text,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Tag(entry.kind.label)
                    Text(
                        "${formatFileSize(entry.sizeBytes)} · ${formatFileDate(entry.addedAt)}",
                        style = Monika.type.caption, color = c.textSecondary, maxLines = 1,
                    )
                }
            }
            // Nút xoá nhẹ (nền chip) để không tranh chỗ với hành động chính là bấm mở tệp.
            Box(
                Modifier.size(40.dp).clip(Radius.pill).background(c.chip).clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                MonikaIcon(
                    R.drawable.ic_fluent_delete_24_regular,
                    if (entry.game?.external == true) "Ẩn khỏi thư viện" else "Xoá tệp",
                    Modifier.size(20.dp), tint = c.chipText,
                )
            }
        }
    }
}
