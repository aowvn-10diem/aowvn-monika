package vn.aow.monika.apkinstall

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
import vn.aow.monika.notify.Notifier

/**
 * Cài game ở nền (game vài GB: chép OBB mất vài phút) — có thông báo tiến độ, không bị ngắt khi tắt màn hình.
 * Kết quả ghi vào [ApkInstallFlow.state] cho màn hình đọc.
 */
class ApkInstallWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val result = ApkInstallFlow.inspected ?: return Result.failure()
        runCatching { setForeground(info(result.label ?: result.packageName, null)) }
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        val installer = ApkInstaller { pkg, parts, progress -> SessionInstaller.install(applicationContext, pkg, parts, progress) }
        val name = result.label ?: result.packageName
        var last = -1
        val end = ApkInstallFlow.execute(result, installer, device = DeviceInfo.current(applicationContext)) { st ->
            ApkInstallFlow.state.value = st
            val title = if (st.phase == UiState.Phase.COPYING_OBB) "Đang chép dữ liệu game" else "Đang cài game"
            if (st.percent != last) { last = st.percent ?: -1; runCatching { nm.notify(NOTIFICATION_ID, Notifier.progress(applicationContext, title, name, st.percent)) } }
        }
        runCatching { nm.cancel(NOTIFICATION_ID) }
        ApkInstallFlow.cleanup(result)
        val open = applicationContext.packageManager.getLaunchIntentForPackage(result.packageName)
            ?: Intent(applicationContext, ApkInstallActivity::class.java)
        when (end.phase) {
            UiState.Phase.DONE -> Notifier.downloadDone(applicationContext, "Đã cài xong: $name", "Bấm để chơi", open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            UiState.Phase.FAILED -> Notifier.downloadDone(applicationContext, "Chưa cài được: $name", end.failure?.text.orEmpty(), Intent(applicationContext, ApkInstallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            else -> Unit
        }
        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = info("Đang cài game", null)

    private fun info(name: String, pct: Int?): ForegroundInfo {
        val n = Notifier.progress(applicationContext, "Đang cài game", name, pct)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ForegroundInfo(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(NOTIFICATION_ID, n)
    }

    companion object {
        private const val NOTIFICATION_ID = 4203
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<ApkInstallWorker>().build())
        }
    }
}
