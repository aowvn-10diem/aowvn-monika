package vn.aow.monika.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import vn.aow.monika.AppGraph
import vn.aow.monika.feed.Post
import java.util.concurrent.TimeUnit

/**
 * App tự hỏi feed aow.vn định kỳ (từ máy user, IP Việt Nam nên không bị Cloudflare chặn).
 * Không cần server hay Firebase.
 */
class NewPostWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = AppGraph.prefs
        val posts = runCatching { AppGraph.feed.fetch(max = 10) }.getOrElse { return Result.retry() }
        val seen = prefs.seenPostIds
        if (seen.isEmpty()) {
            // Lần chạy đầu: chỉ ghi nhớ, không báo 10 bài cũ.
            prefs.seenPostIds = posts.map { it.id }.toSet()
            return Result.success()
        }
        val subscribed = prefs.subscribedLabels
        postsToNotify(posts, seen, subscribed).forEach { Notifier.newPost(applicationContext, it) }
        prefs.seenPostIds = nextSeenIds(posts, seen)
        return Result.success()
    }

    companion object {
        private const val NAME = "new-post-check"
        private const val MAX_PER_RUN = 5
        private const val MAX_SEEN = 200

        /** Bài chưa thấy, đúng nhãn đã đăng ký (chưa đăng ký nhãn nào = nhận hết), tối đa [MAX_PER_RUN] bài. */
        internal fun postsToNotify(posts: List<Post>, seen: Set<String>, subscribed: Set<String>): List<Post> =
            posts.filter { it.id !in seen }
                .filter { subscribed.isEmpty() || it.labels.any(subscribed::contains) }
                .take(MAX_PER_RUN)

        /** Mã bài mới trước, rồi mã cũ; giữ tối đa [MAX_SEEN] mã để bộ nhớ không phình. */
        internal fun nextSeenIds(posts: List<Post>, seen: Set<String>): Set<String> =
            (posts.map { it.id } + seen).take(MAX_SEEN).toSet()

        fun schedule(context: Context, minutes: Long) {
            val request = PeriodicWorkRequestBuilder<NewPostWorker>(minutes.coerceAtLeast(15), TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
