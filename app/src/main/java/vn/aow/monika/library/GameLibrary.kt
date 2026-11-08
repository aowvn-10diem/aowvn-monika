package vn.aow.monika.library

import android.content.Context
import vn.aow.monika.config.ConfigRepository
import vn.aow.monika.config.MonikaConfig
import vn.aow.monika.config.SystemDef
import vn.aow.monika.diag.Diagnostics
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

    @Volatile var cachedDocuments: List<LibraryDocument> = emptyList()
        private set

    val scanner = DeviceScanner(context)

    fun list(): List<Game> {
        val internal = internalContents()
        cachedDocuments = internal.documents
        return (internal.games + externalGames()).map { g -> info?.apply(g) ?: g }
            .also { cached = it; prefetchFor(it) }
    }

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
        val matched = scanner.found().mapNotNull { f ->
            val system = cfg.systems.firstOrNull { s -> (s.extensions + s.extensionsSniffed).any { it.equals(f.extension, true) } && EntryPick.acceptable(f, s) } ?: return@mapNotNull null
            Triple(f, system, f.parentFile?.path.orEmpty())
        }
        // Hệ có quy tắc chọn riêng (Kirikiri): mỗi thư mục chỉ ra MỘT game (bỏ patch*.xp3, ghép exe ↔ xp3), không mỗi file một game.
        val grouped = matched.groupBy { (_, s, dirPath) -> if (s.entryPick.equals("first", true)) null else s.id to dirPath }
        grouped.flatMap { (key, list) ->
            val picked = if (key == null) list.map { it.first } else listOfNotNull(EntryPick.pick(list.map { it.first }, list.first().second))
            picked.map { f ->
                val system = list.first { it.first == f }.second
                // Tên hiển thị của game theo thư mục chứa khi hệ có nhiều file (tên file kho chính thường là viết tắt).
                val title = if (key == null) f.nameWithoutExtension.replace('_', ' ') else (f.parentFile?.name ?: f.nameWithoutExtension).replace('_', ' ')
                Game(f.parentFile ?: f, title, system, f, external = true)
            }
        }
    }.getOrDefault(emptyList())

    private fun internalContents(): LibraryContents = partitionLibrary(
        GameStorage.games(context).listFiles().orEmpty()
            .filter { it.isDirectory }
            .sortedByDescending { it.lastModified() }
            // 1 thư mục lỗi không được làm hỏng cả thư viện (và không bao giờ làm app crash).
            .mapNotNull { dir -> runCatching { GameDetector.detect(dir, configRepo.current) }.getOrElse { Game(dir, dir.name, null, null, locked = true) } },
        configRepo.current,
    )

    /** Chỉ xóa đúng tệp tài liệu; không xóa thư mục có thể chứa game hay tài liệu khác. */
    fun deleteDocument(document: LibraryDocument): Boolean {
        val ok = runCatching { document.file.delete() || !document.file.exists() }.getOrDefault(false)
        if (ok) cachedDocuments = cachedDocuments.filterNot { it.key == document.key }
        return ok
    }

    /** Game chơi gần nhất (đã nhận diện được hệ máy). */
    fun lastPlayed(prefs: vn.aow.monika.Prefs, from: List<Game>? = cached): Game? =
        from.orEmpty().filter { it.system != null && prefs.lastPlayed(it.key) > 0 }.maxByOrNull { prefs.lastPlayed(it.key) }

    /**
     * Xoá game **an toàn**: sao lưu **toàn bộ thư mục game** trước (A54), chỉ xoá thư mục game
     * do app quản lý (con cấp 1 của kho), và dừng nếu sao lưu lỗi hoặc chưa quét/sao lưu trọn vẹn.
     * Game quét trong máy (ngoài kho) chỉ ẩn, không xoá tệp nào.
     * [backup] có tham số để test tiêm lỗi/cảnh báo; chạy thật thì dùng [GameSaveBackup.backupFolder].
     */
    fun deleteWithBackup(
        game: Game,
        backup: (Game) -> GameFolderBackup = { GameSaveBackup.backupFolder(context, it) },
    ): GameDeleteOutcome {
        if (game.external) {
            scanner.hide(game.key)
            cached = cached?.filterNot { it.key == game.key }
            return GameDeleteOutcome(
                deleted = true, backup = null,
                message = "Đã ẩn \"${game.name}\" khỏi Thư viện. Tệp game trong máy và save không bị xoá.",
            )
        }
        // Chỉ xoá thư mục game ngay dưới kho Monika; thư mục lạ/ngoài kho thì app không đụng.
        val managed = runCatching {
            val kho = GameStorage.games(context).canonicalFile
            game.dir.canonicalFile.parentFile == kho
        }.onFailure { Diagnostics.recordHandled(context, "thu-vien", "xoá game: kiểm tra thư mục trong kho", it) }
            .getOrDefault(false)
        if (!managed) {
            return GameDeleteOutcome(
                deleted = false, backup = null,
                message = "App không xoá thư mục này vì nó không nằm trong kho game của Monika. " +
                    "Muốn dọn, hãy dùng trình quản lý tệp của máy.",
            )
        }
        val saved = try {
            backup(game)
        } catch (t: Throwable) {
            Diagnostics.recordHandled(context, "thu-vien", "xoá game: sao lưu thư mục game trước khi xoá", t)
            return GameDeleteOutcome(
                deleted = false, backup = null,
                message = "Chưa sao lưu được thư mục game nên chưa xoá gì: ${t.message ?: "lỗi không rõ"}. " +
                    "Thử lại hoặc dọn bằng trình quản lý tệp.",
            )
        }
        // Quét/sao lưu chưa trọn (liên kết tượng trưng, mục không đọc được, quá lớn, thiếu chỗ) → không xoá
        // và **không phát tệp zip dở** để chia sẻ (backup = null): người chơi không thể tưởng đã có bản sao an toàn.
        if (!saved.complete) {
            return GameDeleteOutcome(
                deleted = false, backup = null,
                message = "Bản sao lưu chưa đầy đủ nên không tạo tệp và chưa xoá gì:\n" + saved.notes.joinToString("\n") +
                    "\nHãy dọn bằng trình quản lý tệp nếu chắc chắn muốn xoá.",
            )
        }
        val ok = game.dir.deleteRecursively() || !game.dir.exists()
        cached = cached?.filterNot { it.key == game.key && ok }
        val kept = saved.file?.let {
            "Bản sao lưu thư mục game: ${it.absolutePath}" +
                (if (saved.notes.isEmpty()) "" else "\nGhi chú: " + saved.notes.joinToString(" "))
        } ?: "Thư mục game rỗng nên không cần sao lưu; save của giả lập trong kho Monika vẫn giữ nguyên."
        return GameDeleteOutcome(
            deleted = ok, backup = saved.file,
            message = if (ok) "Đã xoá \"${game.name}\" khỏi kho.\n$kept"
            else "Chưa xoá hết \"${game.name}\": Android không cho xoá một số tệp.\n$kept",
        )
    }

    /** Xoá game. Trả về false nếu không xoá được hoặc chưa chứng minh sao lưu đủ. */
    fun delete(game: Game): Boolean = deleteWithBackup(game).deleted
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
            // Gom mọi file hợp đuôi (kể cả đuôi cần kiểm loại thật) rồi chọn theo quy tắc của hệ máy, không lấy bừa file đầu tiên.
            val exts = system.extensions + system.extensionsSniffed
            if (exts.isEmpty()) continue
            val cands = files.filter { f -> exts.any { it.equals(f.extension, true) } && EntryPick.acceptable(f, system) }
            // Đuôi khai trong "extensions" giữ thứ tự ưu tiên cũ khi không có quy tắc chọn riêng.
            val ordered = if (system.entryPick.equals("first", true)) system.extensions.flatMap { e -> cands.filter { it.extension.equals(e, true) } } + cands.filter { c -> system.extensions.none { it.equals(c.extension, true) } } else cands
            EntryPick.pick(ordered, system)?.let { return Game(dir, dir.name, system, it) }
        }
        val needsExtract = files.any { ArchiveExtractor.isArchive(it) }
        return Game(dir, dir.name, null, null, needsExtract)
    }
}
