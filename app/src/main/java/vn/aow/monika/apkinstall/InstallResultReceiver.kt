package vn.aow.monika.apkinstall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import vn.aow.monika.notify.Notifier

/** Nhận kết quả cài từ PackageInstaller (commit → PendingIntent → đây). */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_SESSION, -1)
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            // Android cần người chơi xác nhận: mở hộp thoại; nếu app đang ở nền bị chặn thì có thêm thông báo để bấm.
            val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirm) }
                Notifier.downloadDone(context, "Xác nhận cài game", "Bấm để xác nhận cài đặt trên Android.", confirm)
            }
            return
        }
        InstallResults.deliver(id, InstallOutcome.from(status, message))
    }

    companion object {
        const val EXTRA_SESSION = "vn.aow.monika.SESSION"
        const val ACTION = "vn.aow.monika.INSTALL_RESULT"
    }
}
