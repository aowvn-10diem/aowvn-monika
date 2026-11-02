package vn.aow.monika.library

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Kết quả xoá một game: xoá được hay không, bản sao lưu save (nếu có) và câu báo cho người dùng
 * (câu này ghi rõ vị trí bản sao lưu để người chơi tìm lại được).
 */
data class GameDeleteOutcome(val deleted: Boolean, val backup: File?, val message: String)

/**
 * Sao lưu save nằm **trong thư mục game** trước khi xoá (A54): save của engine (Ren'Py, RPG Maker,
 * ONScripter…) thường nằm ngay trong thư mục game nên xoá thư mục là mất luôn nếu không sao lưu.
 *
 * Save của giả lập (libretro, Azahar…) nằm ngoài thư mục game (filesDir/saves, …) nên không bị đụng.
 * Bản sao lưu là 1 tệp .zip trong `filesDir/save-backups`, giữ nguyên đường dẫn tương đối dưới `game/`
 * + tệp mô tả `monika-xoa-game.txt` (nhập lại game rồi chép `game/` vào là chơi tiếp được).
 */
object GameSaveBackup {

    /** Thư mục chứa bản sao lưu, tạo trước khi xoá. */
    const val DIR = "save-backups"

    /** Trần an toàn: quá 256 tệp / 64 MB thì dừng (nơi gọi phải bỏ xoá, không đoán bừa). */
    private const val MAX_FILES = 256
    private const val MAX_BYTES = 64L * 1024 * 1024

    /** Thư mục save quen dùng của engine, đặt trong thư mục game. */
    private val SAVE_DIR_NAMES = setOf("save", "saves", "savegame", "savegames", "savedata", "save_data", "save-data")

    /** Đuôi tệp save quen dùng. */
    private val SAVE_EXTENSIONS = setOf("sav", "srm", "dsv", "rmm", "rxdata", "rpgsave", "rvdata2", "save")

    /** Tệp save nằm trong [dir] (tối đa [MAX_FILES]); lỗi đọc → danh sách rỗng, không ném. */
    fun saveFilesInside(dir: File): List<File> = runCatching {
        dir.walkTopDown().maxDepth(3)
            .filter { it.isFile }
            .filter { file ->
                val relative = runCatching { file.relativeTo(dir) }.getOrNull() ?: return@filter false
                file.extension.lowercase(Locale.ROOT) in SAVE_EXTENSIONS ||
                    relative.invariantSeparatorsPath.split('/').dropLast(1)
                        .any { it.lowercase(Locale.ROOT) in SAVE_DIR_NAMES }
            }
            .take(MAX_FILES)
            .toList()
    }.getOrDefault(emptyList())

    /**
     * Ghi bản sao lưu save của [game] vào `filesDir/save-backups/xoa-<tên>-<giờ>.zip`.
     * Không có save trong thư mục → null (không cần sao lưu).
     * Ném lỗi khi không ghi được — nơi gọi **phải dừng, không xoá**.
     */
    fun of(context: Context, game: Game): File? {
        val saves = saveFilesInside(game.dir)
        if (saves.isEmpty()) return null
        val total = saves.sumOf { runCatching { it.length() }.getOrDefault(0L) }
        require(total <= MAX_BYTES) { "save trong thư mục quá lớn (${total / (1024 * 1024)} MB)" }
        val dir = File(context.filesDir, DIR).apply { check(mkdirs() || isDirectory) { "chưa tạo được thư mục sao lưu" } }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
        val target = File(dir, "xoa-${safeFileName(game.name)}-$stamp.zip")
        try {
            ZipOutputStream(target.outputStream().buffered()).use { zip ->
                for (file in saves) {
                    zip.putNextEntry(ZipEntry("game/" + file.relativeTo(game.dir).invariantSeparatorsPath))
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
    fun safeFileName(name: String): String =
        name.replace(Regex("[\\p{Cntrl}\\\\/:*?\"<>|]"), "_").trim().take(60).ifBlank { "game" }
}
