package vn.aow.monika.library

import android.content.Context
import android.os.Build
import android.os.Environment
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import vn.aow.monika.config.MonikaConfig
import java.io.File

/**
 * Quét toàn bộ bộ nhớ máy tìm file game (.nds .gba .gbc .iso .cso .jar .swf…, đuôi lấy từ config `systems`)
 * → đưa vào Thư viện mà KHÔNG chép/di chuyển file (chơi thẳng từ chỗ cũ).
 * Android 11+: cần quyền "Truy cập mọi tệp" mới thấy file của app khác (Zalo, trình duyệt, ZArchiver…).
 */
class DeviceScanner(private val context: Context) {
    private val file = File(context.filesDir, "device-games.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val list = ListSerializer(String.serializer())
    private val sp = context.getSharedPreferences("device_scan", Context.MODE_PRIVATE)

    /** Có quyền thấy file của app khác không. */
    fun canScanAll(): Boolean = Build.VERSION.SDK_INT < 30 || runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)

    val lastScan get() = sp.getLong("last", 0L)

    /** Đường dẫn game đã tìm thấy (còn tồn tại, chưa bị ẩn). */
    fun found(): List<File> {
        val hidden = hidden()
        return runCatching { json.decodeFromString(list, file.readText()) }.getOrDefault(emptyList())
            .filter { it !in hidden }.map(::File).filter { it.isFile }
    }

    /** Ẩn khỏi Thư viện (không xóa file của người dùng). */
    fun hide(path: String) = sp.edit().putStringSet("hidden", hidden() + path).apply()
    private fun hidden(): Set<String> = sp.getStringSet("hidden", emptySet()).orEmpty()

    /**
     * Quét và lưu kết quả. [onProgress] nhận số thư mục đã quét. Trả về số game tìm thấy.
     * Bỏ qua: thư mục ẩn, Android/data + obb, thư mục game của chính Monika (đã có trong thư viện), thư mục quá sâu.
     */
    fun scan(cfg: MonikaConfig, onProgress: (Int) -> Unit = {}): Int {
        val exts = cfg.systems.filter { it.runner != "apk" }.flatMap { it.extensions + it.extensionsSniffed }.map { it.lowercase() }.toSet() - setOf("ldb")
        val sniffedExts = cfg.systems.flatMap { it.extensionsSniffed }.map { it.lowercase() }.toSet()
        val root = Environment.getExternalStorageDirectory()
        val own = GameStorage.games(context).canonicalPath
        val hits = mutableListOf<String>()
        var dirs = 0
        fun walk(dir: File, depth: Int) {
            if (depth > MAX_DEPTH || hits.size >= MAX_FILES) return
            val children = dir.listFiles() ?: return
            dirs++
            if (dirs % 50 == 0) onProgress(dirs)
            for (f in children) {
                val name = f.name
                if (name.startsWith(".")) continue
                if (f.isDirectory) {
                    val path = runCatching { f.canonicalPath }.getOrNull() ?: continue
                    if (path == own || path.startsWith("$own/") || SKIP.any { path.endsWith(it) }) continue
                    walk(f, depth + 1)
                } else if (f.extension.lowercase() in exts && f.length() >= MIN_SIZE && RomSniff.accepts(f) && (f.extension.lowercase() !in sniffedExts || EntryPick.isXp3Exe(f))) {
                    hits += f.absolutePath
                }
            }
        }
        runCatching { walk(root, 0) }
        file.writeText(json.encodeToString(list, hits.distinct()))
        sp.edit().putLong("last", System.currentTimeMillis()).apply()
        return hits.size
    }

    companion object {
        private const val MAX_DEPTH = 8
        private const val MAX_FILES = 2000
        /** Bỏ file quá nhỏ (vd. .jar thư viện vài KB vẫn là game Java nên để thấp). */
        private const val MIN_SIZE = 8L * 1024
        private val SKIP = listOf("/Android/data", "/Android/obb", "/Android/media", "/DCIM", "/Pictures", "/Movies", "/Music", "/Ringtones", "/Notifications", "/Alarms")
    }
}
