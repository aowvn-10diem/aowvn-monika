package vn.aow.monika.notify

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast

/** Explicit notification target; external apps cannot supply forwarding intents. */
class NotificationOpenActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = if (Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra(EXTRA_TARGET, Intent::class.java)
        else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Intent>(EXTRA_TARGET)
        }
        try {
            if (target != null) startActivity(target)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Không có ứng dụng mở được file này.", Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, "Không có quyền mở file này.", Toast.LENGTH_LONG).show()
        } finally {
            finish()
        }
    }

    companion object {
        private const val EXTRA_TARGET = "notification_target"

        fun intent(context: Context, target: Intent): Intent =
            Intent(context, NotificationOpenActivity::class.java)
                .putExtra(EXTRA_TARGET, Intent(target))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
