package vn.aow.monika.library

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import vn.aow.monika.diag.Diagnostics
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
 * Kết quả xoá một game: xoá được hay không, bản sao lưu thư mục game (nếu có) và câu báo cho người
 * dùng (ghi rõ vị trí bản sao lưu để người chơi tìm lại được).
 */
data class GameDeleteOutcome(val deleted: Boolean, val backup: File?, val message: String)

/** Kết quả quét thư mục game. [complete] = đã quét trọn vẹn; false thì **không được xoá**. */
data class GameFolderScan(
    val files: List<File>,
    val notes: List<String> = emptyList(),
    val complete: Boolean = true,
)

/** Bản sao lưu thư mục game: tệp zip (null = thư mục rỗng), ghi chú và đã đủ chưa. */
data class GameFolderBackup(
    val file: File?,
    val notes: List<String> = emptyList(),
    val complete: Boolean = true,
)

/**
 * Sao lưu **toàn bộ thư mục game** trước khi xoá (A54): cái gì sắp bị xoá thì phải nằm trong zip —
 * không lọc theo đuôi/tên tệp, vì save của engine có thể là `.bin`, `.dat`, tệp không đuôi…
 *
 * Save của giả lập (libretro, Azahar…) nằm ngoài thư mục game (filesDir/saves, …) nên không bị đụng.
 * Bản sao lưu là 1 tệp .zip trong `filesDir/save-backups`, giữ nguyên cây dưới `game/` + tệp mô tả
 * `monika-xoa-game.txt`; người dùng lấy ra bằng nút "Chia sẻ bản sao lưu" (FileProvider + ACTION_SEND).
 */
object GameSaveBackup {

    /** Thư mục chứa bản sao lưu, tạo trước khi xoá. */
    const val DIR = "save-backups"

    /** Tên thành phần trong báo cáo Diagnostics. */
    private const val COMPONENT = "thu-vien"

    /** Trần dung lượng: vượt mức thì báo rõ + từ chối xoá, không bỏ tệp nào. */
    private const val MAX_BYTES = 4L * 1024 * 1024 * 1024

    /** Dung lượng trống tối thiểu phải còn lại sau khi ghi bản sao lưu. */
    private const val MIN_FREE_AFTER_BACKUP = 32L * 1024 * 1024

    /**
     * Quét **toàn bộ** cây thư mục game: mọi tệp thường, không giới hạn độ sâu/số tệp, không lọc
     * đuôi/tên. Chỉ dừng khi không thể đọc hoặc gặp liên kết tượng trưng — khi đó ghi chú + Diagnostics
     * và [GameFolderScan.complete] = false để nơi gọi **từ chối xoá** thay vì coi thiếu là "không có save".
     */
    fun scan(context: Context, dir: File): GameFolderScan {
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
                        Diagnostics.recordHandled(context, COMPONENT, "quét thư mục game: không đọc được $path", e)
                        return FileVisitResult.CONTINUE
                    }
                },
            )
        } catch (t: Throwable) {
            unreadable++
            Diagnostics.recordHandled(context, COMPONENT, "quét thư mục game", t)
        }
        if (skippedLinks > 0) notes += "Bỏ qua $skippedLinks liên kết tượng trưng (không đi theo để tránh lặp vô hạn)."
        if (unreadable > 0) notes += "$unreadable mục trong thư mục không đọc được."
        return GameFolderScan(
            files = files.sortedBy { it.absolutePath },
            notes = notes,
            complete = skippedLinks == 0 && unreadable == 0,
        )
    }

    /** Dung lượng tệp; lỗi đọc thì tính 0 và ghi Diagnostics. */
    private fun bytesOf(context: Context, file: File): Long = runCatching { file.length() }
        .onFailure { Diagnostics.recordHandled(context, COMPONENT, "đọc dung lượng tệp để sao lưu", it) }
        .getOrDefault(0L)

    /**
     * Ghi bản sao lưu **toàn bộ** thư mục game của [game] vào
     * `filesDir/save-backups/xoa-<tên>-<giờ>.zip`.
     * Thư mục rỗng → [GameFolderBackup.file] null (`complete` cho biết có quét được trọn không).
     * Vượt dung lượng / thiếu chỗ → `complete=false` (nơi gọi phải **từ chối xoá**).
     * Ghi zip lỗi → ném để nơi gọi dừng, không xoá.
     */
    fun backupFolder(context: Context, game: Game): GameFolderBackup {
        val scan = scan(context, game.dir)
        if (scan.files.isEmpty()) return GameFolderBackup(null, scan.notes, scan.complete)
        val total = scan.files.sumOf { bytesOf(context, it) }
        if (total > MAX_BYTES) {
            return GameFolderBackup(
                null,
                scan.notes + "Thư mục game lớn (${total / (1024 * 1024)} MB), vượt mức ${MAX_BYTES / (1024 * 1024)} MB.",
                complete = false,
            )
        }
        val dir = File(context.filesDir, DIR).apply { check(mkdirs() || isDirectory) { "chưa tạo được thư mục sao lưu" } }
        val free = runCatching { dir.usableSpace }
            .onFailure { Diagnostics.recordHandled(context, COMPONENT, "đọc dung lượng trống để sao lưu", it) }
            .getOrDefault(0L)
        if (free > 0 && free < total + MIN_FREE_AFTER_BACKUP) {
            return GameFolderBackup(
                null,
                scan.notes + "Không đủ dung lượng trống để sao lưu ${total / (1024 * 1024)} MB (còn ${free / (1024 * 1024)} MB).",
                complete = false,
            )
        }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ROOT).format(Date())
        // Giữ tên NGUYÊN TỬ: tạo tệp bằng CREATE_NEW rồi ghi vào đúng tệp vừa giữ (xem reserveName).
        val target = createUniqueBackupFile(context, dir, "xoa-${safeFileName(game.name)}-$stamp")
        try {
            ZipOutputStream(Files.newOutputStream(target.toPath()).buffered()).use { zip ->
                for (file in scan.files) {
                    zip.putNextEntry(ZipEntry("game/" + file.relativeTo(game.dir).invariantSeparatorsPath))
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
                        scan.notes.forEach { appendLine("ghi chú=$it") }
                        appendLine("Khôi phục: nhập lại game rồi chép thư mục game/ trong tệp zip này vào thư mục game.")
                        appendLine("Hoặc dùng nút \"Chia sẻ bản sao lưu\" để gửi tệp này ra ngoài.")
                    }.toByteArray(Charsets.UTF_8),
                )
                zip.closeEntry()
            }
        } catch (t: Throwable) {
            // Xoá tệp zip dở; nếu xoá không được thì thôi (không ảnh hưởng dữ liệu người chơi).
            target.delete()
            Diagnostics.recordHandled(context, COMPONENT, "sao lưu thư mục game: ghi tệp zip", t)
            throw t
        }
        return GameFolderBackup(target, scan.notes, scan.complete)
    }

    /**
     * Intent chia sẻ bản sao lưu qua FileProvider (`files-path` trong `file_paths.xml`),
     * để người dùng gửi/lưu tệp zip ra Drive, Zalo, ứng dụng quản lý tệp… — tức là **với tới được**.
     */
    fun shareIntent(
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
            putExtra(Intent.EXTRA_SUBJECT, "Bản sao lưu game · ${backup.name}")
            clipData = ClipData.newUri(context.contentResolver, "Bản sao lưu game", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Mở hộp chọn ứng dụng để chia sẻ bản sao lưu; trả câu lỗi nếu không mở được (null = đã mở). */
    fun share(context: Context, backup: File): String? = try {
        context.startActivity(Intent.createChooser(shareIntent(context, backup), "Chia sẻ bản sao lưu game"))
        null
    } catch (t: ActivityNotFoundException) {
        Diagnostics.recordHandled(context, COMPONENT, "chia sẻ bản sao lưu: không có ứng dụng nhận", t)
        "Máy chưa có ứng dụng nhận tệp để chia sẻ. Tệp vẫn nằm ở: ${backup.absolutePath}"
    } catch (t: Exception) {
        Diagnostics.recordHandled(context, COMPONENT, "chia sẻ bản sao lưu", t)
        "Chưa chia sẻ được bản sao lưu. Tệp vẫn nằm ở: ${backup.absolutePath}"
    }

    /** Tên tệp an toàn cho mọi hệ điều hành (bỏ ký tự cấm, cắt ngắn). */
    fun safeFileName(name: String): String =
        name.replace(Regex("[\\p{Cntrl}\\\\/:*?\"<>|]"), "_").trim().take(60).ifBlank { "game" }

    /**
     * Giữ tên zip **nguyên tử** trong [dir]: `<base>-<4 hex>.zip`, tạo tệp bằng
     * [Files.createFile] (`CREATE_NEW`) nên hai lời gọi đồng thời không thể cùng giữ một tên —
     * bên thua nhận [FileAlreadyExistsException] và thử hậu tố khác (tối đa 200 lần, sau đó
     * `-extra<N>`). Không dùng kiểu `if (!exists())` vì vẫn ghi đè khi sao lưu song song.
     * Lỗi khác (hết chỗ/quyền) → ghi Diagnostics và ném để nơi gọi dừng, không xoá.
     */
    internal fun createUniqueBackupFile(context: Context, dir: File, base: String): File {
        repeat(200) {
            val candidate = dir.toPath().resolve("$base-${randomSuffix()}.zip")
            if (reserveName(context, candidate)) return candidate.toFile()
        }
        var counter = 1
        while (counter <= 10_000) {
            val candidate = dir.toPath().resolve("$base-extra$counter.zip")
            if (reserveName(context, candidate)) return candidate.toFile()
            counter++
        }
        throw IllegalStateException("chưa tạo được tên tệp sao lưu duy nhất trong ${dir.absolutePath}")
    }

    /** Giữ [path] bằng CREATE_NEW: true = giữ được, false = đã có tên khác giữ. */
    private fun reserveName(context: Context, path: Path): Boolean = try {
        Files.createFile(path)
        true
    } catch (_: FileAlreadyExistsException) { // diag: bỏ qua tên đã có, thử hậu tố khác
        false
    } catch (t: IOException) {
        Diagnostics.recordHandled(context, COMPONENT, "sao lưu thư mục game: giữ tên tệp", t)
        throw IllegalStateException("chưa tạo được tệp sao lưu: ${t.message ?: "lỗi không rõ"}", t)
    }

    /** 4 ký tự hex ngẫu nhiên cho hậu tố tên zip. */
    private fun randomSuffix(): String =
        java.lang.Long.toHexString(java.util.Random().nextLong() and 0xffff).padStart(4, '0')
}
