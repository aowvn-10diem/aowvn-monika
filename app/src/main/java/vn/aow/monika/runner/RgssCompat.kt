package vn.aow.monika.runner

import android.content.Context
import java.io.File

/** Chép script tương thích của Monika (assets/rgss/…) ra `filesDir/rgss-compat/` để mkxp-z đọc qua `preloadScript` (cần đường dẫn tệp thật). */
object RgssCompat {
    private const val WIN32API = "monika-win32api.rb"

    fun ensure(context: Context): File {
        val dir = File(context.filesDir, "rgss-compat").apply { mkdirs() }
        val out = File(dir, WIN32API)
        val bytes = context.assets.open("rgss/$WIN32API").use { it.readBytes() }
        if (!out.isFile || !out.readBytes().contentEquals(bytes)) out.writeBytes(bytes) // ghi lại khi app cập nhật
        return out
    }
}
