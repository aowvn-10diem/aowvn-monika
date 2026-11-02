package vn.aow.monika.community

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.widget.Toast
import vn.aow.monika.AppGraph
import vn.aow.monika.play.PlayLinks
import java.io.File

/**
 * "Hỏi nhóm": chép lời nhắn, chuyển ảnh qua trình chia sẻ Android để người chơi chọn ứng dụng/nhóm nhận.
 * Không có ảnh thì mở liên kết cộng đồng. Ứng dụng nhận quyết định cách hiển thị ảnh và lời nhắn.
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
            PlayLinks.open(activity, group, file, note)
            Toast.makeText(activity, if (file != null) "Chọn ứng dụng và nhóm để gửi ảnh · lời nhắn đã chép" else "Đã chép lời nhắn · dán vào nhóm để hỏi", Toast.LENGTH_SHORT).show()
        } else {
            // Chưa cấu hình group → chia sẻ ảnh qua app khác.
            PlayLinks.share(activity, file, note)
        }
    }

    private fun save(activity: Activity, bmp: Bitmap): File? = runCatching {
        val dir = File(activity.filesDir, "shots").apply { mkdirs() }
        // Chỉ giữ vài ảnh gần nhất.
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(5)?.forEach { it.delete() }
        File(dir, "monika-${System.currentTimeMillis()}.png").also { f -> f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }.getOrNull()
}
