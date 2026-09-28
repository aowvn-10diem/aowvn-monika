package vn.aow.monika.community

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** 1 liên kết cộng đồng: group/trang Facebook hoặc máy chủ Discord. */
data class CommunityLink(val kind: Kind, val url: String) {
    enum class Kind(val label: String) { FACEBOOK("Facebook"), DISCORD("Discord") }
}

object Community {
    private val FACEBOOK = Regex("""https?://(?:www\.|m\.|web\.)?(?:facebook\.com|fb\.com)/[^\s"'<>&]+""", RegexOption.IGNORE_CASE)
    private val DISCORD = Regex("""https?://(?:www\.)?(?:discord\.gg|discord\.com/invite|discordapp\.com/invite)/[A-Za-z0-9-]+""", RegexOption.IGNORE_CASE)

    /** Link FB / Discord trong bài viết (thường là của nhóm dịch). Bỏ trùng, bỏ link chia sẻ/plugin của FB. */
    fun extract(html: String): List<CommunityLink> {
        val text = html.replace("&amp;", "&")
        val fb = FACEBOOK.findAll(text).map { it.value.trimEnd('/', '.', ',') }
            .filterNot { u -> listOf("/sharer", "/plugins", "/dialog", "/tr?", "/share.php").any { it in u.lowercase() } }
        val dc = DISCORD.findAll(text).map { it.value }
        return (dc.map { CommunityLink(CommunityLink.Kind.DISCORD, it) } + fb.map { CommunityLink(CommunityLink.Kind.FACEBOOK, it) })
            .distinctBy { it.url.lowercase().removeSuffix("/") }.toList()
    }

    /** Tên ngắn để hiện trên nút: facebook.com/HisekuTeam → HisekuTeam, discord.gg/abc → abc. */
    fun shortName(link: CommunityLink): String {
        val u = Uri.parse(link.url)
        u.getQueryParameter("id")?.let { return "Trang ${link.kind.label}" }
        return u.pathSegments.lastOrNull { it.isNotBlank() && it != "groups" && it != "invite" } ?: link.kind.label
    }

    /**
     * Mở bằng app Facebook / Discord nếu máy đã cài (Android tự chuyển link sang app), không có thì mở trình duyệt.
     * Không nhúng đăng nhập FB/Discord trong app: 2 nền tảng này chặn đăng nhập trong WebView, và giữ tài khoản user an toàn.
     */
    fun open(context: Context, url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Không có app nào mở được link này", Toast.LENGTH_SHORT).show()
        }
    }
}
