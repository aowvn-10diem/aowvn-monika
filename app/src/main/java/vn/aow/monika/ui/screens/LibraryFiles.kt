package vn.aow.monika.ui.screens

import android.content.Context
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vn.aow.monika.R
import vn.aow.monika.AppGraph
import vn.aow.monika.library.Game
import vn.aow.monika.library.GameLibrary
import vn.aow.monika.library.GameStorage
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
            key = "game:$dir",
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
            sizeBytes = runCatching { document.file.length() }.getOrDefault(0L),
            addedAt = document.file.lastModified(),
            file = document.file,
            document = document,
        )
    }
    return entries.sortedWith(
        compareByDescending<LibraryFileEntry> { it.addedAt }.thenBy { it.name.lowercase(Locale.ROOT) },
    )
}

/** Dung lượng tệp (thư mục: cộng dồn bên trong). Tệp không đọc được tính 0, không làm hỏng mục Tệp. */
internal fun fileSize(file: File): Long = runCatching {
    if (file.isDirectory) file.walkTopDown().maxDepth(6).filter { it.isFile }.sumOf { it.length() }
    else file.length()
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

// ---- Xoá an toàn (V141): chỉ xoá thứ app quản lý, sao lưu save trước [A54] ----

/** Thư mục chứa bản sao lưu save tạo trước khi xoá (nằm trong filesDir, người dùng mở được). */
internal const val SAVE_BACKUP_DIR = "save-backups"

/** Tối đa 256 tệp / 64 MB giống giới hạn gói save của app — quá mức thì dừng, không xoá. */
private const val MAX_BACKUP_FILES = 256
private const val MAX_BACKUP_BYTES = 64L * 1024 * 1024

/** Thư mục chứa save của engine (đặt trong thư mục game). */
private val SAVE_DIR_NAMES = setOf("save", "saves", "savegame", "savegames", "savedata", "save_data", "save-data")

/** Đuôi tệp save quen dùng của các engine/giả lập. */
private val SAVE_EXTENSIONS = setOf("sav", "srm", "dsv", "rmm", "rxdata", "rpgsave", "rvdata2", "save")

/** Kết quả xoá một mục trong Tệp: có xoá được không, bản sao lưu save (nếu có), lời báo cho người dùng. */
internal data class FileDeleteResult(val deleted: Boolean, val backup: File?, val message: String)

/**
 * Thư mục game do app quản lý: đúng thư mục con cấp 1 của kho game Monika.
 * Ngoài phạm vi này (game quét trong máy, thư mục lạ) app không tự xoá.
 */
internal fun isManagedGameDir(context: Context, dir: File): Boolean = runCatching {
    val kho = GameStorage.games(context).canonicalFile
    val target = dir.canonicalFile
    target.isDirectory && target.parentFile == kho
}.getOrDefault(false)

/** Tệp save nằm trong thư mục game (save của engine); save giả lập nằm ngoài nên không bị đụng. */
internal fun saveFilesInside(dir: File): List<File> = runCatching {
    dir.walkTopDown().maxDepth(3)
        .filter { it.isFile }
        .filter { file ->
            val relative = runCatching { file.relativeTo(dir) }.getOrNull() ?: return@filter false
            file.extension.lowercase(Locale.ROOT) in SAVE_EXTENSIONS ||
                relative.invariantSeparatorsPath.split('/').dropLast(1)
                    .any { it.lowercase(Locale.ROOT) in SAVE_DIR_NAMES }
        }
        .take(MAX_BACKUP_FILES)
        .toList()
}.getOrDefault(emptyList())

/**
 * Sao lưu save trong thư mục game ra `filesDir/save-backups/xoa-<tên>-<giờ>.zip`
 * (giữ nguyên đường dẫn tương đối dưới `game/` + một tệp mô tả). Không có save → null.
 * Ném lỗi nếu không ghi được: nơi gọi phải dừng, không xoá.
 */
internal fun backupGameSaves(context: Context, game: Game): File? {
    val saves = saveFilesInside(game.dir)
    if (saves.isEmpty()) return null
    val total = saves.sumOf { runCatching { it.length() }.getOrDefault(0L) }
    require(total <= MAX_BACKUP_BYTES) { "save trong thư mục quá lớn (${formatFileSize(total)})" }
    val dir = File(context.filesDir, SAVE_BACKUP_DIR).apply { check(mkdirs() || isDirectory) { "chưa tạo được thư mục sao lưu" } }
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
    val target = File(dir, "xoa-${safeFileName(game.name)}-$stamp.zip")
    try {
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            for (file in saves) {
                val relative = file.relativeTo(game.dir).invariantSeparatorsPath
                zip.putNextEntry(ZipEntry("game/$relative"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("monika-xoa-game.txt"))
            zip.write(
                buildString {
                    appendLine("Monika — bản sao lưu save trước khi xoá game")
                    appendLine("game=${game.name}")
                    appendLine("hệ=${game.system?.id ?: "chưa rõ"}")
                    appendLine("thư mục=${game.dir.name}")
                    appendLine("số tệp save=${saves.size}")
                    appendLine("Khôi phục: nhập lại game rồi chép thư mục game/ trong tệp zip này vào thư mục game.")
                }.toByteArray(Charsets.UTF_8),
            )
            zip.closeEntry()
        }
    } catch (t: Throwable) {
        target.delete()
        throw t
    }
    return target
}

/** Tên tệp an toàn cho mọi hệ điều hành (bỏ ký tự cấm, cắt ngắn). */
internal fun safeFileName(name: String): String =
    name.replace(Regex("[\\p{Cntrl}\\\\/:*?\"<>|]"), "_").trim().take(60).ifBlank { "game" }

/**
 * Xoá một game từ mục Tệp:
 * 1. Game quét trong máy (ngoài kho) → chỉ ẩn, không xoá tệp nào.
 * 2. Thư mục không do app quản lý → từ chối, không đụng đĩa.
 * 3. Có save trong thư mục → sao lưu trước; sao lưu lỗi thì KHÔNG xoá gì.
 * Chỉ sau đó mới xoá đúng thư mục game (không đụng thư mục khác trong kho).
 */
internal fun deleteGameWithSaveBackup(
    context: Context,
    game: Game,
    library: GameLibrary = AppGraph.library,
    backup: (Context, Game) -> File? = ::backupGameSaves,
): FileDeleteResult {
    if (game.external) {
        val ok = library.delete(game)
        return FileDeleteResult(
            ok, null,
            if (ok) "Đã ẩn \"${game.name}\" khỏi Thư viện. Tệp game và save trong máy vẫn giữ nguyên."
            else "Chưa ẩn được \"${game.name}\" khỏi Thư viện.",
        )
    }
    if (!isManagedGameDir(context, game.dir)) {
        return FileDeleteResult(
            false, null,
            "App không xoá thư mục này vì nó không nằm trong kho game của Monika. " +
                "Muốn dọn, hãy dùng trình quản lý tệp của máy.",
        )
    }
    val saved = try {
        backup(context, game)
    } catch (t: Throwable) {
        return FileDeleteResult(
            false, null,
            "Chưa sao lưu được save nên chưa xoá gì: ${t.message ?: "lỗi không rõ"}. Thử lại hoặc dọn bằng trình quản lý tệp.",
        )
    }
    val ok = library.delete(game)
    val kept = saved?.let { "Bản sao lưu save: ${it.absolutePath}" }
        ?: "Game không có tệp save trong thư mục; save của giả lập trong kho Monika vẫn giữ nguyên."
    return FileDeleteResult(
        ok, saved,
        if (ok) "Đã xoá \"${game.name}\" khỏi kho.\n$kept"
        else "Chưa xoá hết \"${game.name}\": Android không cho xoá một số tệp. $kept",
    )
}

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
                MonikaIcon(entry.kind.icon, null, Modifier.size(22.dp), tint = Color.White)
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
