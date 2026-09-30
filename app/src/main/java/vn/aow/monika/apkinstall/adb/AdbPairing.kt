package vn.aow.monika.apkinstall.adb

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import io.github.muntashirakon.adb.android.AdbMdns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import vn.aow.monika.R
import vn.aow.monika.apkinstall.ApkInstallActivity
import vn.aow.monika.apkinstall.ApkInstallWorker
import vn.aow.monika.notify.Notifier
import java.net.InetAddress

/**
 * Ghép đôi gỡ lỗi không dây mà không phải rời màn Cài đặt:
 * Monika dò cổng ghép đôi (mDNS) và hiện THÔNG BÁO có ô nhập mã 6 số; nhập xong là ghép đôi rồi tự chạy việc cài.
 * Chỉ chạy khi người chơi chủ động bấm "Bắt đầu ghép đôi"; tự dừng sau 5 phút.
 */
object AdbPairing {
    private const val NOTIF_ID = 4205
    private const val ACTION = "vn.aow.monika.ADB_PAIR"
    private const val KEY_CODE = "code"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var mdns: AdbMdns? = null
    @Volatile private var host: InetAddress? = null
    @Volatile private var port: Int = 0

    fun start(context: Context) {
        stop()
        val app = context.applicationContext
        show(app, "Đang chờ… Mở Cài đặt → Tùy chọn nhà phát triển → Gỡ lỗi không dây → Ghép nối thiết bị bằng mã ghép nối.", withInput = false)
        mdns = AdbMdns(app, AdbMdns.SERVICE_TYPE_TLS_PAIRING) { h, p ->
            if (p > 0) { host = h; port = p; show(app, "Nhập mã 6 số đang hiện trên màn hình ghép nối:", withInput = true) }
        }.also { it.start() }
        scope.launch { kotlinx.coroutines.delay(5 * 60_000L); stop(); cancel(app) }
    }

    fun stop() { runCatching { mdns?.stop() }; mdns = null }
    fun cancel(context: Context) { context.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID) }

    private fun show(context: Context, text: String, withInput: Boolean) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val open = PendingIntent.getActivity(context, 0, Intent(context, ApkInstallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        val b = NotificationCompat.Builder(context, Notifier.CH_ADB)
            .setSmallIcon(R.drawable.ic_stat_monika).setContentTitle("Ghép đôi gỡ lỗi không dây").setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true)
        if (withInput) {
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val pi = PendingIntent.getBroadcast(context, 1, Intent(context, AdbPairReceiver::class.java).setAction(ACTION), flags)
            val input = RemoteInput.Builder(KEY_CODE).setLabel("Mã ghép đôi 6 số").build()
            b.addAction(NotificationCompat.Action.Builder(R.drawable.ic_stat_monika, "Nhập mã", pi).addRemoteInput(input).build())
        }
        runCatching { nm.notify(NOTIF_ID, b.build()) }
    }

    internal fun onCode(context: Context, code: String) {
        val app = context.applicationContext
        val h = host?.hostAddress; val p = port
        if (h == null || p <= 0) { show(app, "Chưa thấy màn ghép đôi. Hãy mở \"Ghép nối thiết bị bằng mã ghép nối\" rồi nhập lại.", withInput = false); return }
        scope.launch {
            val adb = LocalAdb(app)
            val ok = adb.pairWith(h, p, code.trim())
            runCatching { adb.close() }
            if (ok) { stop(); cancel(app); ApkInstallWorker.enqueueAdb(app) }
            else show(app, "Mã sai hoặc đã hết hạn. Nhập lại mã mới đang hiện trên màn hình ghép nối:", withInput = true)
        }
    }

    class AdbPairReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val code = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_CODE)?.toString().orEmpty()
            if (code.isNotBlank()) onCode(context, code)
        }
    }
}
