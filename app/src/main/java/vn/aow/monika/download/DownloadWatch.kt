package vn.aow.monika.download

import android.app.DownloadManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import vn.aow.monika.AppGraph
import vn.aow.monika.library.GameMeta

/** Trạng thái 1 lượt tải (đọc từ DownloadManager). */
data class DownloadState(val id: Long, val status: Int, val done: Long, val total: Long, val speed: Long) {
    val running get() = status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_PAUSED
    val progress get() = if (total > 0) done.toFloat() / total else 0f
}

object DownloadWatch {

    /** Theo dõi 1 lượt tải, cập nhật mỗi 0,7 giây tới khi xong / lỗi / bị hủy. */
    fun watch(context: Context, id: Long): Flow<DownloadState?> = flow {
        val dm = context.getSystemService(DownloadManager::class.java)
        var last = -1L
        while (true) {
            val s = query(dm, id, last)
            emit(s)
            if (s == null || !s.running) break
            last = s.done
            delay(700)
        }
    }.flowOn(Dispatchers.IO)

    /** Lượt tải đang chạy của bài viết [postId] (mở lại bài vẫn thấy tiến trình). */
    fun activeForPost(context: Context, postId: String): Long? {
        val dm = context.getSystemService(DownloadManager::class.java)
        val q = DownloadManager.Query().setFilterByStatus(DownloadManager.STATUS_RUNNING or DownloadManager.STATUS_PENDING or DownloadManager.STATUS_PAUSED)
        return dm.query(q)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                if (GameMeta.fromJson(AppGraph.prefs.downloadMeta(id))?.postId == postId) return@use id
            }
            null
        }
    }

    private fun query(dm: DownloadManager, id: Long, last: Long): DownloadState? =
        dm.query(DownloadManager.Query().setFilterById(id))?.use { c ->
            if (!c.moveToFirst()) return@use null
            val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            DownloadState(
                id, c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)), done,
                c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                if (last >= 0) ((done - last) * 1000 / 700).coerceAtLeast(0) else 0,
            )
        }
}
