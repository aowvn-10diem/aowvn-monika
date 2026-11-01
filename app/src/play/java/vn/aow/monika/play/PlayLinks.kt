package vn.aow.monika.play
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File
/** Chỉ mở liên kết trợ giúp/cộng đồng, không mở trang tải hoặc ủng hộ. */
object PlayLinks {
    fun open(context: Context, url: String, attachment: File? = null) {
        val uri = Uri.parse(url)
        val host = uri.host?.lowercase().orEmpty()
        if (uri.scheme != "https" || !listOf("facebook.com", "fb.com", "discord.gg", "discord.com", "retroachievements.org")
            .any { host == it || host.endsWith(".$it") }) return
        vn.aow.monika.diag.Diagnostics.op(context, "mo-lien-ket") {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            android.widget.Toast.makeText(context, "Không mở được liên kết trợ giúp.", android.widget.Toast.LENGTH_LONG).show()
        }
    }
}
