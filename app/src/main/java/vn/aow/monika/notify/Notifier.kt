package vn.aow.monika.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import vn.aow.monika.R
import vn.aow.monika.feed.Post
import vn.aow.monika.ui.MainActivity

object Notifier {
    private const val CH_POSTS = "posts"
    private const val CH_DOWNLOADS = "downloads"
    const val CH_ADB = "adb_pairing"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_POSTS, "Bài viết mới trên aow.vn", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_DOWNLOADS, "Tải game", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_ADB, "Ghép đôi gỡ lỗi không dây", NotificationManager.IMPORTANCE_HIGH))
    }

    fun newPost(context: Context, post: Post) {
        val open = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_POST_ID, post.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        show(context, CH_POSTS, post.id.hashCode(), "Bài mới trên AowVN", post.title, open)
    }

    fun downloadDone(context: Context, title: String, text: String, intent: Intent) =
        show(context, CH_DOWNLOADS, title.hashCode(), title, text, intent)

    /** Thông báo đang chạy (giải nén...) cho tác vụ nền. */
    /** [percent] null = thanh chạy vô định (chưa biết tiến độ). */
    fun progress(context: Context, title: String, text: String, percent: Int? = null): Notification =
        NotificationCompat.Builder(context, CH_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_stat_monika)
            .setContentTitle(if (percent == null) title else "$title $percent%")
            .setContentText(text)
            .setProgress(100, percent ?: 0, percent == null)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()

    private fun show(context: Context, channel: String, id: Int, title: String, text: String, intent: Intent) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        // Implicit file-view/install intents are dispatched by our unexported
        // activity after the user taps; PendingIntent itself always names a component.
        val component = intent.component
        val target = if (component != null) {
            // Explicit by construction: the copy gets its component set before it can reach the PendingIntent
            // (CodeQL java/android/implicit-pendingintents barrier).
            val explicit = Intent(intent)
            explicit.setComponent(component)
            explicit
        } else NotificationOpenActivity.intent(context, intent)
        val pi = PendingIntent.getActivity(
            context, id, target, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_monika)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, n)
    }
}
