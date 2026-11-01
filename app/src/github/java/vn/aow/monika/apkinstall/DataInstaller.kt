package vn.aow.monika.apkinstall

import android.os.Environment
import java.io.File
import java.io.IOException

/** Android 9–10: app được ghi thẳng vào `Android/data/<gói>/` (cần quyền bộ nhớ). Android 11+ dùng Cách 1/2/3. */
object DataInstaller {
    fun dataDir(pkg: String, root: File = Environment.getExternalStorageDirectory()) = File(root, "Android/data/$pkg")

    sealed class Result {
        data class Ok(val files: Int, val bytes: Long) : Result()
        data class Failed(val text: String) : Result()
    }

    fun copyDirect(pkg: String, files: List<DataFile>, dir: File = dataDir(pkg), onProgress: (Int) -> Unit = {}): Result {
        if (files.isEmpty()) return Result.Ok(0, 0)
        val total = files.sumOf { it.payload.size }.coerceAtLeast(1)
        var done = 0L
        val base = dir.canonicalFile
        try {
            for (f in files) {
                val target = File(dir, f.relPath).canonicalFile
                if (!target.path.startsWith(base.path + File.separator)) throw IOException("Đường dẫn không hợp lệ: ${f.relPath}")
                target.parentFile?.let { if (!it.isDirectory && !it.mkdirs()) throw IOException("Không tạo được thư mục ${it.path}") }
                if (target.isFile && target.length() == f.payload.size) { done += f.payload.size; onProgress((done * 100 / total).toInt()); continue }
                val part = File(target.path + ".part")
                f.payload.open().use { i -> part.outputStream().use { o ->
                    val buf = ByteArray(256 * 1024)
                    while (true) { val n = i.read(buf); if (n < 0) break; o.write(buf, 0, n); done += n; onProgress((done * 100 / total).toInt().coerceIn(0, 100)) }
                } }
                if (part.length() != f.payload.size) { part.delete(); throw IOException("Chép thiếu dữ liệu: ${f.relPath}") }
                target.delete()
                if (!part.renameTo(target)) throw IOException("Không đặt được tên ${f.relPath}")
            }
        } catch (e: SecurityException) {
            return Result.Failed("Android không cho ghi dữ liệu game. Hãy cấp quyền bộ nhớ cho Aow Monika rồi thử lại.")
        } catch (e: IOException) {
            return Result.Failed("Chép dữ liệu lỗi: ${e.message}")
        }
        return Result.Ok(files.size, files.sumOf { it.payload.size })
    }
}
