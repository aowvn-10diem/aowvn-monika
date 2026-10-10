package vn.aow.monika.diag

import android.content.Context
import vn.aow.monika.library.Xp3Index
import java.io.File

/**
 * V26: ghi vệt (breadcrumb) về lối vào game Kirikiri lúc mở, để báo cáo lỗi "Cannot find storage startup.tjs" có đủ dữ kiện:
 * lối vào truyền cho engine (chỉ tên file + tên thư mục game, không ghi đường dẫn đầy đủ), tên + dung lượng mỗi .xp3/.exe trong thư mục game, và xp3 nào có `startup.tjs` ở gốc.
 * Chỉ ghi tên file/dung lượng/kết quả kiểm, KHÔNG ghi nội dung game. Mỗi dòng ≤ 200 ký tự (giới hạn của Breadcrumbs).
 */
object KirikiriDiag {
    private const val TAG = "kirikiri-entry"
    private const val MAX_FILES = 12

    /** Giới hạn thời gian cho cả lượt chẩn đoán; quá hạn thì chỉ ghi một dòng và bỏ, không chờ thêm. */
    private const val TIMEOUT_MS = 8_000L

    /** Chạy ở luồng nền (chỉ mục XP3 có thể vài MB), có timeout; mọi lỗi bị nuốt, không bao giờ làm hỏng việc mở game. */
    fun logEntryAsync(c: Context, entryPath: String?) {
        val app = c.applicationContext
        val task = java.util.concurrent.FutureTask { describe(entryPath) }
        Thread(task, "kirikiri-diag").apply { isDaemon = true }.start()
        Thread({
            runCatching {
                val lines = try {
                    task.get(TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
                } catch (e: java.util.concurrent.TimeoutException) {
                    task.cancel(true); listOf("quá ${TIMEOUT_MS / 1000}s khi đọc thư mục/chỉ mục, bỏ qua")
                } catch (e: Exception) {
                    listOf("không rõ (${e.javaClass.simpleName})")
                }
                for (line in lines) Diagnostics.crumb(app, TAG, line)
            }
        }, "kirikiri-diag-log").apply { isDaemon = true }.start()
    }

    /** Văn bản hỗ trợ thủ công, giới hạn cùng 8 giây và chỉ chứa metadata như breadcrumb. */
    internal fun reportText(entryPath: String?): String {
        val lines = vn.aow.monika.library.TimedCall.run(TIMEOUT_MS, "kirikiri-diag-copy") { describe(entryPath) }
            ?: listOf("Không đọc được thông tin Kirikiri trong ${TIMEOUT_MS / 1000} giây")
        return Diagnostics.scrub(null, lines.joinToString("\n")).take(8 * 1024)
    }

    /** Tách riêng để kiểm thử: trả về các dòng vệt cho một đường dẫn lối vào. */
    internal fun describe(entryPath: String?): List<String> {
        if (entryPath.isNullOrBlank()) return listOf("không có đường dẫn (vào trình duyệt file của Kirikiri)")
        val entry = File(entryPath)
        val out = ArrayList<String>()
        val folder = (if (entry.isDirectory) entry else entry.parentFile)?.name.orEmpty()
        out += "truyền ${entry.name} thư mục=$folder file=${entry.isFile} dir=${entry.isDirectory} ${sizeOf(entry)}"
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
