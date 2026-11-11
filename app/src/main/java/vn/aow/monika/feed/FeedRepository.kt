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

@kotlinx.serialization.Serializable
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

/**
 * Phần đọc danh sách bài mà màn danh sách cần. Tách khỏi [FeedRepository] để test dựng màn bằng
 * dữ liệu tĩnh, không gọi mạng.
 */
interface FeedSource {
    /** Bản đã lưu của danh sách [key], null nếu chưa từng tải. */
    fun cachedList(key: String): List<Post>?

    /** Tải danh sách [key] và cập nhật bản lưu. */
    suspend fun fetchList(key: String, label: String? = null, max: Int? = null): List<Post>

    /** Tải danh sách theo nhãn/từ khóa, bắt đầu từ [startIndex]. */
    suspend fun fetch(label: String? = null, startIndex: Int = 1, max: Int? = null, query: String? = null): List<Post>
}

/**
 * Blogger trả mã HTTP lỗi. 4xx (bài đã xoá, id cũ còn trong danh sách lưu đĩa) là chuyện thường;
 * chỉ 5xx mới là lỗi thật (xem `fetchJson`).
 */
class FeedHttpException(val code: Int) : java.io.IOException("Không tải được bài viết (HTTP $code)")

/** JSON feed lệch hình dạng: thiếu `feed`/`entry` (Blogger trả HTML, bài bị gỡ…) — thay cho `!!` cũ gây NPE. */
class FeedShapeException(message: String) : java.io.IOException(message)

/** Đọc feed JSON của Blogger (aow.vn). */
class FeedRepository(
    private val http: OkHttpClient,
    private val configRepo: ConfigRepository,
    private val cacheDir: java.io.File? = null,
    private val ctx: android.content.Context? = null,
    /** V193 G1: false = không gọi mạng (bài viết đang tắt). */
    private val enabled: () -> Boolean = { true },
) : FeedSource {
    private val cache = mutableMapOf<String, Post>()

    /** Chốt duy nhất trước mọi request feed; tắt là không có request nào rời máy. */
    private fun requireEnabled() {
        check(enabled()) { "Bài viết đang tắt" }
    }

    /** Trang đầu của từng danh sách (Trang chủ, tab Game…) lưu RAM + đĩa: mở lại app hiện ngay, không tải lại từ đầu. */
    private val lists = java.util.concurrent.ConcurrentHashMap<String, List<Post>>()
    private val listJson = Json { ignoreUnknownKeys = true }

    private fun listFile(key: String) = cacheDir?.let { java.io.File(it, "feed-" + key.hashCode().toUInt().toString(16) + ".json") }

    /** Bản đã lưu của danh sách [key] (RAM trước, rồi đĩa). Null nếu chưa từng tải. */
    override fun cachedList(key: String): List<Post>? = lists[key] ?: runCatching {
        listFile(key)?.takeIf { it.exists() }?.readText()?.let { listJson.decodeFromString(kotlinx.serialization.builtins.ListSerializer(Post.serializer()), it) }
    }.getOrNull()?.also { lists[key] = it; it.forEach { p -> cache[p.id] = p } }

    /** Tải danh sách và cập nhật bản lưu. */
    override suspend fun fetchList(key: String, label: String?, max: Int?): List<Post> =
        fetch(label = label, startIndex = 1, max = max, query = null).also { list ->
            lists[key] = list
            withContext(Dispatchers.IO) {
                runCatching { listFile(key)?.writeText(listJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(Post.serializer()), list)) }
            }
        }

    fun cached(id: String): Post? = cache[id]

    /** @param query tìm kiếm toàn văn (tham số q của Blogger). */
    override suspend fun fetch(label: String?, startIndex: Int, max: Int?, query: String?): List<Post> {
        requireEnabled()
        val feed = configRepo.current.feed
        val base = feed.url.trimEnd('/') + (label?.let { "/-/" + Uri.encode(it) } ?: "")
        val q = query?.takeIf { it.isNotBlank() }?.let { "&q=" + Uri.encode(it) } ?: ""
        val url = "$base?alt=json&orderby=published&start-index=$startIndex&max-results=${max ?: feed.pageSize}$q"
        // Sai URL feed trong config là lỗi thật → vẫn báo `op-fail` khi JSON lệch hình dạng.
        val entries = fetchJson(STEP_LIST, url) { it.feedObject()["entry"] as? JsonArray }
        return entries?.map { parseEntry(it.jsonObject) }.orEmpty().onEach { cache[it.id] = it }
    }

    /**
     * Danh mục TẤT CẢ bài (bản tóm tắt: tiêu đề, ảnh, nhãn — không kèm nội dung) để nhận diện game trong Thư viện.
     * aow.vn ~300 bài → 2-3 lần gọi, vài trăm KB.
     */
    suspend fun fetchIndex(maxPages: Int = 10): List<Post> {
        requireEnabled()
        val base = configRepo.current.feed.url.trimEnd('/').replace("/posts/default", "/posts/summary")
        val out = mutableListOf<Post>()
        for (page in 0 until maxPages) {
            val url = "$base?alt=json&orderby=published&start-index=${page * INDEX_PAGE + 1}&max-results=$INDEX_PAGE"
            // Sai URL feed trong config là lỗi thật → không đánh dấu expected.
            val entries = fetchJson(STEP_INDEX, url) { it.feedObject()["entry"] as? JsonArray } ?: break
            out += entries.map { parseEntry(it.jsonObject).copy(contentHtml = "") }
            if (entries.size < INDEX_PAGE) break
        }
        return out.distinctBy { it.id }
    }

    suspend fun fetchPost(id: String): Post {
        cache[id]?.let { return it }
        requireEnabled()
        val url = configRepo.current.feed.url.trimEnd('/') + "/$id?alt=json"
        // Bài bị gỡ là chuyện thường (danh sách lưu đĩa còn id cũ): 404 và thiếu `entry` chỉ ghi vệt, không báo.
        return fetchJson(STEP_POST, url, expected = { it is FeedShapeException }) { parseEntry(it.entryObject()) }
            .also { cache[it.id] = it }
    }

    private var pagesCache: List<Post>? = null

    /**
     * Đọc 1 trang tĩnh của blog (aow.vn/p/...) qua feed pages, không cần mở trình duyệt.
     * Dùng cho trang tải giả lập: sếp sửa trang trên Blogger là app nhận link mới.
     */
    suspend fun fetchPage(pageUrl: String, forceRefresh: Boolean = false): Post? {
        requireEnabled()
        val path = pageUrl.substringAfter("://").substringAfter('/').substringBefore('?')
        val pages = pagesCache.takeUnless { forceRefresh } ?: run {
            val url = configRepo.current.feed.url.replace("/posts/", "/pages/").trimEnd('/') + "?alt=json&max-results=500"
            // Trang tĩnh bị xoá/đổi đường dẫn là chuyện thường → không báo; 5xx vẫn báo.
            fetchJson(STEP_PAGE, url, expected = { it is FeedShapeException }) { el ->
                (el.feedObject()["entry"] as? JsonArray).orEmpty().map { parseEntry(it.jsonObject) }
            }.also { pagesCache = it }
        }
        return pages.firstOrNull { it.url.substringAfter("://").substringAfter('/') == path }
    }

    /**
     * Tải [url] rồi đọc bằng [read] trong cùng một khối, để lỗi hình dạng JSON cũng được phân loại như lỗi mạng.
     * Chỉ lỗi thật mới thành báo cáo `op-fail` (xem [worthReporting]); [step] cho biết hỏng ở đường đọc nào.
     */
    private suspend fun <T> fetchJson(
        step: String,
        url: String,
        expected: (Throwable) -> Boolean = { false },
        read: (JsonElement) -> T,
    ): T = withContext(Dispatchers.IO) {
        try {
            val call = http.newCall(Request.Builder().url(url).build())
            call.timeout().timeout(FEED_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            val root = call.execute().use { response ->
                if (!response.isSuccessful) throw FeedHttpException(response.code)
                // V166: đọc body qua mạng → đứt luồng ở đây là chuyện mạng (đánh dấu nguồn).
                vn.aow.monika.diag.Diagnostics.networkRead { Json.parseToJsonElement(response.body!!.string()) }
            }
            read(root)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // V112: chỉ 5xx/IO/JSON sai ở đường không mong đợi mới là lỗi app. 4xx (bài đã xoá, id cũ trong danh
            // sách lưu) và lỗi đã khai báo [expected] là luồng thường → chỉ để lại vệt, tránh dồn báo cáo như #305/#512.
            ctx?.let { c ->
                if (worthReporting(e, step, expected))
                    vn.aow.monika.diag.Diagnostics.opFail(c, vn.aow.monika.diag.Diagnostics.Op.FETCH_FEED, step, e)
                else
                    vn.aow.monika.diag.Diagnostics.crumb(c, "tai-feed", "$step: ${e.javaClass.simpleName} (bình thường, không báo)")
            }
            throw e
        }
    }

    /** Lỗi đáng lập báo cáo: không phải lỗi đã khai báo, không phải 4xx, không phải lỗi mạng. */
    private fun worthReporting(e: Throwable, step: String, expected: (Throwable) -> Boolean): Boolean =
        !expected(e) && !(e is FeedHttpException && e.code < 500) &&
            !vn.aow.monika.diag.Diagnostics.isOffline(step, e)

    /** Object `feed` của Blogger; thiếu/sai kiểu → [FeedShapeException] thay cho `!!`. */
    private fun JsonElement.feedObject(): JsonObject =
        (this as? JsonObject)?.get("feed") as? JsonObject
            ?: throw FeedShapeException("JSON feed thiếu \"feed\"")

    /** Một bài trả về rời (`/posts/default/<id>?alt=json`) bọc `entry` ở gốc; thiếu → bài đã bị gỡ. */
    private fun JsonElement.entryObject(): JsonObject =
        (this as? JsonObject)?.get("entry") as? JsonObject
            ?: throw FeedShapeException("JSON feed thiếu \"entry\"")

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

    private companion object {
        const val INDEX_PAGE = 150
        const val FEED_TIMEOUT_SECONDS = 10L
        // Bước riêng cho từng đường đọc: trước đây cả 4 dùng chung "tai-bai" nên mọi kiểu hỏng dồn vào 1 issue.
        const val STEP_LIST = "tai-danh-sach"
        const val STEP_INDEX = "tai-chi-muc"
        const val STEP_POST = "tai-bai"
        const val STEP_PAGE = "tai-trang"
    }
}
