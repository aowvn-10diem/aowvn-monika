package vn.aow.monika.feed

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import vn.aow.monika.config.ConfigRepository

data class Post(
    /** Mã bài của Blogger (phần sau ".post-"). */
    val id: String,
    val title: String,
    val published: String,
    val url: String,
    val labels: List<String>,
    val thumbnail: String?,
    val contentHtml: String,
)

/** Đọc feed JSON của Blogger (aow.vn). */
class FeedRepository(
    private val http: OkHttpClient,
    private val configRepo: ConfigRepository,
) {
    private val cache = mutableMapOf<String, Post>()

    fun cached(id: String): Post? = cache[id]

    /** @param query tìm kiếm toàn văn (tham số q của Blogger). */
    suspend fun fetch(label: String? = null, startIndex: Int = 1, max: Int? = null, query: String? = null): List<Post> {
        val feed = configRepo.current.feed
        val base = feed.url.trimEnd('/') + (label?.let { "/-/" + Uri.encode(it) } ?: "")
        val q = query?.takeIf { it.isNotBlank() }?.let { "&q=" + Uri.encode(it) } ?: ""
        val url = "$base?alt=json&orderby=published&start-index=$startIndex&max-results=${max ?: feed.pageSize}$q"
        val root = getJson(url).jsonObject["feed"]!!.jsonObject
        val entries = root["entry"] as? JsonArray ?: return emptyList()
        return entries.map { parseEntry(it.jsonObject) }.onEach { cache[it.id] = it }
    }

    suspend fun fetchPost(id: String): Post {
        cache[id]?.let { return it }
        val url = configRepo.current.feed.url.trimEnd('/') + "/$id?alt=json"
        return parseEntry(getJson(url).jsonObject["entry"]!!.jsonObject).also { cache[it.id] = it }
    }

    private var pagesCache: List<Post>? = null

    /**
     * Đọc 1 trang tĩnh của blog (aow.vn/p/...) qua feed pages, không cần mở trình duyệt.
     * Dùng cho trang tải giả lập: sếp sửa trang trên Blogger là app nhận link mới.
     */
    suspend fun fetchPage(pageUrl: String, forceRefresh: Boolean = false): Post? {
        val path = pageUrl.substringAfter("://").substringAfter('/').substringBefore('?')
        val pages = pagesCache.takeUnless { forceRefresh } ?: run {
            val url = configRepo.current.feed.url.replace("/posts/", "/pages/").trimEnd('/') + "?alt=json&max-results=500"
            val root = getJson(url).jsonObject["feed"]!!.jsonObject
            (root["entry"] as? JsonArray).orEmpty().map { parseEntry(it.jsonObject) }.also { pagesCache = it }
        }
        return pages.firstOrNull { it.url.substringAfter("://").substringAfter('/') == path }
    }

    private suspend fun getJson(url: String): JsonElement = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            check(response.isSuccessful) { "Không tải được bài viết (HTTP ${response.code})" }
            Json.parseToJsonElement(response.body!!.string())
        }
    }

    private fun parseEntry(e: JsonObject): Post {
        fun JsonObject.text(key: String) = (this[key] as? JsonObject)?.get("\$t")?.jsonPrimitive?.content.orEmpty()
        val rawId = e.text("id")
        val links = (e["link"] as? JsonArray).orEmpty().map { it.jsonObject }
        return Post(
            id = rawId.substringAfterLast(".post-"),
            title = e.text("title"),
            published = e.text("published"),
            url = links.firstOrNull { it["rel"]?.jsonPrimitive?.content == "alternate" }
                ?.get("href")?.jsonPrimitive?.content.orEmpty(),
            labels = (e["category"] as? JsonArray).orEmpty().mapNotNull { it.jsonObject["term"]?.jsonPrimitive?.content },
            // Blogger trả ảnh 72px; đổi sang cỡ lớn hơn cho đẹp.
            thumbnail = (e["media\$thumbnail"] as? JsonObject)?.get("url")?.jsonPrimitive?.content
                ?.replace("/s72-c/", "/w640-h360-c/"),
            contentHtml = e.text("content").ifEmpty { e.text("summary") },
        )
    }
}
