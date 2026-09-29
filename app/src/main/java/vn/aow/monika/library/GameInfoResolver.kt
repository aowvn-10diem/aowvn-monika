package vn.aow.monika.library

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import vn.aow.monika.feed.FeedRepository
import java.io.File
import java.io.RandomAccessFile
import java.text.Normalizer
import java.util.zip.ZipFile

/**
 * Tự tìm tên + ảnh cho game chưa có thông tin bài viết (game quét trong máy, thêm tay):
 * 1. Đọc ngay trong file game (không cần mạng): NDS → tên + icon 32×32 trong banner; GBA → tên trong header;
 *    JAR → MIDlet-Name + icon trong MANIFEST.
 * 2. Khớp với DANH MỤC toàn bộ bài aow.vn (tải 1 lần/ngày, lưu máy): so tên gần đúng (sai 1-2 chữ vẫn nhận),
 *    ưu tiên bài đúng nhãn hệ máy (NDS/GBA/Java…), phạt bài của hệ khác → lấy ảnh bìa + tên đẹp + link bài.
 * Kết quả lưu vào `filesDir/game-info.json` → mỗi game chỉ tra 1 lần (không khớp thì 3 ngày thử lại,
 * đổi thuật toán [VERSION] thì tra lại hết).
 */
class GameInfoResolver(
    private val context: Context,
    private val feed: FeedRepository,
    private val config: () -> vn.aow.monika.config.MonikaConfig = { vn.aow.monika.AppGraph.config.current },
    /** Nguồn ảnh bìa thứ 2 (kho libretro-thumbnails). Null = tắt. */
    private val boxArts: BoxArts? = null,
) {

    @Serializable
    data class Info(
        val title: String? = null,
        val cover: String? = null,
        val postId: String? = null,
        val postUrl: String? = null,
        /** Đã tra aow.vn xong (có hoặc không có kết quả) lúc nào. 0 = chưa tra được (lỗi mạng). */
        val checkedAt: Long = 0,
        /** Phiên bản thuật toán khớp — tăng [VERSION] là mọi game được tra lại. */
        val v: Int = 0,
        /** Tên / icon đọc trong file game (giữ riêng để lần tra sau không phải đọc lại). */
        val localTitle: String? = null,
        val localIcon: String? = null,
    )

    private val file = File(context.filesDir, "game-info.json")
    private val iconDir = File(context.filesDir, "game-icons")
    private val json = Json { ignoreUnknownKeys = true }
    private val ser = MapSerializer(String.serializer(), Info.serializer())
    private val map: MutableMap<String, Info> by lazy {
        runCatching { json.decodeFromString(ser, file.readText()) }.getOrDefault(emptyMap()).toMutableMap()
    }
    private val lock = Mutex()
    private val indexFile = File(context.filesDir, "post-index.json")
    private val postSer = kotlinx.serialization.builtins.ListSerializer(vn.aow.monika.feed.Post.serializer())
    @Volatile private var index: List<vn.aow.monika.feed.Post>? = null

    /** Danh mục bài: bản trên máy nếu còn mới (< 1 ngày), không thì tải lại; mất mạng → dùng bản cũ. */
    suspend fun postIndex(): List<vn.aow.monika.feed.Post>? {
        index?.let { return it }
        val cached = runCatching { json.decodeFromString(postSer, indexFile.readText()) }.getOrNull()
        if (cached != null && System.currentTimeMillis() - indexFile.lastModified() < INDEX_TTL_MS) return cached.also { index = it }
        val fresh = runCatching { feed.fetchIndex() }.getOrNull()
        if (!fresh.isNullOrEmpty()) runCatching { indexFile.writeText(json.encodeToString(postSer, fresh)) }
        return (fresh?.takeIf { it.isNotEmpty() } ?: cached ?: fresh)?.also { index = it }
    }

    /** Tăng mỗi khi có thêm thông tin → Thư viện vẽ lại. */
    val updated = MutableStateFlow(0)

    /** Gắn tên/ảnh đã tìm được vào game. Game tải từ bài viết (đã có ảnh bìa) giữ nguyên. */
    fun apply(g: Game): Game {
        val base = g.meta
        if (base?.cover != null && base.title.isNotBlank()) return g
        val info = synchronized(map) { map[g.key] } ?: return g
        val title = base?.title?.ifBlank { null } ?: info.title
        val meta = (base ?: GameMeta()).copy(
            title = title.orEmpty(),
            cover = base?.cover ?: info.cover,
            postId = base?.postId ?: info.postId,
            postUrl = base?.postUrl ?: info.postUrl,
        )
        return g.copy(name = title ?: g.name, meta = meta)
    }

    private fun needs(g: Game, now: Long): Boolean {
        if (g.system == null || g.locked || g.evicted || g.entry == null) return false
        if (g.meta?.cover != null) return false
        val info = synchronized(map) { map[g.key] } ?: return true
        if (info.v < VERSION) return true
        return info.postId == null && now - info.checkedAt > RETRY_MS
    }

    /** Tra thông tin cho các game còn thiếu (chạy nền, tuần tự, bỏ qua nếu đang chạy). */
    suspend fun resolveAll(games: List<Game>) {
        if (!lock.tryLock()) return
        try {
            val now = System.currentTimeMillis()
            var changed = 0
            for (g in games.filter { needs(it, now) }.take(MAX_PER_RUN)) {
                val old = synchronized(map) { map[g.key] }
                val info = runCatching { resolve(g, old) }.getOrNull() ?: continue
                synchronized(map) { map[g.key] = info }
                if (info != old) changed++
                if (changed > 0 && changed % 4 == 0) { save(); updated.value++ }
            }
            if (changed > 0) { save(); updated.value++ }
        } finally {
            lock.unlock()
        }
    }

    private suspend fun resolve(g: Game, old: Info?): Info {
        val entry = g.entry!!
        val local = if (old != null && (old.localTitle != null || old.localIcon != null)) Info(title = old.localTitle, cover = old.localIcon) else readLocal(entry)
        // Tên để khớp: tên file/thư mục (thường là tên Việt hóa) + tên trong ROM (NDS/GBA/JAR).
        val fromName = cleanName(if (g.external) entry.nameWithoutExtension else g.meta?.title?.ifBlank { null } ?: g.dir.name)
        val candidates = listOfNotNull(fromName, local?.title?.let(::cleanName))
            // Bỏ tên vô nghĩa ("0001", "rom") — tên file ROM tải về hay chỉ là số.
            .filter { q -> tokens(q).any { t -> t.length >= 2 && t.any(Char::isLetter) } }
            .distinctBy { tokens(it) }
        val posts = postIndex()
        val own = g.system?.labels.orEmpty()
        // Nhãn của các hệ máy khác (bỏ nhãn chung như "Game Android Việt Hóa" của hệ APK — bài nào cũng có).
        val others = config().systems.filter { it.runner != "apk" }.flatMap { it.labels }.toSet() - own.toSet()
        val best = posts?.let { match(candidates, own, it, others) }
        val now = System.currentTimeMillis()
        if (best != null) {
            return Info(
                title = GameMeta.cleanTitle(best.title),
                cover = GameMeta.largeCover(best.thumbnail) ?: local?.cover,
                postId = best.id, postUrl = best.url, checkedAt = now, v = VERSION,
                localTitle = local?.title, localIcon = local?.cover,
            )
        }
        // Không có bài trên aow.vn → ảnh bìa gốc của game từ kho libretro (như Daijishō).
        val sys = g.system
        if (boxArts != null && sys != null) {
            for (repo in boxArts.reposFor(sys.id, sys.thumbnails)) {
                val names = boxArts.names(repo) ?: continue
                val hit = BoxArts.best(candidates, names) ?: continue
                val junkName = tokens(fromName).none { t -> t.length >= 2 && t.any(Char::isLetter) }
                return Info(
                    title = if (g.meta?.title.isNullOrBlank() && (junkName || local?.title != null)) cleanName(hit) else null,
                    cover = boxArts.url(repo, hit), checkedAt = now, v = VERSION,
                    localTitle = local?.title, localIcon = local?.cover,
                )
            }
        }
        val title = local?.title?.takeIf { g.external || g.meta == null }?.let(::pretty)
        return Info(
            title = title, cover = local?.cover, checkedAt = if (posts != null) now else 0, v = VERSION,
            localTitle = local?.title, localIcon = local?.cover,
        )
    }

    private fun save() = runCatching { synchronized(map) { file.writeText(json.encodeToString(ser, map.toMap())) } }

    // ---------- Đọc thông tin nằm sẵn trong file game ----------

    private fun readLocal(f: File): Info? = runCatching {
        when (f.extension.lowercase()) {
            "nds" -> readNds(f)
            "gba" -> readGba(f)
            "jar" -> readJar(f)
            else -> null
        }
    }.getOrNull()

    /** Banner NDS: icon 4bpp 32×32 (4×4 ô 8×8) + bảng 16 màu BGR555; tên tiếng Anh UTF-16 "Tên\nPhụ đề\nHãng". */
    private fun readNds(f: File): Info? = RandomAccessFile(f, "r").use { raf ->
        val header = ByteArray(0x6C).also { raf.readFully(it) }
        val off = le32(header, 0x68).toLong()
        if (off <= 0 || off + 0x440 > raf.length()) return@use null
        val banner = ByteArray(0x440).also { raf.seek(off); raf.readFully(it) }
        val lines = String(banner, 0x340, 0x100, Charsets.UTF_16LE).trim('\u0000', ' ').lines().map { it.trim() }.filter { it.isNotEmpty() }
        val title = (if (lines.size >= 2) lines.dropLast(1) else lines).joinToString(" ").takeIf { it.isNotBlank() }
        val palette = IntArray(16) { i ->
            val c = (banner[0x220 + i * 2].toInt() and 0xff) or ((banner[0x221 + i * 2].toInt() and 0xff) shl 8)
            if (i == 0) 0 else (0xff shl 24) or (((c and 0x1f) * 255 / 31) shl 16) or ((((c shr 5) and 0x1f) * 255 / 31) shl 8) or (((c shr 10) and 0x1f) * 255 / 31)
        }
        val px = IntArray(32 * 32)
        for (t in 0 until 16) for (i in 0 until 64) {
            val b = banner[0x20 + t * 32 + i / 2].toInt() and 0xff
            val idx = if (i % 2 == 0) b and 0x0f else b shr 4
            px[((t / 4) * 8 + i / 8) * 32 + (t % 4) * 8 + i % 8] = palette[idx]
        }
        val cover = if (px.any { it != 0 }) saveIcon(f, Bitmap.createBitmap(px, 32, 32, Bitmap.Config.ARGB_8888)) else null
        Info(title = title, cover = cover)
    }

    /** Header GBA: tên 12 ký tự ASCII ở 0xA0. */
    private fun readGba(f: File): Info? = RandomAccessFile(f, "r").use { raf ->
        if (raf.length() < 0xC0) return@use null
        val b = ByteArray(12).also { raf.seek(0xA0); raf.readFully(it) }
        val t = String(b, Charsets.US_ASCII).trim('\u0000', ' ')
        Info(title = t.takeIf { s -> s.length >= 3 && s.all { it in ' '..'~' } })
    }

    /** MANIFEST của game Java: MIDlet-Name + MIDlet-Icon (hoặc icon trong MIDlet-1: "Tên, /icon.png, Lớp"). */
    private fun readJar(f: File): Info? = ZipFile(f).use { zip ->
        val mf = zip.getEntry("META-INF/MANIFEST.MF") ?: return@use null
        val attrs = zip.getInputStream(mf).bufferedReader(Charsets.UTF_8).readText()
            .replace("\r\n", "\n").replace("\n ", "").lines()
            .mapNotNull { l -> l.indexOf(':').takeIf { it > 0 }?.let { l.substring(0, it).trim() to l.substring(it + 1).trim() } }.toMap()
        val name = attrs["MIDlet-Name"] ?: attrs["MIDlet-1"]?.substringBefore(',')?.trim()
        val iconPath = (attrs["MIDlet-Icon"]?.takeIf { it.isNotBlank() } ?: attrs["MIDlet-1"]?.split(',')?.getOrNull(1)?.trim())?.trimStart('/')
        val cover = iconPath?.takeIf { it.isNotBlank() }?.let { zip.getEntry(it) }?.let { e ->
            val bytes = zip.getInputStream(e).use { it.readBytes() }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { saveIcon(f, it) }
        }
        Info(title = name?.takeIf { it.isNotBlank() }, cover = cover)
    }

    private fun saveIcon(f: File, bmp: Bitmap): String? = runCatching {
        iconDir.mkdirs()
        val out = File(iconDir, f.path.hashCode().toUInt().toString(16) + ".png")
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        "file://" + out.path
    }.getOrNull()

    private fun le32(b: ByteArray, o: Int) =
        (b[o].toInt() and 0xff) or ((b[o + 1].toInt() and 0xff) shl 8) or ((b[o + 2].toInt() and 0xff) shl 16) or ((b[o + 3].toInt() and 0xff) shl 24)

    companion object {
        private const val RETRY_MS = 3L * 24 * 3600 * 1000
        private const val INDEX_TTL_MS = 24L * 3600 * 1000
        /** Tăng khi đổi cách khớp → tra lại mọi game. */
        const val VERSION = 3
        private const val MAX_PER_RUN = 40
        private const val MIN_SCORE = 0.6

        /** Ảnh là icon nhỏ đọc từ file game (vẽ giữa ô, giữ nét pixel) chứ không phải ảnh bìa. */
        fun isIcon(cover: String?) = cover?.startsWith("file://") == true

        private val STOP = setOf(
            "game", "viet", "hoa", "the", "of", "a", "an", "and", "ban", "full", "rom", "nds", "gba", "gbc", "gb", "psp", "ps1", "jar", "java",
            "usa", "europe", "japan", "eur", "jpn", "en", "vn", "v", "version", "fan", "hot", "moi", "sieu", "100", "pc", "android", "ios",
        )
        private val ROMAN = mapOf("ii" to "2", "iii" to "3", "iv" to "4")

        /** "1234 - Pokemon_Fire_Red (USA) [v1.1]" → "Pokemon Fire Red". */
        fun cleanName(s: String): String = GameMeta.cleanTitle(
            // Tên file dạng "Pokemon - HeartGold Version": " - " là 1 phần tên, không phải phần mô tả như tiêu đề bài.
            s.replace('_', ' ').replace('.', ' ')
                .replace(Regex("""^\s*\d{3,5}\s*[-–]\s*"""), "")
                .replace(Regex("""\s+[-–]\s+(?=\p{L})"""), " ")
                .replace(Regex("""[\[(][^\])]*[\])]"""), " ")
                .replace(Regex("""(?i)\bv\d+(\s\d+)*\b"""), " ")
        ).replace(Regex("""\s+"""), " ").trim()

        /** Chữ HOA toàn bộ → Viết Hoa Đầu Từ. */
        fun pretty(s: String): String = if (s.any { it.isLowerCase() }) s
        else s.lowercase().split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

        fun tokens(s: String): Set<String> = tokenList(s).toSet()

        private fun tokenList(s: String): List<String> =
            Normalizer.normalize(s.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD)
                .replace(Regex("""\p{M}+"""), "").lowercase()
                .split(Regex("""[^a-z0-9]+""")).map { ROMAN[it] ?: it }.filter { it.isNotEmpty() && it !in STOP }

        /** 2 từ coi là giống nhau: trùng hẳn, hoặc từ dài sai 1 chữ (≥5 ký tự) / 2 chữ (≥8) — "Plantium" ≈ "Platinum". Số phải trùng hẳn. */
        private fun same(a: String, b: String): Boolean {
            if (a == b) return true
            if (a.any(Char::isDigit) || b.any(Char::isDigit)) return false
            val n = minOf(a.length, b.length)
            val limit = when { n >= 8 -> 2; n >= 5 -> 1; else -> return false }
            if (kotlin.math.abs(a.length - b.length) > limit) return false
            return levenshtein(a, b) <= limit
        }

        private fun levenshtein(a: String, b: String): Int {
            var prev = IntArray(b.length + 1) { it }
            for (i in 1..a.length) {
                val cur = IntArray(b.length + 1).also { it[0] = i }
                for (j in 1..b.length) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = cur
            }
            return prev[b.length]
        }

        /**
         * Điểm khớp tên game ↔ tên bài (0..1): chủ yếu là tỉ lệ từ khóa của tên game có trong tên bài (phải ≥ 60%),
         * cộng độ trùng 2 chiều để bài đúng game xếp trên bài tổng hợp / bản mod.
         */
        fun score(query: String, title: String): Double {
            val q = tokens(query)
            if (q.isEmpty() || (q.size == 1 && q.first().length < 4)) return 0.0
            val t = tokens(title)
            if (t.isEmpty()) return 0.0
            // "Mega Man" ≈ "Megaman": ghép 2 từ liền nhau của mỗi bên để so.
            val tJoined = t + title.let(::tokenList).zipWithNext { x, y -> x + y }
            val qList = tokenList(query)
            val joinedHits = qList.zipWithNext().filter { (x, y) -> (x + y) in t }.flatMap { listOf(it.first, it.second) }.toSet()
            val hit = q.count { a -> a in joinedHits || tJoined.any { b -> same(a, b) } }
            val recall = hit.toDouble() / q.size
            // Tên file dài hơn tên bài ("Ghost Trick Phantom Detective" ↔ "Ghost Trick"): đủ mọi từ của tên bài (≥ 2 từ) cũng tính là khớp.
            val hitT = t.count { b -> q.any { a -> same(a, b) } }
            val cover = if (t.size >= 2 && hitT == t.size) 1.0 else 0.0
            if (hit == 0 || maxOf(recall, cover) < 0.6) return 0.0
            // Tên 1 từ ("Pokemon") chỉ khớp bài tên cũng ngắn — tránh gắn bừa ảnh 1 bài Pokemon bất kỳ.
            if (q.size == 1 && t.size > 2) return 0.0
            val dice = 2.0 * hit / (q.size + t.size)
            return maxOf(recall, cover) * 0.7 + dice * 0.3
        }

        /**
         * Chọn bài khớp nhất cho 1 game. Bài có nhãn đúng hệ máy +0.15; bài mang nhãn của hệ máy KHÁC ([otherLabels]) −0.5.
         * Ngưỡng 0.62 → thà để trống còn hơn gắn nhầm ảnh.
         */
        fun match(names: List<String>, systemLabels: List<String>, posts: List<vn.aow.monika.feed.Post>, otherLabels: Set<String> = emptySet()): vn.aow.monika.feed.Post? {
            if (names.isEmpty()) return null
            return posts.map { p ->
                val title = GameMeta.cleanTitle(p.title)
                var sc = names.maxOf { score(it, title) }
                if (sc > 0) when {
                    p.labels.any { it in systemLabels } -> sc += 0.15
                    p.labels.any { it in otherLabels } -> sc -= 0.5
                }
                p to sc
            }.filter { it.second >= 0.62 }.maxByOrNull { it.second }?.first
        }
    }
}
