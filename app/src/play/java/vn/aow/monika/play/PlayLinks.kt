package vn.aow.monika.play
import android.content.Context
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
/** Chỉ mở liên kết trợ giúp/cộng đồng; không mở trang tải hay trang trả tiền ngoài cửa hàng. */
object PlayLinks {
    fun open(context: Context, url: String, attachment: File? = null, message: String? = null) {
        val uri = Uri.parse(url)
        val host = uri.host?.lowercase().orEmpty()
        if (uri.scheme != "https" || !listOf("facebook.com", "fb.com", "discord.gg", "discord.com", "retroachievements.org")
            .any { host == it || host.endsWith(".$it") }) return
        if (attachment != null) {
            share(context, attachment, listOfNotNull(message, url).joinToString("\n\n"))
            return
        }
        vn.aow.monika.diag.Diagnostics.op(context, "mo-lien-ket") {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            android.widget.Toast.makeText(context, "Không mở được liên kết trợ giúp.", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    /** Người chơi chọn ứng dụng nhận; quyền đọc chỉ cấp cho URI ảnh qua FileProvider. */
    fun share(context: Context, attachment: File?, message: String) {
        vn.aow.monika.diag.Diagnostics.op(context, "chia-se-hoi-nhom") {
            val send = Intent(Intent.ACTION_SEND)
                .setType(if (attachment != null) "image/png" else "text/plain")
                .putExtra(Intent.EXTRA_TEXT, message)
            attachment?.let {
                val image = FileProvider.getUriForFile(context, context.packageName + ".files", it)
                send.putExtra(Intent.EXTRA_STREAM, image)
                send.clipData = ClipData.newUri(context.contentResolver, "Ảnh hỏi nhóm", image)
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(send, "Hỏi nhóm").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (send.clipData != null) {
                chooser.clipData = send.clipData
                chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(chooser)
        }.onFailure {
            android.widget.Toast.makeText(context, "Không chia sẻ được ảnh hoặc lời nhắn.", android.widget.Toast.LENGTH_LONG).show()
        }
    }
}
