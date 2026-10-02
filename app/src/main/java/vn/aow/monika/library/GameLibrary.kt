package vn.aow.monika.library

import android.content.Context
import vn.aow.monika.config.ConfigRepository
import vn.aow.monika.config.MonikaConfig
import vn.aow.monika.config.SystemDef
import java.io.File

data class Game(
    val dir: File,
    val name: String,
    /** Null = chưa nhận diện được. */
    val system: SystemDef?,
    /** File/thư mục sẽ mở để chạy. */
    val entry: File?,
    /** Còn file nén chưa giải nén được (sai mật khẩu...). */
    val needsExtract: Boolean = false,
    /** Thông tin từ bài viết aow.vn (ảnh bìa, link bài). Null = game thêm tay từ máy. */
    val meta: GameMeta? = null,
    /** Đã bị dọn bộ đệm (chỉ còn thông tin bài viết) → hiện "Tải lại". */
    val evicted: Boolean = false,
    /**
     * Có file không đọc được: game tải từ LẦN CÀI TRƯỚC (gỡ app rồi cài lại → Android 11+ coi là app khác,
     * chặn đọc file cũ trong Download). Cần quyền "Truy cập mọi tệp" mới chơi được.
     */
    val locked: Boolean = false,
    /** Game tìm thấy khi quét máy (nằm ngoài thư mục Monika) — chơi thẳng từ chỗ cũ, "xóa" = ẩn khỏi Thư viện. */
    val external: Boolean = false,
) {
    /** Khóa riêng của game (lưu lần chơi, giữ lại…): game quét được dùng đường dẫn file vì nhiều game có thể chung 1 thư mục. */
    val key: String get() = if (external) entry?.path ?: dir.path else dir.path
}

class GameLibrary(
    private val context: Context,
    private val configRepo: ConfigRepository,
    /** Gắn tên/ảnh tự tìm được cho game chưa có thông tin bài viết. */
    private val info: GameInfoResolver? = null,
) {

    /** Danh sách lần quét gần nhất: mở tab Thư viện hiện ngay, không chớp màn "Chưa có game". */
    @Volatile var cached: List<Game>? = null
        private set

    val scanner = DeviceScanner(context)

    fun list(): List<Game> = (internalGames() + externalGames()).map { g -> info?.apply(g) ?: g }.also { cached = it; prefetchFor(it) }

    private val prefetched = HashSet<String>()

    /**
     * Có game thuộc hệ cần engine/app tải thêm (ONScripter, Kirikiri...) → xếp tải ngay theo luật mạng, để lúc bấm Chơi đã sẵn.
     * Mỗi gói chỉ xếp 1 lần mỗi lần mở app; mọi lỗi được nuốt.
     */
    private fun prefetchFor(games: List<Game>) = runCatching {
        val cfg = configRepo.current
        games.mapNotNull { it.system }.distinct().forEach { s ->
            val id = s.engine?.takeIf { cfg.modules.containsKey(it) }
            if (id != null && prefetched.add(id)) vn.aow.monika.pack.PackManager.prefetch(context, listOf(id))
        }
    }

    /** Game quét được trong máy, nhận diện hệ theo đuôi file. */
    private fun externalGames(): List<Game> = runCatching {
        val cfg = configRepo.current
        scanner.found().mapNotNull { f ->
            val system = cfg.systems.firstOrNull { s -> s.extensions.any { it.equals(f.extension, true) } && RomSniff.accepts(f) } ?: return@mapNotNull null
            Game(f.parentFile ?: f, f.nameWithoutExtension.replace('_', ' '), system, f, external = true)
        }
    }.getOrDefault(emptyList())

    private fun internalGames(): List<Game> =
        GameStorage.games(context).listFiles().orEmpty()
            .filter { it.isDirectory }
            .sortedByDescending { it.lastModified() }
            // 1 thư mục lỗi không được làm hỏng cả thư viện (và không bao giờ làm app crash).
            .mapNotNull { dir -> runCatching { GameDetector.detect(dir, configRepo.current) }.getOrElse { Game(dir, dir.name, null, null, locked = true) } }

    /** Game chơi gần nhất (đã nhận diện được hệ máy). */
    fun lastPlayed(prefs: vn.aow.monika.Prefs, from: List<Game>? = cached): Game? =
        from.orEmpty().filter { it.system != null && prefs.lastPlayed(it.key) > 0 }.maxByOrNull { prefs.lastPlayed(it.key) }

    /** Xóa game. Trả về false nếu còn file không xóa được (thường là file của lần cài trước, Android khóa). */
    fun delete(game: Game): Boolean {
        if (game.external) { scanner.hide(game.key); cached = cached?.filterNot { it.key == game.key }; return true }
        val ok = game.dir.deleteRecursively() || !game.dir.exists()
        cached = cached?.filterNot { it.key == game.key && ok }
        return ok
    }
}

/**
 * Nhận diện loại game theo cấu hình:
 * 1. Quy tắc "engines" (file đánh dấu, ví dụ data.xp3 → Kirikiri).
 * 2. Đuôi file theo thứ tự khai báo trong "systems".
 */
object GameDetector {
    private const val MAX_DEPTH = 4

    fun detect(dir: File, cfg: MonikaConfig): Game {
        val meta = GameMeta.read(dir)
        if (meta != null && dir.listFiles().orEmpty().all { it.name == ".monika.json" }) {
            return Game(dir, meta.title.ifBlank { dir.name }, null, null, meta = meta, evicted = true)
        }
        val game = detectFiles(dir, cfg).copy(name = meta?.title?.ifBlank { null } ?: dir.name, meta = meta)
        return if (isLocked(dir)) game.copy(locked = true) else game
    }

    /** Có file thấy được nhưng không mở được (của bản cài trước) → cần quyền "Truy cập mọi tệp". */
    fun isLocked(dir: File): Boolean =
        dir.walkTopDown().maxDepth(MAX_DEPTH).filter { it.isFile }.take(8).any { !it.canRead() || runCatching { it.inputStream().use { s -> s.read() } }.isFailure }

    private fun detectFiles(dir: File, cfg: MonikaConfig): Game {
        val files = dir.walkTopDown().maxDepth(MAX_DEPTH).filter { it.isFile }.toList()
        val paths = files.map { it to it.path.replace('\\', '/') }

        for (rule in cfg.engines) {
            val system = cfg.system(rule.system) ?: continue
            for (marker in rule.markers) {
                val hit = paths.firstOrNull { (_, p) -> p.endsWith("/$marker", ignoreCase = true) } ?: continue
                val base = File(hit.second.dropLast(marker.length))
                val entry = rule.entry.map { File(base, it) }.firstOrNull { it.exists() } ?: base
                return Game(dir, dir.name, system, entry)
            }
        }
        for (system in cfg.systems) {
            for (ext in system.extensions) {
                files.firstOrNull { it.extension.equals(ext, ignoreCase = true) && RomSniff.accepts(it) }
                    ?.let { return Game(dir, dir.name, system, it) }
            }
        }
        val needsExtract = files.any { ArchiveExtractor.isArchive(it) }
        return Game(dir, dir.name, null, null, needsExtract)
    }
}
