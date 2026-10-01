package vn.aow.monika.achievements

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import vn.aow.monika.library.Game
import java.io.File

/**
 * Gắn game trong Thư viện với game RA: băm file → tra bảng hash của hệ máy (tải từ RA, lưu 7 ngày).
 * Kết quả lưu theo "đường dẫn|kích thước|ngày sửa" để không băm lại.
 */
class RaGameIndex(context: Context, private val api: RaApi, private val account: RaAccount) {
    private val dir = File(context.filesDir, "ra").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true }
    private val matchFile = File(dir, "match.json")
    private val matches: MutableMap<String, Int> = runCatching {
        json.decodeFromString(MapSerializer(String.serializer(), Int.serializer()), matchFile.readText()).toMutableMap()
    }.getOrDefault(mutableMapOf())

    private fun listFile(console: Int) = File(dir, "gamelist-$console.json")

    /** Bảng hash → ID game của 1 hệ. Lỗi mạng mà có bản cũ thì dùng bản cũ. */
    private suspend fun hashes(console: Int, key: String): Map<String, Int> = withContext(Dispatchers.IO) {
        val f = listFile(console)
        val fresh = f.isFile && System.currentTimeMillis() - f.lastModified() < 7L * 24 * 3600 * 1000
        val list: List<RaGameListItem> = if (fresh) json.decodeFromString(ListSerializer(RaGameListItem.serializer()), f.readText())
        else runCatching { api.gameList(console, key).also { f.writeText(json.encodeToString(ListSerializer(RaGameListItem.serializer()), it)) } }
            .getOrElse { if (f.isFile) json.decodeFromString(ListSerializer(RaGameListItem.serializer()), f.readText()) else throw it }
        buildMap { list.forEach { g -> g.hashes.forEach { put(it.lowercase(), g.id) } } }
    }

    /** ID game RA của game này, hoặc null nếu chưa hỗ trợ / không có thành tựu / chưa đăng nhập. */
    suspend fun gameIdFor(game: Game): Int? = withContext(Dispatchers.IO) {
        val creds = account.creds.value ?: return@withContext null
        val system = game.system ?: return@withContext null
        val file = game.entry?.takeIf { it.isFile } ?: return@withContext null
        val console = RaHasher.consoleFor(system.id, file) ?: return@withContext null
        val cacheKey = "${file.path}|${file.length()}|${file.lastModified()}"
        matches[cacheKey]?.let { return@withContext it.takeIf { id -> id > 0 } }
        val hash = RaHasher.hash(file, console) ?: return@withContext null
        val id = hashes(console, creds.key)[hash] ?: -1
        matches[cacheKey] = id
        runCatching { matchFile.writeText(json.encodeToString(MapSerializer(String.serializer(), Int.serializer()), matches)) }
        id.takeIf { it > 0 }
    }

    /** Hệ này có băm được ở bản hiện tại không (để UI giải thích khi không gắn được). */
    fun supported(systemId: String, file: File) = RaHasher.consoleFor(systemId, file) != null
}
