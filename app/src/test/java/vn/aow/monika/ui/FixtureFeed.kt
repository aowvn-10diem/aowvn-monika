package vn.aow.monika.ui

import vn.aow.monika.feed.FeedSource
import vn.aow.monika.feed.Post

/**
 * Nguồn bài tĩnh cho test giao diện.
 *
 * Màn danh sách thật (tab Game) tự gọi `AppGraph.feed` — repository đi HTTP tới aow.vn — nên test
 * dựng màn với nguồn này để nội dung cố định và **không chạm mạng**: cùng máy, cùng kết quả.
 */
class FixtureFeed(private val posts: List<Post> = fixturePosts()) : FeedSource {
    /** Số lần màn gọi tải (vẫn trả dữ liệu tĩnh, chỉ để test biết màn đã tự nạp). */
    var fetches = 0
        private set

    override fun cachedList(key: String): List<Post>? = posts

    override suspend fun fetchList(key: String, label: String?, max: Int?): List<Post> {
        fetches++
        return posts
    }

    override suspend fun fetch(label: String?, startIndex: Int, max: Int?, query: String?): List<Post> {
        fetches++
        return posts.drop((startIndex - 1).coerceAtLeast(0))
    }
}

/**
 * Bài mẫu cố định, đủ dài để danh sách cuộn được trong khung thấp của test.
 * Không ảnh bìa (mạng) — hàng bài tự có nền `surfaceSoft` nên vẫn vẽ ra điểm ảnh.
 */
fun fixturePosts(count: Int = 8): List<Post> = (1..count).map { i ->
    Post(
        id = "fixture-$i",
        title = "Bài mẫu $i",
        published = "2026-01-%02dT08:00:00+07:00".format(i),
        url = "https://aow.vn/2026/01/fixture-$i.html",
        labels = listOf("GBA"),
        thumbnail = null,
        contentHtml = "<p>Nội dung mẫu $i</p>",
    )
}
