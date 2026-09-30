package vn.aow.monika.apkinstall

import android.os.Environment
import java.io.File
import java.io.IOException

/**
 * Chép OBB vào `Android/obb/<gói>/`. App đã được bật "Cài ứng dụng không rõ nguồn gốc" được Android cho ghi vào đây
 * (chế độ bộ nhớ "installer"), nên không cần Gỡ lỗi hay quyền thư mục.
 */
object ObbInstaller {
    fun obbDir(pkg: String, root: File = Environment.getExternalStorageDirectory()) = File(root, "Android/obb/$pkg")

    sealed class Result {
        data class Ok(val files: Int, val bytes: Long) : Result()
        data class Failed(val text: String) : Result()
    }

    /** File đã có đủ kích thước thì bỏ qua (chạy lại sau khi bị ngắt không phải chép lại từ đầu). */
    fun copy(pkg: String, files: List<ObbFile>, dir: File = obbDir(pkg), onProgress: (Int) -> Unit = {}): Result {
        if (files.isEmpty()) return Result.Ok(0, 0)
        val total = files.sumOf { it.payload.size }.coerceAtLeast(1)
        var done = 0L
        try {
            if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Không tạo được thư mục OBB: ${dir.path}")
            for (f in files) {
                val target = File(dir, f.targetName)
                if (target.isFile && target.length() == f.payload.size) { done += f.payload.size; onProgress((done * 100 / total).toInt()); continue }
                val part = File(dir, f.targetName + ".part")
                f.payload.open().use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(1024 * 1024)
                        while (true) {
                            val n = input.read(buf); if (n < 0) break
                            out.write(buf, 0, n); done += n
                            onProgress((done * 100 / total).toInt().coerceIn(0, 100))
                        }
                    }
                }
                if (part.length() != f.payload.size) { part.delete(); throw IOException("Chép thiếu dữ liệu: ${f.targetName}") }
                target.delete()
                if (!part.renameTo(target)) throw IOException("Không đặt được tên ${f.targetName}")
            }
        } catch (e: SecurityException) {
            return Result.Failed("Android không cho ghi vào thư mục OBB. Hãy bật 'Cài ứng dụng không rõ nguồn gốc' cho Aow Monika rồi thử lại.")
        } catch (e: IOException) {
            val denied = e.message.orEmpty().contains("EACCES") || e.message.orEmpty().contains("Permission denied") || e.message.orEmpty().contains("EPERM")
            return Result.Failed(if (denied) "Android không cho ghi vào thư mục OBB. Hãy bật 'Cài ứng dụng không rõ nguồn gốc' cho Aow Monika rồi thử lại." else "Chép OBB lỗi: ${e.message}")
        }
        return Result.Ok(files.size, files.sumOf { it.payload.size })
    }
}
