package vn.aow.monika.library

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import vn.aow.monika.AppGraph
import vn.aow.monika.Prefs
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Dọn bộ nhớ đệm kiểu Telegram.
 *
 * Bộ đệm = thứ TẢI LẠI ĐƯỢC: game tải từ bài aow.vn, lõi giả lập, ảnh/bài viết đã xem, file tải dở.
 * KHÔNG BAO GIỜ đụng tới: save game, save state (filesDir/saves, filesDir/states), game user tự thêm từ máy.
 *
 * Game bị dọn chỉ còn lại file `.monika.json` → Thư viện hiện "Đã dọn · Tải lại" (mở lại bài viết).
 *
 * Thứ tự khi vượt giới hạn: ảnh/bài viết → lõi giả lập lâu không dùng → game lâu không chơi nhất.
 */
object StorageCleaner {
    private const val DAY = 24L * 60 * 60 * 1000
    private const val META = ".monika.json"

    enum class Kind { MEDIA, CORE, GAME }

    data class Item(val kind: Kind, val file: File, val size: Long, val lastUsed: Long, val label: String)

    data class Usage(val games: Long, val cores: Long, val media: Long) {
        val total get() = games + cores + media
    }

    data class Report(val freed: Long, val removedGames: List<String>)

    /** Dung lượng bộ đệm hiện tại theo nhóm (để hiển thị trong Cài đặt). */
    fun usage(context: Context): Usage {
        val items = collect(context)
        return Usage(
            games = items.filter { it.kind == Kind.GAME }.sumOf { it.size },
            cores = items.filter { it.kind == Kind.CORE }.sumOf { it.size },
            media = items.filter { it.kind == Kind.MEDIA }.sumOf { it.size },
        )
    }

    /**
     * @param protect thư mục không được xóa (vd. game vừa tải xong).
     * @param mediaOnly true = chỉ xóa ảnh/bài viết (nút "Xóa bộ nhớ đệm ảnh").
     */
    fun clean(context: Context, protect: File? = null, mediaOnly: Boolean = false): Report {
        val prefs = AppGraph.prefs
        val now = System.currentTimeMillis()
        var freed = 0L
        val removed = mutableListOf<String>()

        freed += cleanLeftoverDownloads(context, now)

        // 1. Xóa theo tuổi (không dùng quá N ngày).
        val items = collect(context).filterNot { protect != null && it.file.canonicalPath == protect.canonicalPath }
        val keep = mutableListOf<Item>()
        for (item in items) {
            val days = when (item.kind) {
                Kind.MEDIA -> prefs.autoDeleteMediaDays
                Kind.CORE -> if (mediaOnly) 0 else prefs.autoDeleteCoreDays
                Kind.GAME -> if (mediaOnly) 0 else prefs.autoDeleteGameDays
            }
            val expired = if (mediaOnly && item.kind == Kind.MEDIA) true else days > 0 && now - item.lastUsed > days * DAY
            if (expired) {
                freed += delete(item)
                if (item.kind == Kind.GAME) removed += item.label
            } else keep += item
        }

        // 2. Vượt giới hạn → xóa dần theo thứ tự ưu tiên + lâu không dùng nhất.
        val limit = prefs.cacheLimitBytes
        if (!mediaOnly && limit > 0) {
            var total = keep.sumOf { it.size }
            val order = keep.sortedWith(compareBy<Item>({ it.kind.ordinal }, { it.lastUsed }))
            for (item in order) {
                if (total <= limit) break
                val f = delete(item)
                freed += f
                total -= item.size
                if (item.kind == Kind.GAME) removed += item.label
            }
        }
        return Report(freed, removed)
    }

    private fun collect(context: Context): List<Item> = buildList {
        // Ảnh + bài viết (Coil, WebView...): từng file trong cacheDir, để xóa file cũ mà giữ file mới.
        context.cacheDir.walkTopDown().filter { it.isFile }.forEach {
            add(Item(Kind.MEDIA, it, it.length(), it.lastModified(), it.name))
        }
        // Lõi giả lập.
        File(context.filesDir, "cores").listFiles().orEmpty().filter { it.isDirectory }.forEach { dir ->
            add(Item(Kind.CORE, dir, sizeOf(dir), dir.lastModified(), dir.name))
        }
        // Game tải từ bài viết (có link bài để tải lại). Game tự thêm từ máy KHÔNG nằm trong bộ đệm.
        val prefs: Prefs = AppGraph.prefs
        val pinned = prefs.pinnedGames
        GameStorage.games(context).listFiles().orEmpty().filter { it.isDirectory && it.path !in pinned }.forEach { dir ->
            val meta = GameMeta.read(dir) ?: return@forEach
            if (meta.postUrl.isNullOrBlank() && meta.postId.isNullOrBlank()) return@forEach
            val size = sizeOf(dir) - (File(dir, META).takeIf { it.exists() }?.length() ?: 0)
            if (size <= 0) return@forEach // Đã dọn rồi.
            val used = maxOf(prefs.lastPlayed(dir.path), dir.lastModified())
            add(Item(Kind.GAME, dir, size, used, meta.title.ifBlank { dir.name }))
        }
    }

    private fun delete(item: Item): Long = when (item.kind) {
        Kind.GAME -> {
            // Giữ lại .monika.json để Thư viện biết mà hiện "Tải lại".
            item.file.listFiles().orEmpty().filter { it.name != META }.forEach { it.deleteRecursively() }
            item.size
        }
        else -> if (item.file.deleteRecursively()) item.size else 0L
    }

    /** File trong _TaiVe quá 1 ngày = tải dở/lỗi còn sót. */
    private fun cleanLeftoverDownloads(context: Context, now: Long): Long =
        GameStorage.downloads(context).listFiles().orEmpty()
            .filter { now - it.lastModified() > DAY }
            .sumOf { f -> sizeOf(f).also { f.deleteRecursively() } }

    private fun sizeOf(f: File): Long = if (f.isFile) f.length() else f.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}

/** Dọn bộ đệm mỗi ngày (chạy nền, khi máy rảnh). */
class CacheCleanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { StorageCleaner.clean(applicationContext) }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<CacheCleanWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("cache-clean", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
