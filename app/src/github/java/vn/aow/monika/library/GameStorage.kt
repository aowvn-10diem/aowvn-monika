package vn.aow.monika.library

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * Game lưu ở Download/AowVN Monika/ để app ngoài (Kirikiroid2, JoiPlay...) đọc được
 * và game không mất khi gỡ app. Không ghi được thì lùi về thư mục riêng của app.
 */
object GameStorage {
    const val FOLDER = "AowVN Monika"

    fun isPublic(context: Context): Boolean = publicRoot().let { (it.isDirectory || it.mkdirs()) && it.canWrite() }

    fun root(context: Context): File =
        if (isPublic(context)) publicRoot()
        else context.getExternalFilesDir(FOLDER) ?: File(context.filesDir, FOLDER)

    fun games(context: Context) = File(root(context), "Game").apply { mkdirs() }
    fun downloads(context: Context) = File(root(context), "_TaiVe").apply { mkdirs() }

    private fun publicRoot() =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER)
}
