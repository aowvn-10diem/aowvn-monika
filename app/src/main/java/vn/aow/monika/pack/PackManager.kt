package vn.aow.monika.pack

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import vn.aow.monika.AppGraph
import vn.aow.monika.R
import vn.aow.monika.notify.Notifier

/**
 * Tải trước các gói (engine/module) theo quy tắc mạng [PackPolicy].
 * - START → tải ngay bằng [PackWorker] (cần mạng bất kỳ).
 * - ASK → thông báo "Đợi Wi-Fi" / "Tải luôn bằng 4G"; nhớ lựa chọn theo gói.
 * - WAIT → [PackWorker] với điều kiện mạng không tính phí: WorkManager tự chạy ngay khi máy chuyển sang Wi-Fi,
 *   kể cả khi app đã bị tắt.
 * Gói mới = thêm 1 dòng vào [installers] + khai báo trong config.modules.
 */
object PackManager {
    private val installers: Map<String, Installer> = mapOf(
        "azahar" to Installer(
            ready = { AppGraph.azahar.ready() },
            supported = { AppGraph.azahar.available() },
            ensure = { st -> AppGraph.azahar.ensure(st) },
        ),
    )

    private class Installer(val ready: () -> Boolean, val supported: () -> Boolean, val ensure: suspend (onStatus: (String) -> Unit) -> Unit)

    private fun sp(c: Context) = c.getSharedPreferences("packs", Context.MODE_PRIVATE)
    private fun choiceOf(c: Context, id: String) = sp(c).getString("choice.$id", null)?.let { runCatching { PackChoice.valueOf(it) }.getOrNull() }
    private fun cm(c: Context) = c.getSystemService(ConnectivityManager::class.java)

    internal suspend fun install(id: String, onStatus: (String) -> Unit) {
        (installers[id] ?: throw java.io.IOException("Gói không rõ: $id")).ensure(onStatus)
    }

    fun needed(id: String): Boolean = installers[id]?.let { it.supported() && !it.ready() } == true

    /** Gọi khi đoán được gói cần (xem [vn.aow.monika.library.Prefetch]). Mọi lỗi được nuốt: tải trước không bao giờ làm hỏng việc chính. */
    fun prefetch(context: Context, ids: Collection<String>) = runCatching {
        for (id in ids.distinct().filter(::needed)) request(context.applicationContext, id)
    }

    fun request(context: Context, id: String) {
        val size = AppGraph.config.current.modules[id]?.size ?: 0L
        val cm = cm(context)
        val unmetered = !cm.isActiveNetworkMetered
        val saver = cm.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
        when (PackPolicy.decide(size, unmetered, saver, choiceOf(context, id))) {
            PackAction.START -> enqueue(context, id, wifiOnly = false)
            PackAction.WAIT -> enqueue(context, id, wifiOnly = true)
            PackAction.ASK -> ask(context, id, size)
        }
    }

    /** Người dùng bấm nút trong thông báo. */
    internal fun choose(context: Context, id: String, choice: PackChoice) {
        sp(context).edit().putString("choice.$id", choice.name).apply()
        runCatching { NotificationManagerCompat.from(context).cancel(askId(id)) }
        request(context, id)
    }

    private fun enqueue(context: Context, id: String, wifiOnly: Boolean) {
        val req = OneTimeWorkRequestBuilder<PackWorker>()
            .setInputData(Data.Builder().putString(PackWorker.KEY_ID, id).build())
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
            .build()
        // KEEP: gói đang tải/đang chờ thì không xếp thêm; đổi từ "đợi Wi-Fi" sang "tải luôn" thì thay thế.
        WorkManager.getInstance(context).enqueueUniqueWork("pack-$id", if (wifiOnly) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE, req)
    }

    private fun askId(id: String) = 4300 + (id.hashCode() and 0xff)

    private fun ask(context: Context, id: String, size: Long) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) { enqueue(context, id, wifiOnly = true); return } // không hỏi được → mặc định đợi Wi-Fi (an toàn cho gói dữ liệu)
        fun action(choice: PackChoice, label: String): NotificationCompat.Action {
            val i = Intent(context, PackChoiceReceiver::class.java).setPackage(context.packageName)
                .putExtra(PackChoiceReceiver.EXTRA_ID, id).putExtra(PackChoiceReceiver.EXTRA_CHOICE, choice.name)
            val pi = PendingIntent.getBroadcast(context, askId(id) * 2 + choice.ordinal, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            return NotificationCompat.Action(0, label, pi)
        }
        val mb = if (size > 0) " (~${size / (1024 * 1024)} MB)" else ""
        val n = NotificationCompat.Builder(context, "downloads")
            .setSmallIcon(R.drawable.ic_stat_monika)
            .setContentTitle("Cần tải thêm thành phần$mb")
            .setContentText("Bạn đang dùng mạng di động. Tải bây giờ hay đợi Wi-Fi?")
            .addAction(action(PackChoice.WAIT_WIFI, "Đợi Wi-Fi"))
            .addAction(action(PackChoice.USE_CELLULAR, "Tải luôn bằng 4G"))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(askId(id), n)
    }
}

class PackChoiceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val choice = intent.getStringExtra(EXTRA_CHOICE)?.let { runCatching { PackChoice.valueOf(it) }.getOrNull() } ?: return
        PackManager.choose(context.applicationContext, id, choice)
    }
    companion object { const val EXTRA_ID = "pack"; const val EXTRA_CHOICE = "choice" }
}

class PackWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.success()
        if (!PackManager.needed(id)) return Result.success()
        runCatching { setForeground(info("Đang chuẩn bị thành phần", id, null)) }
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        return try {
            PackManager.install(id) { st ->
                val pct = st.substringAfterLast(' ').removeSuffix("%").toIntOrNull()
                runCatching { nm.notify(NOTI_ID, Notifier.progress(applicationContext, "Đang tải thành phần", id, pct)) }
            }
            Result.success()
        } catch (e: Exception) {
            runCatching { vn.aow.monika.diag.Diagnostics.recordCoreFailure(applicationContext, "pack:$id", e.message ?: e.toString()) }
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        } finally { runCatching { nm.cancel(NOTI_ID) } }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = info("Đang chuẩn bị thành phần", "", null)

    private fun info(title: String, text: String, pct: Int?): ForegroundInfo {
        val n = Notifier.progress(applicationContext, title, text, pct)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ForegroundInfo(NOTI_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(NOTI_ID, n)
    }

    companion object { const val KEY_ID = "id"; private const val NOTI_ID = 4203 }
}
