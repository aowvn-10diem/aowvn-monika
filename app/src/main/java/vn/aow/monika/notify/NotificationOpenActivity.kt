package vn.aow.monika.notify

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast

/**
 * Explicit, unexported notification target. It never forwards an Intent received from outside (CodeQL
 * java/android/intent-redirection): only primitive fields travel in extras, and a NEW Intent is built here
 * after checking them against what the app itself produces (VIEW on a content URI of our own FileProvider).
 */
class NotificationOpenActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = buildTarget(this, intent.getStringExtra(EXTRA_ACTION), intent.getStringExtra(EXTRA_DATA),
            intent.getStringExtra(EXTRA_TYPE), intent.getIntExtra(EXTRA_FLAGS, 0))
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
        private const val EXTRA_ACTION = "notification_action"
        private const val EXTRA_DATA = "notification_data"
        private const val EXTRA_TYPE = "notification_type"
        private const val EXTRA_FLAGS = "notification_flags"
        /** Cờ được phép chuyển tiếp: đọc file qua FileProvider và mở ở task mới. */
        private const val ALLOWED_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK

        /** Gói các trường (chuỗi/số) của [target]; không bỏ cả Intent vào extras. */
        fun intent(context: Context, target: Intent): Intent =
            Intent(context, NotificationOpenActivity::class.java)
                .putExtra(EXTRA_ACTION, target.action)
                .putExtra(EXTRA_DATA, target.dataString)
                .putExtra(EXTRA_TYPE, target.type)
                .putExtra(EXTRA_FLAGS, target.flags)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /**
         * Authority app tự phát ra: FileProvider của app, MediaStore (`media`) và SAF (`*.documents`, ví dụ
         * `com.android.providers.downloads.documents`) cho thông báo "Đã tải xong" của BrowserDownloads.
         */
        private fun allowedAuthority(context: Context, authority: String?): Boolean =
            authority != null && (authority == "${context.packageName}.files" || authority == "media" ||
                authority.endsWith(".documents"))

        /** Dựng Intent MỚI từ các trường đã kiểm; null nếu không khớp thứ app tự tạo (VIEW + content:// của FileProvider của app, MediaStore hoặc SAF). */
        internal fun buildTarget(context: Context, action: String?, data: String?, type: String?, flags: Int): Intent? {
            if (action != Intent.ACTION_VIEW || data.isNullOrEmpty()) return null
            val uri = Uri.parse(data)
            if (uri.scheme != "content" || !allowedAuthority(context, uri.authority)) return null
            return Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, type)
                .addFlags((flags and ALLOWED_FLAGS) or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
