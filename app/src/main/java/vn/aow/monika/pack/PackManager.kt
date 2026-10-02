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
        ONSYURI to Installer(
            ready = { AppGraph.packs.ready(ONSYURI, ONSYURI_MAIN) },
            supported = { AppGraph.packs.supported(ONSYURI) },
            ensure = { st -> AppGraph.packs.ensure(ONSYURI, ONSYURI_MAIN, st) },
        ),
        KIRIKIRI to Installer(
            ready = { AppGraph.packs.ready(KIRIKIRI, KIRIKIRI_LIB) },
            supported = { AppGraph.packs.supported(KIRIKIRI) },
            ensure = { st -> AppGraph.packs.ensure(KIRIKIRI, KIRIKIRI_LIB, st) },
        ),
        SEVENZIP to Installer(
            ready = { AppGraph.packs.ready(SEVENZIP, SEVENZIP_LIB) },
            supported = { AppGraph.packs.supported(SEVENZIP) },
            ensure = { st -> AppGraph.packs.ensure(SEVENZIP, SEVENZIP_LIB, st) },
        ),
    )

    const val ONSYURI = "onsyuri"
    const val ONSYURI_MAIN = "onsyuri.wasm"
    const val KIRIKIRI = "kirikiri"
    const val KIRIKIRI_LIB = "libkrkr2yuri.so"
    const val SEVENZIP = "sevenzip"
    const val SEVENZIP_LIB = "lib7-Zip-JBinding.so"

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
        val size = AppGraph.config.current.modules[id]?.let { it.sizeByAbi[AppGraph.packs.abi] ?: it.size } ?: 0L
        val cm = cm(context)
        val unmetered = !cm.isActiveNetworkMetered
        val saver = cm.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
        val decision = PackPolicy.decide(size, unmetered, saver, choiceOf(context, id))
        runCatching { vn.aow.monika.diag.Diagnostics.crumb(context, "pack", "yêu cầu $id ~${size / 1024}KB wifi=$unmetered tiết kiệm=$saver → $decision") }
        when (decision) {
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
        runCatching { vn.aow.monika.diag.Diagnostics.crumb(applicationContext, "pack", "bắt đầu $id (lần ${runAttemptCount + 1})") }
        runCatching { setForeground(info("Đang chuẩn bị thành phần", id, null)) }
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        return try {
            PackManager.install(id) { st ->
                val pct = st.substringAfterLast(' ').removeSuffix("%").toIntOrNull()
                runCatching { nm.notify(NOTI_ID, Notifier.progress(applicationContext, "Đang tải thành phần", id, pct)) }
            }
            runCatching { vn.aow.monika.diag.Diagnostics.crumb(applicationContext, "pack", "xong $id") }
            Result.success()
        } catch (e: Exception) {
            // Chỉ báo cáo khi hết lượt thử (retry còn thì chỉ ghi vệt) → không đẻ 3 báo cáo cho 1 lần tải lỗi.
            val last = runAttemptCount >= 2
            runCatching { vn.aow.monika.diag.Diagnostics.crumb(applicationContext, "pack", "$id lần ${runAttemptCount + 1} lỗi: ${e.javaClass.simpleName} ${e.message.orEmpty().take(80)}") }
            if (last) runCatching { vn.aow.monika.diag.Diagnostics.recordHandled(applicationContext, "pack:$id", "tải/cài lỗi sau ${runAttemptCount + 1} lần thử", e) }
            if (!last) Result.retry() else Result.failure()
        } finally { runCatching { nm.cancel(NOTI_ID) } }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = info("Đang chuẩn bị thành phần", "", null)

    private fun info(title: String, text: String, pct: Int?): ForegroundInfo {
        val n = Notifier.progress(applicationContext, title, text, pct)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ForegroundInfo(NOTI_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(NOTI_ID, n)
    }

    companion object { const val KEY_ID = "id"; private const val NOTI_ID = 4203 }
}
