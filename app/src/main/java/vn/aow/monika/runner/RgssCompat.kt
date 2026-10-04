package vn.aow.monika.runner

import android.content.Context
import java.io.File

/** Chép script tương thích của Monika (assets/rgss/…) ra `filesDir/rgss-compat/` để mkxp-z đọc qua `preloadScript` (cần đường dẫn tệp thật). */
object RgssCompat {
    private const val WIN32API = "monika-win32api.rb"

    fun ensure(context: Context): File = copy(context, WIN32API)

    /** Chỉ VX; cấu hình rgssVersion khác của người chơi được tôn trọng. */
    fun preloads(context: Context, gameDir: File): List<String> {
        val oldVersion = MkxpConfigWriter.parseExisting(File(gameDir, "mkxp.json").takeIf { it.isFile }?.readText())["rgssVersion"]
            ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() } ?: 0
        val vx = MkxpConfigWriter.detectRgss(gameDir) == 2 && oldVersion in listOf(0, 2)
        return listOf(ensure(context).absolutePath) +
            if (vx) listOf(copy(context, "monika-ruby18.rb").absolutePath) else emptyList()
    }

    private fun copy(context: Context, name: String): File {
        val dir = File(context.filesDir, "rgss-compat").apply { mkdirs() }
        val out = File(dir, name)
        val bytes = context.assets.open("rgss/$name").use { it.readBytes() }
        if (!out.isFile || !out.readBytes().contentEquals(bytes)) out.writeBytes(bytes) // ghi lại khi app cập nhật
        return out
    }
}
