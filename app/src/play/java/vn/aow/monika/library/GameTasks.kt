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

}
