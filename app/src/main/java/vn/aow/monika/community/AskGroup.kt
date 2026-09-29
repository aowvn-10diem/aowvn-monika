package vn.aow.monika.community

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.core.content.FileProvider
import vn.aow.monika.AppGraph
import vn.aow.monika.browser.InAppBrowserActivity
import java.io.File

/**
 * "Hỏi nhóm": đang chơi bị kẹt → chụp màn hình game, chép sẵn lời nhắn, mở Group Facebook AowVN ngay trong app;
 * khi tạo bài và bấm thêm ảnh, trình duyệt tự đính kèm ảnh chụp. Game đã được lưu trước đó (giả lập tự lưu).
 */
object AskGroup {
    fun ask(activity: Activity, shot: Bitmap?, gameName: String, system: String) {
        val file = shot?.let { save(activity, it) }
        val note = "Mọi người ơi, mình đang chơi $gameName ($system) trên Aow Monika và bị kẹt ở đoạn này, giúp mình với 🙏"
        runCatching {
            activity.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Hỏi nhóm", note))
        }
        val group = AppGraph.config.current.community.facebookGroup
        if (group.isNotBlank()) {
            InAppBrowserActivity.start(activity, group, file)
            Toast.makeText(activity, "Đã chép lời nhắn · game đã lưu", Toast.LENGTH_SHORT).show()
        } else {
            // Chưa cấu hình group → chia sẻ ảnh qua app khác.
            val send = Intent(Intent.ACTION_SEND).setType(if (file != null) "image/png" else "text/plain").putExtra(Intent.EXTRA_TEXT, note)
            file?.let { send.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(activity, activity.packageName + ".files", it)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            activity.startActivity(Intent.createChooser(send, "Hỏi nhóm"))
        }
    }

    private fun save(activity: Activity, bmp: Bitmap): File? = runCatching {
        val dir = File(activity.filesDir, "shots").apply { mkdirs() }
        // Chỉ giữ vài ảnh gần nhất.
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(5)?.forEach { it.delete() }
        File(dir, "monika-${System.currentTimeMillis()}.png").also { f -> f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }.getOrNull()
}
