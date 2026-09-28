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
 * 2. Tìm bài trên aow.vn theo tên → lấy ảnh bìa + tên đẹp + link bài (bấm tên game mở bài).
 * Kết quả lưu vào `filesDir/game-info.json` → mỗi game chỉ tra 1 lần (không có kết quả thì 7 ngày thử lại).
 */
class GameInfoResolver(private val context: Context, private val feed: FeedRepository) {

    @Serializable
    data class Info(
        val title: String? = null,
        val cover: String? = null,
        val postId: String? = null,
        val postUrl: String? = null,
        /** Đã tra aow.vn xong (có hoặc không có kết quả) lúc nào. 0 = chưa tra được (lỗi mạng). */
        val checkedAt: Long = 0,
    )

    private val file = File(context.filesDir, "game-info.json")
    private val iconDir = File(context.filesDir, "game-icons")
    private val json = Json { ignoreUnknownKeys = true }
    private val ser = MapSerializer(String.serializer(), Info.serializer())
    private val map: MutableMap<String, Info> by lazy {
        runCatching { json.decodeFromString(ser, file.readText()) }.getOrDefault(emptyMap()).toMutableMap()
    }
    private val lock = Mutex()

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
        return info.cover == null && now - info.checkedAt > RETRY_MS
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
        val local = old?.takeIf { it.title != null || it.cover != null } ?: readLocal(entry)
        // Tên để tìm: tên file/thư mục đã làm gọn trước (thường là tên Việt hóa), rồi tên trong ROM.
        val fromName = cleanName(if (g.external) entry.nameWithoutExtension else g.meta?.title?.ifBlank { null } ?: g.dir.name)
        val candidates = listOfNotNull(fromName, local?.title?.let(::cleanName))
            // Bỏ tên vô nghĩa ("0001", "rom") — tên file ROM tải về hay chỉ là số.
            .filter { q -> tokens(q).any { t -> t.length >= 2 && t.any(Char::isLetter) } }
            .distinctBy { tokens(it) }
        var networkOk = false
        for (q in candidates) {
            val posts = runCatching { feed.fetch(query = q, max = 8) }.onSuccess { networkOk = true }.getOrNull() ?: continue
            val best = posts.map { it to score(q, GameMeta.cleanTitle(it.title)) }.filter { it.second >= MIN_SCORE }.maxByOrNull { it.second }?.first
            if (best != null) {
                return Info(
                    title = GameMeta.cleanTitle(best.title),
                    cover = GameMeta.largeCover(best.thumbnail) ?: local?.cover,
                    postId = best.id, postUrl = best.url, checkedAt = System.currentTimeMillis(),
                )
            }
        }
        val title = local?.title?.takeIf { g.external || g.meta == null }?.let(::pretty)
        return Info(title = title ?: old?.title, cover = local?.cover, checkedAt = if (networkOk) System.currentTimeMillis() else 0)
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
        private const val RETRY_MS = 7L * 24 * 3600 * 1000
        private const val MAX_PER_RUN = 40
        private const val MIN_SCORE = 0.6

        /** Ảnh là icon nhỏ đọc từ file game (vẽ giữa ô, giữ nét pixel) chứ không phải ảnh bìa. */
        fun isIcon(cover: String?) = cover?.startsWith("file://") == true

        private val STOP = setOf("game", "viet", "hoa", "the", "of", "a", "ban", "full", "rom", "nds", "gba", "gbc", "gb", "psp", "ps1", "jar", "java", "usa", "europe", "japan", "eur", "jpn", "en", "vn", "v", "version")

        /** "1234 - Pokemon_Fire_Red (USA) [v1.1]" → "Pokemon Fire Red". */
        fun cleanName(s: String): String = GameMeta.cleanTitle(
            s.replace('_', ' ').replace('.', ' ')
                .replace(Regex("""[\[(][^\])]*[\])]"""), " ")
                .replace(Regex("""^\s*\d{3,5}\s*[-–]\s*"""), "")
                .replace(Regex("""(?i)\bv\d+(\s\d+)*\b"""), " ")
        ).replace(Regex("""\s+"""), " ").trim()

        /** Chữ HOA toàn bộ → Viết Hoa Đầu Từ. */
        fun pretty(s: String): String = if (s.any { it.isLowerCase() }) s
        else s.lowercase().split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

        fun tokens(s: String): Set<String> =
            Normalizer.normalize(s.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD)
                .replace(Regex("""\p{M}+"""), "").lowercase()
                .split(Regex("""[^a-z0-9]+""")).filter { it.isNotEmpty() && it !in STOP }.toSet()

        /** Tỉ lệ từ khóa của tên game có trong tên bài (0..1). Tên 1 từ phải dài ≥ 4 ký tự để tránh khớp bừa. */
        fun score(query: String, title: String): Double {
            val q = tokens(query)
            if (q.isEmpty() || (q.size == 1 && q.first().length < 4)) return 0.0
            val t = tokens(title)
            val hit = q.count { it in t }
            if (hit == 0) return 0.0
            // Phạt nhẹ bài có quá nhiều từ không liên quan (vd. bài tổng hợp).
            val extra = (t.size - hit).coerceAtLeast(0)
            return hit.toDouble() / q.size - extra * 0.02
        }
    }
}
