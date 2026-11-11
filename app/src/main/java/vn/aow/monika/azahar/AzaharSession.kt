package vn.aow.monika.azahar

import android.content.Context
import vn.aow.monika.diag.Diagnostics
import java.io.File
import java.io.IOException

/**
 * Dấu "phiên chơi 3DS" để biết lần mở trước có sập **trước khung hình đầu** hay không.
 *
 * Bộ đệm shader trên đĩa (`Renderer/use_disk_shader_cache` trong [AzaharConfig]) được engine ghi thêm
 * trong lúc chơi. Tiến trình bị cắt giữa lúc ghi (sập native, ANR, bị kill) để lại tệp hỏng; lần mở sau
 * engine đọc lại rồi sập tiếp — người chơi thấy "mở game là văng". Vì vậy mỗi phiên ghi một dấu:
 * - [STARTED] — đã vào engine, chưa có khung hình đầu (nếu tiến trình chết ở đây thì cache có thể hỏng);
 * - [FIRST_FRAME] — đã có hình, cache đang được dùng bình thường;
 * - không có tệp — lần trước thoát sạch.
 *
 * Chỉ xoá cache khi dấu còn là [STARTED]. Mọi trạng thái khác (kể cả tệp lạ) đều giữ nguyên cache lành.
 */
object AzaharSession {
    const val STARTED = "started"
    const val FIRST_FRAME = "first-frame"

    /** Dấu phiên nằm ngay trong thư mục người dùng của engine, không lẫn với config.ini. */
    private const val STATE = "monika-session"

    /**
     * Bộ đệm shader trên đĩa nằm trong `<userDir>/cache` (bản dựng engine nhúng không kèm mã C++
     * để đối chiếu tên tệp). Đây là tệp dựng lại được, không chứa save/NAND/savestate.
     */
    private const val CACHE_DIR = "cache"

    /** Hàm thuần: dấu của phiên trước có nghĩa "sập trước khung hình đầu" không. */
    fun shaderCacheBroken(previous: String?): Boolean = previous == STARTED

    fun read(c: Context, dir: File): String? {
        val file = File(dir, STATE)
        if (!file.isFile) return null
        return try {
            file.readText().trim()
        } catch (e: IOException) {
            Diagnostics.recordHandled(c, "app:azahar-session", "Không đọc được dấu phiên chơi 3DS", e)
            null
        }
    }

    /** Vào engine: từ đây tới [firstFrame] mà tiến trình chết thì cache shader coi như có thể hỏng. */
    fun begin(c: Context, dir: File) = write(c, dir, STARTED)

    fun firstFrame(c: Context, dir: File) = write(c, dir, FIRST_FRAME)

    /** Thoát sạch (người chơi bấm thoát): lần mở sau không cần dọn cache. */
    fun end(c: Context, dir: File) {
        val file = File(dir, STATE)
        if (file.exists() && !file.delete()) Diagnostics.crumb(c, "azahar-session", "không xoá được dấu phiên chơi")
    }

    /**
     * Xoá bộ đệm shader của engine và trả về số tệp đã xoá (0 = không có gì để xoá).
     * Chỉ gọi khi [shaderCacheBroken] báo phiên trước sập trước khung hình đầu.
     */
    fun purgeShaderCache(c: Context, dir: File): Int {
        val cache = File(dir, CACHE_DIR)
        if (!cache.isDirectory) return 0
        var removed = 0
        for (file in cache.walkBottomUp().filter { it.isFile }.toList()) if (file.delete()) removed++
        // Con trước cha: dọn luôn các thư mục con rỗng còn lại.
        for (folder in cache.walkBottomUp().filter { it.isDirectory && it != cache }.toList()) folder.delete()
        if (cache.exists() && !cache.delete()) Diagnostics.crumb(c, "azahar-session", "còn thư mục cache shader chưa xoá được")
        return removed
    }

    private fun write(c: Context, dir: File, value: String) {
        try {
            dir.mkdirs()
            File(dir, STATE).writeText(value)
        } catch (e: IOException) {
            // Không có dấu phiên thì lần sau chỉ mất phần dọn cache tự động, game vẫn chạy bình thường.
            Diagnostics.recordHandled(c, "app:azahar-session", "Không ghi được dấu phiên chơi 3DS ($value)", e)
        }
    }
}
