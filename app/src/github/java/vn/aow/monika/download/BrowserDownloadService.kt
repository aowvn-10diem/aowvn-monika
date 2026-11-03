package vn.aow.monika.download

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vn.aow.monika.AppGraph
import vn.aow.monika.notify.Notifier

/** Giữ tiến trình sống khi đang tải từ trình duyệt + thông báo tiến độ; tự dừng khi hết lượt tải. */
class BrowserDownloadService : Service() {
    private val scope = CoroutineScope(Dispatchers.Main)
    private var loop: Job? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        post(initial = true)
        if (loop == null) loop = scope.launch {
            var idle = 0
            while (true) {
                delay(700)
                if (AppGraph.browserDownloads.activeJobs().isEmpty()) { if (++idle >= 3) break } else { idle = 0; post(initial = false) }
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun post(initial: Boolean) {
        val act = AppGraph.browserDownloads.activeJobs()
        val first = act.firstOrNull()
        val pct = first?.progress?.takeIf { it >= 0 }?.let { (it * 100).toInt() }
        val n = Notifier.progress(this, if (act.size > 1) "Đang tải ${act.size} file" else first?.name ?: "Đang tải", first?.let { speedText(it) } ?: "Đang chuẩn bị…", pct)
        if (initial) {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(NOTIF_ID, n)
        } else runCatching { NotificationManagerCompat.from(this).notify(NOTIF_ID, n) }
    }

    private fun speedText(j: DlJob): String {
        val mb = { b: Long -> String.format(java.util.Locale.US, "%.1f MB", b / 1048576.0) }
        return if (j.state == DlState.SAVING) "Đang lưu…" else if (j.total > 0) "${mb(j.done)} / ${mb(j.total)}" else mb(j.done)
    }

    override fun onDestroy() { loop?.cancel(); super.onDestroy() }

    companion object { private const val NOTIF_ID = 4301 }
}
