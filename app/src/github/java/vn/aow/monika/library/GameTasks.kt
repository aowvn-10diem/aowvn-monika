package vn.aow.monika.library

import android.app.DownloadManager
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import vn.aow.monika.AppGraph

/** 1 game đang được tải / giải nén — hiện thành ô riêng trong Thư viện, tiến độ gắn liền với game đó. */
data class GameTask(
    val id: String,
    val title: String,
    val cover: String?,
    /** "Đang tải", "Đang giải nén", "Chờ mạng"… */
    val phase: String,
    /** 0..100, null = chưa biết (quay vòng). */
    val percent: Int?,
)

/** Các việc giải nén đang chạy (ImportWorker + thêm game từ máy). Lượt tải đọc thẳng từ DownloadManager. */
object GameTasks {
    val running = MutableStateFlow<Map<String, GameTask>>(emptyMap())

    fun put(task: GameTask) = running.update { it + (task.id to task) }
    fun progress(id: String, percent: Int) = running.update { m -> m[id]?.let { m + (id to it.copy(percent = percent)) } ?: m }
    fun remove(id: String) = running.update { it - id }

    /** Lượt tải game đang chạy (bỏ qua lượt tải app/plugin), kèm tên + ảnh bìa lấy từ bài viết. */
    fun downloads(context: Context): List<GameTask> {
        val dm = context.getSystemService(DownloadManager::class.java) ?: return emptyList()
        val q = DownloadManager.Query().setFilterByStatus(DownloadManager.STATUS_RUNNING or DownloadManager.STATUS_PENDING or DownloadManager.STATUS_PAUSED)
        return runCatching {
            dm.query(q)?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        val id = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                        if (AppGraph.prefs.isToolDownload(id)) continue
                        val meta = GameMeta.fromJson(AppGraph.prefs.downloadMeta(id))
                        val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        add(
                            GameTask(
                                "dl:$id",
                                meta?.title?.ifBlank { null } ?: c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)).orEmpty(),
                                meta?.cover,
                                if (status == DownloadManager.STATUS_PAUSED) "Chờ mạng" else "Đang tải",
                                if (total > 0) (done * 100 / total).toInt() else null,
                            )
                        )
                    }
                }
            }
        }.getOrNull().orEmpty()
    }
}
