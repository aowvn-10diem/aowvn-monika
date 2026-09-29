package vn.aow.monika.cheats

import android.content.Context
import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.Request
import vn.aow.monika.library.BoxArts
import vn.aow.monika.library.GameInfoResolver
import java.io.File

/**
 * Kho cheat tự nhận diện: danh mục tên tệp của libretro-database (đóng sẵn trong APK, khớp tên game OFFLINE như ảnh bìa),
 * chọn bộ mã đúng game rồi tải tệp .cht về (raw GitHub, dự phòng jsDelivr) và lưu cục bộ.
 */
class CheatDb(private val context: Context, private val http: OkHttpClient) {

    /** 1 bộ mã khớp game: thư mục hệ máy + tên tệp (mỗi game có thể có vài bộ: GameShark, Code Breaker, Action Replay…). */
    data class Candidate(val repo: String, val file: String) {
        val label: String get() = Regex("""\(([^()]*)\)[^()]*$""").find(file)?.groupValues?.get(1)?.takeIf { it.length < 24 } ?: "Bộ mã"
    }

    private val index: Map<String, List<String>> by lazy {
        val out = HashMap<String, MutableList<String>>()
        runCatching {
            context.assets.open("cheats/index.txt").bufferedReader().useLines { lines ->
                for (l in lines) {
                    val t = l.indexOf('\t')
                    if (t > 0) out.getOrPut(l.substring(0, t)) { mutableListOf() }.add(l.substring(t + 1))
                }
            }
        }
        out
    }

    fun supports(systemId: String) = repos(systemId).isNotEmpty()

    fun repos(systemId: String): List<String> = REPOS[systemId].orEmpty()

    /** Các bộ mã khớp với tên game (tốt nhất trước). Rỗng = không nhận diện được. */
    fun match(systemId: String, names: List<String>): List<Candidate> {
        for (repo in repos(systemId)) {
            val files = index[repo] ?: continue
            val best = BoxArts.best(names, files) ?: continue
            val key = GameInfoResolver.tokens(GameInfoResolver.cleanName(best))
            // Cùng game (bỏ vùng/loại mã trong ngoặc) → gom làm các "bộ mã" để người chơi đổi.
            val same = files.filter { GameInfoResolver.tokens(GameInfoResolver.cleanName(it)) == key }
            val ordered = (listOf(best) + same.filter { it != best }.sortedBy { rank(it) }).distinct()
            return ordered.map { Candidate(repo, it) }
        }
        return emptyList()
    }

    private fun rank(name: String): Int {
        val n = name.lowercase()
        return when {
            "(usa" in n -> 0
            "europe" in n || "world" in n -> 1
            "japan" in n -> 3
            else -> 2
        }
    }

    /** Tải nội dung .cht (có bộ nhớ đệm trong máy). Null = lỗi mạng. */
    fun fetch(c: Candidate): List<CheatEntry>? {
        val cache = File(context.filesDir, "cheats-db/" + (c.repo + "_" + c.file).replace(Regex("[^A-Za-z0-9._-]+"), "_") + ".cht")
        val text = cache.takeIf { it.isFile }?.readText() ?: run {
            val path = "cht/" + Uri.encode(c.repo) + "/" + Uri.encode(c.file) + ".cht"
            val body = listOf(RAW + path, JSDELIVR + path).firstNotNullOfOrNull { url ->
                runCatching {
                    http.newCall(Request.Builder().url(url).build()).execute().use { r -> if (r.isSuccessful) r.body!!.string() else null }
                }.getOrNull()
            } ?: return null
            runCatching { cache.parentFile?.mkdirs(); cache.writeText(body) }
            body
        }
        return ChtFormat.parse(text, source = "auto")
    }

    companion object {
        private const val RAW = "https://raw.githubusercontent.com/libretro/libretro-database/master/"
        private const val JSDELIVR = "https://cdn.jsdelivr.net/gh/libretro/libretro-database@master/"

        /** Hệ máy Monika → thư mục cheat của libretro-database (lõi phải hỗ trợ retro_cheat_set). */
        val REPOS: Map<String, List<String>> = mapOf(
            "gba" to listOf("Nintendo - Game Boy Advance"),
            "gbc" to listOf("Nintendo - Game Boy Color", "Nintendo - Game Boy"),
            "nds" to listOf("Nintendo - Nintendo DS"),
            "snes" to listOf("Nintendo - Super Nintendo Entertainment System"),
            "nes" to listOf("Nintendo - Nintendo Entertainment System"),
            "n64" to listOf("Nintendo - Nintendo 64"),
            "genesis" to listOf("Sega - Mega Drive - Genesis"),
            "sms" to listOf("Sega - Master System - Mark III"),
            "gg" to listOf("Sega - Game Gear"),
            "dc" to listOf("Sega - Dreamcast"),
            "pce" to listOf("NEC - PC Engine - TurboGrafx 16"),
            "ps1" to listOf("Sony - PlayStation"),
            "psp" to listOf("Sony - PlayStation Portable"),
            "lynx" to listOf("Atari - Lynx"),
            "a2600" to listOf("Atari - 2600"),
            "a7800" to listOf("Atari - 7800"),
            "ws" to listOf("Bandai - WonderSwan", "Bandai - WonderSwan Color"),
            "ngp" to listOf("SNK - Neo Geo Pocket", "SNK - Neo Geo Pocket Color"),
        )
    }
}
