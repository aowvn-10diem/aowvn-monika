package vn.aow.monika.community

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import vn.aow.monika.browser.InAppBrowserActivity

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
     * Gói app chính thức mở được link của từng loại (thử lần lượt).
     * Facebook cố ý KHÔNG mở bằng app FB: bấm Quay lại sẽ ở lại app FB, không về Aow Monika
     * → luôn dùng trình duyệt nhúng. Discord mở bằng app (cần app để chat/nhận file).
     */
    private val APPS = mapOf(
        CommunityLink.Kind.DISCORD to listOf("com.discord"),
    )

    fun kindOf(url: String): CommunityLink.Kind? = when {
        DISCORD.containsMatchIn(url) -> CommunityLink.Kind.DISCORD
        FACEBOOK.containsMatchIn(url) -> CommunityLink.Kind.FACEBOOK
        else -> null
    }

    /**
     * Discord: máy đã cài app → mở bằng app. Facebook: luôn trình duyệt nhúng.
     * Chưa cài → mở bằng trình duyệt nhúng trong app ([InAppBrowserActivity]), không phải rời app.
     */
    fun open(context: Context, url: String) {
        val kind = kindOf(url)
        val pkg = APPS[kind].orEmpty().firstOrNull { installed(context, it) }
        if (pkg != null) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
                // App có cài nhưng không nhận link này → dùng trình duyệt nhúng.
            }
        }
        try {
            InAppBrowserActivity.start(context, url)
        } catch (_: Exception) {
            Toast.makeText(context, "Không mở được link này", Toast.LENGTH_SHORT).show()
        }
    }

    private fun installed(context: Context, pkg: String) =
        runCatching { context.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)
}
