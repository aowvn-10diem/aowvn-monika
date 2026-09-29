package vn.aow.monika.library

import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import vn.aow.monika.AppGraph
import vn.aow.monika.notify.Notifier

/**
 * Tải sẵn lõi giả lập ở nền, có thông báo tiến độ rõ ràng ("Đang tải lõi giả lập… 2/5 · mgba 40%").
 * Chạy lần đầu mở app (danh sách `prefetchCores` trong config) và sau khi giải nén game mới (lõi đúng hệ máy của game).
 * Tách khỏi ImportWorker để giải nén xong là báo "Đã tải xong" ngay, không phải chờ tải lõi.
 */
class CorePrefetchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val extra = inputData.getStringArray(KEY_IDS).orEmpty().toList()
        val ids = extra + AppGraph.config.current.prefetchCores
        if (AppGraph.cores.missing(ids).isEmpty()) return Result.success()
        runCatching { setForeground(info("Đang chuẩn bị giả lập", "Tải lõi giả lập lần đầu, chỉ một lần", null)) }
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        var lastPct = -1
        val ok = AppGraph.cores.prefetchWithProgress(ids) { id, i, n, pct ->
            if (pct != lastPct || i == 1) {
                lastPct = pct
                runCatching { nm.notify(NOTIFICATION_ID, Notifier.progress(applicationContext, "Đang tải lõi giả lập ($i/$n)", "$id · chỉ tải lần đầu, sau đó chơi ngay", pct)) }
            }
        }
        runCatching { nm.cancel(NOTIFICATION_ID) }
        val left = AppGraph.cores.missing(ids).size
        if (ok > 0 && left == 0) Notifier.downloadDone(
            applicationContext, "Giả lập đã sẵn sàng", "Đã tải xong $ok lõi. Bấm Chơi là vào game ngay.",
            android.content.Intent(applicationContext, vn.aow.monika.ui.MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        // Còn lõi lỗi (mất mạng...) → thử lại sau; WorkManager tự giãn cách.
        return if (left > 0 && runAttemptCount < 3) Result.retry() else Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = info("Đang chuẩn bị giả lập", "Tải lõi giả lập lần đầu, chỉ một lần", null)

    private fun info(title: String, text: String, pct: Int?): ForegroundInfo {
        val n = Notifier.progress(applicationContext, title, text, pct)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ForegroundInfo(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(NOTIFICATION_ID, n)
    }

    companion object {
        private const val KEY_IDS = "ids"
        private const val NOTIFICATION_ID = 4202

        /** Nhiều lần gọi gộp thành 1 lượt (APPEND_OR_REPLACE giữ lượt đang chạy, xếp tiếp lượt mới). */
        fun enqueue(context: Context, extraIds: Collection<String> = emptyList()) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "core-prefetch", ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<CorePrefetchWorker>()
                    .setInputData(androidx.work.Data.Builder().putStringArray(KEY_IDS, extraIds.toTypedArray()).build()).build(),
            )
        }
    }
}
