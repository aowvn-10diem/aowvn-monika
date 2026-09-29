package vn.aow.monika.library

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import vn.aow.monika.AppGraph
import vn.aow.monika.notify.Notifier
import vn.aow.monika.ui.MainActivity
import java.io.File

/**
 * Giải nén game vừa tải xong ở chạy nền (game PSP/PC có thể nặng vài GB, mất vài phút).
 * Chạy bằng WorkManager nên không bị hệ thống ngắt giữa chừng như khi chạy trong BroadcastReceiver.
 */
class ImportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val file = File(inputData.getString(KEY_FILE) ?: return Result.failure())
        if (!file.exists()) return Result.failure()
        // Android 12+ có thể không cho chạy dịch vụ nền từ lúc này; khi đó vẫn giải nén bình thường.
        runCatching { setForeground(getForegroundInfo()) }

        // Cập nhật % trên thông báo đang chạy.
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        val meta = GameMeta.fromJson(inputData.getString(KEY_META))
        // Ô "Đang giải nén xx%" của đúng game này trong Thư viện.
        val taskId = "import:${file.path}"
        GameTasks.put(GameTask(taskId, meta?.title?.ifBlank { null } ?: file.nameWithoutExtension, meta?.cover, "Đang giải nén", null))
        val progress = ExtractProgress { p ->
            GameTasks.progress(taskId, p)
            // Cùng ID với thông báo foreground → hệ thống thay nội dung, không tạo thông báo mới.
            runCatching { nm.notify(NOTIFICATION_ID, Notifier.progress(applicationContext, "Đang giải nén", file.name, p)) }
        }
        val result = try {
            Importer.importFile(applicationContext, file, deleteSource = true, AppGraph.config.current.archivePasswords, progress)
        } finally {
            GameTasks.remove(taskId)
        }
        meta?.let { m ->
            GameMeta.write(result.dir, m)
            // Tải lại game từng bị dọn → bỏ ô "Đã dọn" cũ của cùng bài viết.
            if (m.postId != null) GameStorage.games(applicationContext).listFiles().orEmpty()
                .filter { it != result.dir && GameMeta.read(it)?.postId == m.postId && it.listFiles().orEmpty().all { f -> f.name == ".monika.json" } }
                .forEach { it.deleteRecursively() }
        }
        // Tải sẵn lõi giả lập cho game vừa nhận → lần đầu bấm Chơi không phải chờ.
        runCatching { AppGraph.cores.prefetch(vn.aow.monika.ui.screens.coreIdsOf(AppGraph.library.list().filter { it.dir == result.dir })) }
        // Game mới tải có thể làm vượt giới hạn bộ đệm → dọn game cũ, không đụng game vừa tải.
        if (!result.pending) runCatching { StorageCleaner.clean(applicationContext, protect = result.dir) }
        val name = meta?.title?.ifBlank { null } ?: result.dir.name
        val open = Intent(applicationContext, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_LIBRARY, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (result.pending) {
            Notifier.downloadDone(applicationContext, "Đang chờ phần tiếp theo: $name", result.error.orEmpty(), open)
        } else if (result.error == null) {
            Notifier.downloadDone(applicationContext, "Đã tải xong: $name", "Bấm để mở thư viện và chơi", open)
        } else {
            Notifier.downloadDone(applicationContext, "Chưa giải nén được: $name", "${result.error}\nVào Thư viện → Giải nén để thử lại.", open)
        }
        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        foregroundInfo(inputData.getString(KEY_FILE)?.let { File(it).name }.orEmpty(), null)

    private fun foregroundInfo(name: String, percent: Int?): ForegroundInfo {
        val notification = Notifier.progress(applicationContext, "Đang giải nén", name, percent)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val KEY_FILE = "file"
        private const val KEY_META = "meta"
        private const val NOTIFICATION_ID = 4201

        fun enqueue(context: Context, file: File, metaJson: String? = null) {
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<ImportWorker>()
                    .setInputData(workDataOf(KEY_FILE to file.absolutePath, KEY_META to metaJson)).build()
            )
        }
    }
}
