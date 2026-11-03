package vn.aow.monika.ui.screens

import android.content.ActivityNotFoundException
import android.content.ClipData
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vn.aow.monika.R
import vn.aow.monika.AppGraph
import vn.aow.monika.diag.Diagnostics
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
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
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

// ---- Xoá an toàn (V141): chỉ xoá thứ app quản lý, sao lưu trước [A54] ----

/** Thư mục chứa bản sao lưu tạo trước khi xoá (nằm trong filesDir, chia sẻ ra ngoài bằng FileProvider). */
internal const val SAVE_BACKUP_DIR = "save-backups"

/**
 * Trần dung lượng bản sao lưu. Vì sao lưu **toàn bộ** thư mục game (không lọc save) nên mức này
 * rộng hơn nhiều so với gói save; vượt mức thì **báo rõ** và từ chối xoá, không bỏ tệp nào.
 */
private const val MAX_BACKUP_BYTES = 4L * 1024 * 1024 * 1024

/** Dung lượng trống tối thiểu phải còn lại sau khi ghi bản sao lưu. */
private const val MIN_FREE_AFTER_BACKUP = 32L * 1024 * 1024

/** Kết quả quét thư mục game. [complete] = đã quét trọn vẹn; false thì **không được xoá**. */
internal data class GameScan(
    val files: List<File>,
    val notes: List<String> = emptyList(),
    val complete: Boolean = true,
)

/** Bản sao lưu: tệp zip (null = thư mục game rỗng), ghi chú và đã đủ chưa. */
internal data class GameBackup(
    val file: File?,
    val notes: List<String> = emptyList(),
    val complete: Boolean = true,
)

/** Kết quả xoá một mục trong Tệp: có xoá được không, bản sao lưu (nếu có), lời báo cho người dùng. */
internal data class FileDeleteResult(val deleted: Boolean, val backup: File?, val message: String)

/**
 * Thư mục game do app quản lý: đúng thư mục con cấp 1 của kho game Monika.
 * Ngoài phạm vi này (game quét trong máy, thư mục lạ) app không tự xoá.
 */
internal fun isManagedGameDir(context: Context, dir: File): Boolean = runCatching {
    val kho = GameStorage.games(context).canonicalFile
    val target = dir.canonicalFile
    target.isDirectory && target.parentFile == kho
}.onFailure { Diagnostics.recordHandled(context, "thu-vien", "kiểm tra thư mục game trong kho", it) }
    .getOrDefault(false)

/**
 * Quét **toàn bộ** thư mục game: mọi tệp thường, **không lọc theo đuôi/tên** — vì cái gì sắp bị xoá
 * thì phải được sao lưu hết (save có đuôi lạ như `.bin`, `.dat`, tệp không đuôi vẫn phải giữ).
 * Không giới hạn độ sâu/số tệp. Chỉ dừng ở chỗ không thể đọc hoặc gặp liên kết tượng trưng — khi đó
 * ghi chú + Diagnostics và [GameScan.complete] = false để nơi gọi **từ chối xoá**.
 */
internal fun scanGameFiles(context: Context, dir: File): GameScan {
    val files = mutableListOf<File>()
    val notes = mutableListOf<String>()
    var skippedLinks = 0
    var unreadable = 0
    try {
        Files.walkFileTree(
            dir.toPath(),
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(path: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (attrs.isSymbolicLink) {
                        skippedLinks++
                        return FileVisitResult.CONTINUE
                    }
                    if (attrs.isRegularFile) files += path.toFile()
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(path: Path, e: IOException): FileVisitResult {
                    unreadable++
                    Diagnostics.recordHandled(context, "thu-vien", "quét thư mục game: không đọc được $path", e)
                    return FileVisitResult.CONTINUE
                }
            },
        )
    } catch (t: Throwable) {
        unreadable++
        Diagnostics.recordHandled(context, "thu-vien", "quét thư mục game", t)
    }
    if (skippedLinks > 0) notes += "Bỏ qua $skippedLinks liên kết tượng trưng (không đi theo để tránh lặp vô hạn)."
    if (unreadable > 0) notes += "$unreadable mục trong thư mục không đọc được."
    return GameScan(
        files = files.sortedBy { it.absolutePath },
        notes = notes,
        complete = skippedLinks == 0 && unreadable == 0,
    )
}

/** Dung lượng tệp; lỗi đọc thì tính 0 và ghi Diagnostics. */
private fun scanFileBytes(context: Context, file: File): Long = runCatching { file.length() }
    .onFailure { Diagnostics.recordHandled(context, "thu-vien", "đọc dung lượng tệp để sao lưu", it) }
    .getOrDefault(0L)

/**
 * Sao lưu **toàn bộ** thư mục game ra `filesDir/save-backups/xoa-<tên>-<giờ>.zip`
 * (giữ nguyên cây dưới `game/` + tệp mô tả, kèm ghi chú nếu quét chưa trọn).
 * Không có tệp nào → [GameBackup.file] null (`complete` cho biết có quét được trọn không).
 * Ghi lỗi → ném để nơi gọi dừng, không xoá.
 */
internal fun backupGameFolder(context: Context, game: Game): GameBackup {
    val scan = scanGameFiles(context, game.dir)
    if (scan.files.isEmpty()) return GameBackup(null, scan.notes, scan.complete)
    val total = scan.files.sumOf { scanFileBytes(context, it) }
    if (total > MAX_BACKUP_BYTES) {
        return GameBackup(
            null,
            scan.notes + "Thư mục game lớn (${formatFileSize(total)}), vượt mức ${formatFileSize(MAX_BACKUP_BYTES)}.",
            complete = false,
        )
    }
    val dir = File(context.filesDir, SAVE_BACKUP_DIR).apply { check(mkdirs() || isDirectory) { "chưa tạo được thư mục sao lưu" } }
    val free = runCatching { dir.usableSpace }
        .onFailure { Diagnostics.recordHandled(context, "thu-vien", "đọc dung lượng trống để sao lưu", it) }
        .getOrDefault(0L)
    if (free > 0 && free < total + MIN_FREE_AFTER_BACKUP) {
        return GameBackup(
            null,
            scan.notes + "Không đủ dung lượng trống để sao lưu ${formatFileSize(total)} (còn ${formatFileSize(free)}).",
            complete = false,
        )
    }
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ROOT).format(Date())
    // Giữ tên NGUYÊN TỬ bằng CREATE_NEW rồi ghi vào đúng tệp vừa giữ (xem createUniqueBackupFile).
    val target = createUniqueBackupFile(context, dir, "xoa-${safeFileName(game.name)}-$stamp")
    try {
        ZipOutputStream(Files.newOutputStream(target.toPath()).buffered()).use { zip ->
            for (file in scan.files) {
                val relative = file.relativeTo(game.dir).invariantSeparatorsPath
                zip.putNextEntry(ZipEntry("game/$relative"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("monika-xoa-game.txt"))
            zip.write(
                buildString {
                    appendLine("Monika — bản sao lưu TOÀN BỘ thư mục game trước khi xoá")
                    appendLine("game=${game.name}")
                    appendLine("hệ=${game.system?.id ?: "chưa rõ"}")
                    appendLine("thư mục=${game.dir.name}")
                    appendLine("số tệp=${scan.files.size} (mọi tệp, không lọc save)")
                    appendLine("dung lượng=${formatFileSize(total)}")
                    scan.notes.forEach { appendLine("ghi chú=$it") }
                    appendLine("Khôi phục: nhập lại game rồi chép thư mục game/ trong tệp zip này vào thư mục game.")
                    appendLine("Hoặc dùng nút \"Chia sẻ bản sao lưu\" trong Thư viện để gửi tệp này ra ngoài.")
                }.toByteArray(Charsets.UTF_8),
            )
            zip.closeEntry()
        }
    } catch (t: Throwable) {
        // Xoá tệp zip dở; nếu xoá không được thì thôi (không ảnh hưởng dữ liệu người chơi).
        target.delete()
        Diagnostics.recordHandled(context, "thu-vien", "sao lưu thư mục game: ghi tệp zip", t)
        throw t
    }
    return GameBackup(target, scan.notes, scan.complete)
}

/**
 * Intent chia sẻ bản sao lưu qua FileProvider (`files-path` trong `file_paths.xml`),
 * để người dùng gửi/lưu tệp zip ra Drive, Zalo, ứng dụng quản lý tệp… — tức là **với tới được**.
 */
internal fun backupShareIntent(
    context: Context,
    backup: File,
    uriFor: (File) -> Uri = { file ->
        FileProvider.getUriForFile(context, context.packageName + ".files", file)
    },
): Intent {
    val uri = uriFor(backup)
    return Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Bản sao lưu save · ${backup.name}")
        clipData = ClipData.newUri(context.contentResolver, "Bản sao lưu save", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

/** Mở hộp chọn ứng dụng để chia sẻ bản sao lưu; trả câu lỗi nếu không mở được (null = đã mở). */
internal fun shareBackup(context: Context, backup: File): String? = try {
    context.startActivity(Intent.createChooser(backupShareIntent(context, backup), "Chia sẻ bản sao lưu save"))
    null
} catch (t: ActivityNotFoundException) {
    Diagnostics.recordHandled(context, "thu-vien", "chia sẻ bản sao lưu: không có ứng dụng nhận", t)
    "Máy chưa có ứng dụng nhận tệp để chia sẻ. Tệp vẫn nằm ở: ${backup.absolutePath}"
} catch (t: Exception) {
    Diagnostics.recordHandled(context, "thu-vien", "chia sẻ bản sao lưu", t)
    "Chưa chia sẻ được bản sao lưu. Tệp vẫn nằm ở: ${backup.absolutePath}"
}

/** Tên tệp an toàn cho mọi hệ điều hành (bỏ ký tự cấm, cắt ngắn). */
internal fun safeFileName(name: String): String =
    name.replace(Regex("[\\p{Cntrl}\\\\/:*?\"<>|]"), "_").trim().take(60).ifBlank { "game" }

/**
 * Giữ tên zip **nguyên tử** trong [dir]: `<base>-<4 hex>.zip`, tạo tệp bằng [Files.createFile]
 * (`CREATE_NEW`) nên hai lời gọi đồng thời không thể cùng giữ một tên — bên thua nhận
 * [FileAlreadyExistsException] và thử hậu tố khác (200 lần, sau đó `-extra<N>`; tối đa 10.000).
 * Không dùng kiểu `if (!exists())` vì vẫn ghi đè khi sao lưu song song.
 * Lỗi khác (hết chỗ/quyền) → ghi Diagnostics và ném để nơi gọi từ chối xoá.
 */
internal fun createUniqueBackupFile(context: Context, dir: File, base: String): File {
    repeat(200) {
        val candidate = dir.toPath().resolve("$base-${randomSuffix()}.zip")
        if (reserveBackupName(context, candidate)) return candidate.toFile()
    }
    var counter = 1
    while (counter <= 10_000) {
        val candidate = dir.toPath().resolve("$base-extra$counter.zip")
        if (reserveBackupName(context, candidate)) return candidate.toFile()
        counter++
    }
    throw IllegalStateException("chưa tạo được tên tệp sao lưu duy nhất trong ${dir.absolutePath}")
}

/** Giữ [path] bằng CREATE_NEW: true = giữ được, false = tên đã có người giữ. */
private fun reserveBackupName(context: Context, path: Path): Boolean = try {
    Files.createFile(path)
    true
} catch (_: FileAlreadyExistsException) { // diag: bỏ qua tên đã có, thử hậu tố khác
    false
} catch (t: IOException) {
    Diagnostics.recordHandled(context, "thu-vien", "sao lưu thư mục game: giữ tên tệp", t)
    throw IllegalStateException("chưa tạo được tệp sao lưu: ${t.message ?: "lỗi không rõ"}", t)
}

/** 4 ký tự hex ngẫu nhiên cho hậu tố tên zip. */
internal fun randomSuffix(): String =
    java.lang.Long.toHexString(java.util.Random().nextLong() and 0xffff).padStart(4, '0')

/**
 * Xoá một game từ mục Tệp:
 * 1. Game quét trong máy (ngoài kho) → chỉ ẩn, không xoá tệp nào.
 * 2. Thư mục không do app quản lý → từ chối, không đụng đĩa.
 * 3. **Sao lưu toàn bộ thư mục game** trước (mọi tệp, không lọc save); sao lưu lỗi hoặc chưa trọn
 *    (liên kết tượng trưng, mục không đọc được, quá lớn, thiếu chỗ) thì KHÔNG xoá gì.
 * Chỉ sau đó mới xoá đúng thư mục game (không đụng thư mục khác trong kho).
 */
internal fun deleteGameWithSaveBackup(
    context: Context,
    game: Game,
    library: GameLibrary = AppGraph.library,
    backup: (Context, Game) -> GameBackup = ::backupGameFolder,
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
        Diagnostics.recordHandled(context, "thu-vien", "xoá game: sao lưu thư mục game trước khi xoá", t)
        return FileDeleteResult(
            false, null,
            "Chưa sao lưu được thư mục game nên chưa xoá gì: ${t.message ?: "lỗi không rõ"}. " +
                "Thử lại hoặc dọn bằng trình quản lý tệp.",
        )
    }
    // Chưa quét/sao lưu trọn vẹn (liên kết tượng trưng, mục không đọc được, quá lớn, thiếu chỗ) → không xoá.
    if (!saved.complete) {
        return FileDeleteResult(
            false, saved.file,
            "Chưa chứng minh sao lưu đủ nên chưa xoá gì:\n" + saved.notes.joinToString("\n") +
                "\nHãy dọn bằng trình quản lý tệp nếu chắc chắn muốn xoá.",
        )
    }
    val ok = library.delete(game)
    val kept = saved.file?.let {
        "Bản sao lưu thư mục game: ${it.absolutePath}" +
            (if (saved.notes.isEmpty()) "" else "\nGhi chú: " + saved.notes.joinToString(" "))
    } ?: "Thư mục game rỗng nên không cần sao lưu; save của giả lập trong kho Monika vẫn giữ nguyên."
    return FileDeleteResult(
        ok, saved.file,
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
