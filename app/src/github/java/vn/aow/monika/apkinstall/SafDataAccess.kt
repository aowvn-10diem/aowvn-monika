package vn.aow.monika.apkinstall

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.IOException
import java.io.OutputStream

/** Thư mục đích để chép data: cài đặt thật dùng SAF ([DocumentFile]), test dùng thư mục thường. */
interface DocDir {
    fun size(name: String): Long?
    fun subDir(name: String): DocDir
    /** Mở ghi file [name] (tạo mới, ghi đè nếu có). */
    fun openWrite(name: String): OutputStream
    fun delete(name: String)
}

class FileDocDir(private val dir: File) : DocDir {
    override fun size(name: String) = File(dir, name).takeIf { it.isFile }?.length()
    override fun subDir(name: String) = FileDocDir(File(dir, name).also { if (!it.isDirectory && !it.mkdirs()) throw IOException("Không tạo được thư mục $name") })
    override fun openWrite(name: String): OutputStream = File(dir, name).outputStream()
    override fun delete(name: String) { File(dir, name).delete() }
}

class SafDocDir(private val context: Context, private val dir: DocumentFile) : DocDir {
    override fun size(name: String) = dir.findFile(name)?.takeIf { it.isFile }?.length()
    override fun subDir(name: String): DocDir {
        val d = dir.findFile(name)?.takeIf { it.isDirectory } ?: dir.createDirectory(name) ?: throw IOException("Không tạo được thư mục $name")
        return SafDocDir(context, d)
    }
    override fun openWrite(name: String): OutputStream {
        val f = dir.findFile(name) ?: dir.createFile("application/octet-stream", name) ?: throw IOException("Không tạo được file $name")
        return context.contentResolver.openOutputStream(f.uri, "wt") ?: throw IOException("Không ghi được file $name")
    }
    override fun delete(name: String) { dir.findFile(name)?.delete() }
}

/**
 * Cách 2 (kiểu ZArchiver): người chơi cấp quyền cho đúng thư mục `Android/data/<gói>` bằng bộ chọn thư mục của Android,
 * rồi Monika chép dữ liệu vào. Thư mục chỉ có khi game đã được cài và mở 1 lần.
 * Android 11–12 chạy; Android 13+ tùy bản vá — bản vá bảo mật 03/2024 trở đi chặn hẳn, khi đó Cách 2 báo lỗi và gợi ý Cách 3.
 */
object SafDataAccess {
    private const val AUTHORITY = "com.android.externalstorage.documents"
    private const val PREFS = "saf_data"

    fun docId(pkg: String) = "primary:Android/data/$pkg"

    /** Gợi ý cho bộ chọn thư mục mở sẵn đúng thư mục dữ liệu của game. */
    fun initialUri(pkg: String): Uri = DocumentsContract.buildDocumentUri(AUTHORITY, docId(pkg))

    /** Người chơi có chọn đúng thư mục không (chọn nhầm thư mục khác thì không được chép). */
    fun isExpectedTree(tree: Uri, pkg: String): Boolean = runCatching { DocumentsContract.getTreeDocumentId(tree) == docId(pkg) }.getOrDefault(false)

    fun savedTree(context: Context, pkg: String): Uri? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("tree_$pkg", null)?.let(Uri::parse)
    fun saveTree(context: Context, pkg: String, tree: Uri) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("tree_$pkg", tree.toString()).apply() }

    fun root(context: Context, tree: Uri): DocDir? = DocumentFile.fromTreeUri(context, tree)?.takeIf { it.canWrite() }?.let { SafDocDir(context, it) }

    sealed class Result {
        data class Ok(val files: Int, val bytes: Long) : Result()
        data class Failed(val text: String) : Result()
    }

    /** Chép mọi [files] vào [root] (đúng cấu trúc thư mục). File đã có đủ kích thước thì bỏ qua. */
    fun copy(root: DocDir, files: List<DataFile>, onProgress: (Int) -> Unit = {}): Result {
        if (files.isEmpty()) return Result.Ok(0, 0)
        val total = files.sumOf { it.payload.size }.coerceAtLeast(1)
        var done = 0L
        try {
            for (f in files) {
                val parts = f.relPath.split('/').filter { it.isNotEmpty() }
                if (parts.isEmpty() || parts.any { it == ".." || it == "." }) throw IOException("Đường dẫn không hợp lệ: ${f.relPath}")
                var dir = root
                for (d in parts.dropLast(1)) dir = dir.subDir(d)
                val name = parts.last()
                if (dir.size(name) == f.payload.size) { done += f.payload.size; onProgress((done * 100 / total).toInt()); continue }
                f.payload.open().use { i -> dir.openWrite(name).use { o ->
                    val buf = ByteArray(256 * 1024)
                    while (true) { val n = i.read(buf); if (n < 0) break; o.write(buf, 0, n); done += n; onProgress((done * 100 / total).toInt().coerceIn(0, 100)) }
                } }
                if (dir.size(name) != f.payload.size) { dir.delete(name); throw IOException("Chép thiếu dữ liệu: ${f.relPath}") }
            }
        } catch (e: SecurityException) {
            return Result.Failed("Android không cho ghi vào thư mục này. Hãy chọn lại thư mục dữ liệu của game.")
        } catch (e: IOException) {
            return Result.Failed("Chép dữ liệu lỗi: ${e.message}")
        }
        return Result.Ok(files.size, files.sumOf { it.payload.size })
    }
}
