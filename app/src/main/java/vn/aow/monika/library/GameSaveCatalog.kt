package vn.aow.monika.library

import android.content.Context
import vn.aow.monika.diag.Diagnostics
import java.io.File

/**
 * Một mục save hiện có của game, đủ để vẽ mục Save ở trang game: tên dễ đọc, thời điểm ghi và
 * dung lượng. Không đọc nội dung save (chỉ đọc metadata) nên liệt kê nhanh và không tốn bộ nhớ.
 */
data class GameSaveEntry(
    /** Tên hiển thị tiếng Việt, ví dụ "Save trong game (SRAM)" hay "State: lưu nhanh 1". */
    val name: String,
    /** Mốc thời gian ghi gần nhất (epoch millis); 0 nếu hệ thống không cho biết. */
    val savedAt: Long,
    /** Dung lượng tệp save (byte). */
    val size: Long,
)

/**
 * Liệt kê save hiện có của một game — chỉ đọc, không sửa gì trên đĩa.
 *
 * Dùng lại đúng bộ đường dẫn của [GameSaveTransfer] nên danh sách khớp với thứ mà bước xuất/nhập
 * thao tác. Lỗi cấu hình/kho save được ném thành [SaveTransferException] với câu tiếng Việt để
 * màn đọc báo rõ, không bao giờ văng app.
 */
object GameSaveCatalog {

    fun supported(context: Context, game: Game): Boolean = try {
        GameSaveTransfer.supported(game, vn.aow.monika.AppGraph.config.current)
    } catch (t: Throwable) {
        // Cấu hình hỏng cũng coi là "chưa hỗ trợ"; ghi mã lỗi (không kèm tên tệp/đường dẫn).
        Diagnostics.recordHandled(
            context, "app:save-transfer", "kiem-tra-ho-tro-save-loi", java.io.IOException(t.javaClass.simpleName),
        )
        false
    }

    /** Toàn bộ save/state hiện có, mới ghi trước. Game chưa có save → danh sách rỗng. */
    fun list(context: Context, game: Game): List<GameSaveEntry> {
        val target = GameSaveTransfer.target(context, game)
        val files = linkedMapOf<String, File>()
        when {
            target.suite != null -> target.root.listFiles().orEmpty()
                .filter { it.isFile && it.extension.lowercase() in setOf("rsh", "rsr") }
                .forEach { files["rms/${it.name}"] = it }
            target.directory != null -> files.putAll(ConsoleSaveTrees.snapshot(target))
            else -> target.ram?.let { files["ram.srm"] = it }
        }
        files.putAll(target.states)
        return entries(files)
    }

    /** Như [list] nhưng nhận thẳng danh sách tệp — dùng cho test và cho nơi đã có target. */
    internal fun entries(files: Map<String, File>): List<GameSaveEntry> = files
        // Placeholder 0 byte của lõi (RAM/state rỗng) không phải save để hiện; cây save thì giữ.
        .filter { (name, file) -> file.isFile && (file.length() > 0L || name.startsWith("save/")) }
        .map { (name, file) ->
            GameSaveEntry(
                name = label(name),
                savedAt = file.lastModified(),
                size = try {
                    file.length()
                // diag: bỏ qua vì chỉ là metadata dung lượng; đọc lỗi thì hiện 0 và UI ghi "Chưa rõ", không mất save.
                } catch (t: Throwable) {
                    0L
                },
            )
        }
        .sortedWith(compareByDescending<GameSaveEntry> { it.savedAt }.thenBy { it.name })

    /** Tên hệ thống → câu tiếng Việt cho người dùng. */
    private fun label(name: String): String = when {
        name == "ram.srm" -> "Save trong game (SRAM)"
        name.startsWith("state/") -> "State: " + stateLabel(name.removePrefix("state/"))
        name.startsWith("save/") -> name.removePrefix("save/")
        name.startsWith("rms/") -> "RMS: " + name.removePrefix("rms/")
        else -> name
    }

    private fun stateLabel(file: String): String = when {
        file.startsWith("auto") -> "tự động"
        file == "slot1.state" -> "lưu nhanh 1"
        file == "slot2.state" -> "lưu nhanh 2"
        file == "slot3.state" -> "lưu nhanh 3"
        else -> file.removeSuffix(".state")
    }
}
