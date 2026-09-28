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
    /** Có file .rar/.7z chưa giải nén. */
    val needsExtract: Boolean = false,
)

class GameLibrary(private val context: Context, private val configRepo: ConfigRepository) {

    fun list(): List<Game> =
        GameStorage.games(context).listFiles().orEmpty()
            .filter { it.isDirectory }
            .sortedByDescending { it.lastModified() }
            .map { GameDetector.detect(it, configRepo.current) }

    fun delete(game: Game) = game.dir.deleteRecursively()
}

/**
 * Nhận diện loại game theo cấu hình:
 * 1. Quy tắc "engines" (file đánh dấu, ví dụ data.xp3 → Kirikiri).
 * 2. Đuôi file theo thứ tự khai báo trong "systems".
 */
object GameDetector {
    private const val MAX_DEPTH = 4

    fun detect(dir: File, cfg: MonikaConfig): Game {
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
                files.firstOrNull { it.extension.equals(ext, ignoreCase = true) }
                    ?.let { return Game(dir, dir.name, system, it) }
            }
        }
        val needsExtract = files.any { it.extension.lowercase() in Importer.UNSUPPORTED_ARCHIVES }
        return Game(dir, dir.name, null, null, needsExtract)
    }
}
