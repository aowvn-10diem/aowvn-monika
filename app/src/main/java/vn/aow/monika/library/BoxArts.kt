package vn.aow.monika.library

import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Ảnh bìa theo tên game — cách Daijishō làm: kho libretro-thumbnails đặt tên ảnh đúng theo tên chuẩn No-Intro
 * ("Pokemon - Emerald Version (USA, Europe).png"). Tải danh sách tên ảnh của từng hệ máy (1 lần / 30 ngày),
 * khớp gần đúng với tên file / tên trong ROM, rồi dùng thẳng link ảnh.
 * Dùng khi game KHÔNG có bài trên aow.vn (ưu tiên ảnh bìa Việt hóa của AowVN).
 */
class BoxArts(private val http: OkHttpClient, private val dir: File) {

    /** Tên kho libretro theo hệ máy của Monika (config `systems[].thumbnails` ghi đè được). */
    fun reposFor(systemId: String, override: List<String>): List<String> = override.ifEmpty { DEFAULT[systemId].orEmpty() }

    /** Danh sách tên ảnh bìa của 1 kho (đã bỏ đuôi .png). Null = lỗi mạng và chưa có bản lưu. */
    fun names(repo: String): List<String>? {
        val f = File(dir, repo.replace(Regex("[^A-Za-z0-9]+"), "_") + ".txt")
        val cached = f.takeIf { it.isFile }?.readLines()?.filter { it.isNotBlank() }
        if (cached != null && System.currentTimeMillis() - f.lastModified() < TTL_MS) return cached
        val fresh = runCatching {
            val req = Request.Builder().url(BASE + Uri.encode(repo) + "/Named_Boxarts/").build()
            http.newCall(req).execute().use { r ->
                check(r.isSuccessful)
                HREF.findAll(r.body!!.string()).map { Uri.decode(it.groupValues[1]) }.toList()
            }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
        if (fresh != null) runCatching { dir.mkdirs(); f.writeText(fresh.joinToString("\n")) }
        return fresh ?: cached
    }

    fun url(repo: String, name: String): String = BASE + Uri.encode(repo) + "/Named_Boxarts/" + Uri.encode(name) + ".png"

    companion object {
        private const val BASE = "https://thumbnails.libretro.com/"
        private const val TTL_MS = 30L * 24 * 3600 * 1000
        private val HREF = Regex("""href="([^"/?]+)\.png"""")
        private val DEFAULT = mapOf(
            "nds" to listOf("Nintendo - Nintendo DS"),
            "gba" to listOf("Nintendo - Game Boy Advance"),
            "gbc" to listOf("Nintendo - Game Boy Color", "Nintendo - Game Boy"),
            "ps1" to listOf("Sony - PlayStation"),
            "psp" to listOf("Sony - PlayStation Portable"),
        )

        /** Bản phát hành "chuẩn" được ưu tiên; bản hack / thử nghiệm / Nhật bị trừ điểm (vẫn dùng nếu chỉ có nó). */
        private fun regionBonus(name: String): Double {
            val n = name.lowercase()
            var b = 0.0
            if ("(usa" in n) b += 0.06 else if ("europe" in n || "(world" in n) b += 0.05
            if ("(japan)" in n || "(korea)" in n || "(china)" in n) b -= 0.03
            if ("hack" in n || "(beta" in n || "(proto" in n || "(demo" in n || "(alternate" in n || "(sample" in n || "(kiosk" in n) b -= 0.08
            return b
        }

        /** Tên ảnh khớp nhất với các tên của game (ngưỡng cao hơn aow.vn vì kho có hàng nghìn tên gần giống nhau). */
        fun best(names: List<String>, boxarts: List<String>): String? {
            if (names.isEmpty()) return null
            val queries = names.map(GameInfoResolver::cleanName).filter { GameInfoResolver.tokens(it).isNotEmpty() }
            if (queries.isEmpty()) return null
            var bestName: String? = null
            var bestScore = 0.0
            for (b in boxarts) {
                val clean = GameInfoResolver.cleanName(b)
                val sc = queries.maxOf { q ->
                    val s = GameInfoResolver.score(q, clean)
                    // Trùng khít cả bộ từ khóa → chắc chắn đúng game.
                    if (s > 0 && GameInfoResolver.tokens(q) == GameInfoResolver.tokens(clean)) s + 0.2 else s
                }
                if (sc <= 0) continue
                val total = sc + regionBonus(b)
                if (total > bestScore) { bestScore = total; bestName = b }
            }
            return bestName?.takeIf { bestScore >= 0.8 }
        }
    }
}
