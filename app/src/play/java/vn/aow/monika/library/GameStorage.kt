package vn.aow.monika.library
import android.content.Context
import java.io.File
/** Bản Play chỉ nhập SAF và lưu bản làm việc trong thư mục riêng. */
object GameStorage {
    const val FOLDER = "AowVN Monika"
    fun isPublic(context: Context) = false
    fun root(context: Context) = File(context.filesDir, FOLDER).apply { mkdirs() }
    fun games(context: Context) = File(root(context), "Game").apply { mkdirs() }
    fun downloads(context: Context) = File(context.cacheDir, "imports").apply { mkdirs() }
}
