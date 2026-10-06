package vn.aow.monika.diag

import android.content.Context
import vn.aow.monika.library.Xp3Index
import java.io.File

/**
 * V26: ghi vệt (breadcrumb) về lối vào game Kirikiri lúc mở, để báo cáo lỗi "Cannot find storage startup.tjs" có đủ dữ kiện:
 * đường dẫn truyền cho engine, tên + dung lượng mỗi .xp3/.exe trong thư mục game, và xp3 nào có `startup.tjs` ở gốc.
 * Chỉ ghi tên file/dung lượng/kết quả kiểm, KHÔNG ghi nội dung game. Mỗi dòng ≤ 200 ký tự (giới hạn của Breadcrumbs).
 */
object KirikiriDiag {
    private const val TAG = "kirikiri-entry"
    private const val MAX_FILES = 12

    /** Chạy ở luồng nền (chỉ mục XP3 có thể vài MB); mọi lỗi bị nuốt, không bao giờ làm hỏng việc mở game. */
    fun logEntryAsync(c: Context, entryPath: String?) {
        val app = c.applicationContext
        Thread({ runCatching { for (line in describe(entryPath)) Diagnostics.crumb(app, TAG, line) } }, "kirikiri-diag")
            .apply { isDaemon = true }.start()
    }

    /** Tách riêng để kiểm thử: trả về các dòng vệt cho một đường dẫn lối vào. */
    internal fun describe(entryPath: String?): List<String> {
        if (entryPath.isNullOrBlank()) return listOf("không có đường dẫn (vào trình duyệt file của Kirikiri)")
        val entry = File(entryPath)
        val out = ArrayList<String>()
        out += "truyền ${entry.path} file=${entry.isFile} dir=${entry.isDirectory} ${sizeOf(entry)}"
        val dir = if (entry.isDirectory) entry else entry.parentFile
        val files = dir?.listFiles().orEmpty().filter { it.isFile }
        val startupLoose = files.any { it.name.equals("startup.tjs", ignoreCase = true) }
        out += "thư mục có startup.tjs rời=$startupLoose; ${files.count { it.extension.equals("xp3", true) }} xp3, ${files.count { it.extension.equals("exe", true) }} exe"
        files.filter { it.extension.equals("xp3", true) || it.extension.equals("exe", true) }
            .sortedBy { it.name.lowercase() }.take(MAX_FILES).forEach { f ->
                out += if (f.extension.equals("xp3", true)) "xp3 ${f.name} ${sizeOf(f)} ${startupOf(f)}" else "exe ${f.name} ${sizeOf(f)}"
            }
        return out
    }

    private fun sizeOf(f: File) = if (f.isFile) "${f.length() / 1024}KB" else ""

    private fun startupOf(f: File): String = when (val r = Xp3Index.read(f)) {
        is Xp3Index.Result.Names -> "mục=${r.names.size}${if (r.complete) "" else "+"} startup.tjs@gốc=${Xp3Index.hasRootStartup(r.names)}"
        is Xp3Index.Result.Unreadable -> "chỉ mục không đọc được (${r.reason})"
    }
}
