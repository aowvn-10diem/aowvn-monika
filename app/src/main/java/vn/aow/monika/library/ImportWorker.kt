package vn.aow.monika.library

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

        val result = Importer.importFile(applicationContext, file, deleteSource = true, AppGraph.config.current.archivePasswords)
        val open = Intent(applicationContext, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_LIBRARY, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (result.error == null) {
            Notifier.downloadDone(applicationContext, "Đã tải xong: ${result.dir.name}", "Bấm để mở thư viện và chơi", open)
        } else {
            Notifier.downloadDone(applicationContext, "Chưa giải nén được: ${result.dir.name}", "${result.error}\nVào Thư viện → Giải nén để thử lại.", open)
        }
        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val name = inputData.getString(KEY_FILE)?.let { File(it).name }.orEmpty()
        val notification = Notifier.progress(applicationContext, "Đang giải nén", name)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val KEY_FILE = "file"
        private const val NOTIFICATION_ID = 4201

        fun enqueue(context: Context, file: File) {
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<ImportWorker>().setInputData(workDataOf(KEY_FILE to file.absolutePath)).build()
            )
        }
    }
}
