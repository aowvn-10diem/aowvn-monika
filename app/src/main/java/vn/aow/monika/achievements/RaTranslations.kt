package vn.aow.monika.achievements

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import vn.aow.monika.BuildConfig
import java.io.File

@Serializable
data class RaViText(val t: String = "", val d: String = "")

/**
 * Bản dịch tiếng Việt tên/mô tả thành tựu (RA không có bản dịch chính thức). Cùng nguồn với config:
 * `<thư mục của config.json>/ra-vi/<GameID>.json` = `{ "<AchievementID>": {"t": "Tên", "d": "Mô tả"} }`. Không có (404) → giữ bản gốc.
 */
class RaTranslations(context: Context, private val http: OkHttpClient) {
    private val dir = File(context.filesDir, "ra/vi").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true }
    private val ser = MapSerializer(String.serializer(), RaViText.serializer())

    suspend fun forGame(gameId: Int): Map<String, RaViText> = withContext(Dispatchers.IO) {
        val f = File(dir, "$gameId.json")
        val fresh = f.isFile && System.currentTimeMillis() - f.lastModified() < 24L * 3600 * 1000
        if (!fresh) runCatching {
            val url = BuildConfig.REMOTE_CONFIG_URL.substringBeforeLast('/') + "/ra-vi/$gameId.json"
            http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (r.isSuccessful) f.writeText(r.body!!.string()) else if (r.code == 404) f.writeText("{}")
            }
        }
        runCatching { json.decodeFromString(ser, f.readText()) }.getOrDefault(emptyMap())
    }
}
