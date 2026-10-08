package vn.aow.monika.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.aow.monika.feed.Post

class NewPostSelectionTest {
    private fun post(id: String, vararg labels: String) =
        Post(id, "Bài $id", "2026-10-08", "https://aow.vn/p/$id.html", labels.toList(), null, "")

    @Test fun skipsAlreadySeenPosts() {
        val posts = listOf(post("a"), post("b"), post("c"))
        assertEquals(listOf("b", "c"), NewPostWorker.postsToNotify(posts, setOf("a"), emptySet()).map { it.id })
    }

    @Test fun noSubscriptionAllowsEveryLabel() {
        val posts = listOf(post("a", "game"), post("b"))
        assertEquals(listOf("a", "b"), NewPostWorker.postsToNotify(posts, emptySet(), emptySet()).map { it.id })
    }

    @Test fun subscriptionKeepsOnlyMatchingLabels() {
        val posts = listOf(post("a", "game"), post("b", "app"), post("c", "rpg", "app"))
        assertEquals(listOf("b", "c"), NewPostWorker.postsToNotify(posts, emptySet(), setOf("app")).map { it.id })
    }

    @Test fun atMostFivePostsPerRunKeepingOrder() {
        val posts = (1..7).map { post("p$it") }
        assertEquals(listOf("p1", "p2", "p3", "p4", "p5"), NewPostWorker.postsToNotify(posts, emptySet(), emptySet()).map { it.id })
    }

    @Test fun seenIdsPutNewPostsFirstAndCapAt200() {
        val old = (0 until 199).map { "old$it" }.toSet()
        val next = NewPostWorker.nextSeenIds(listOf(post("n1"), post("n2"), post("n3")), old)
        assertEquals(200, next.size)
        assertTrue(next.containsAll(listOf("n1", "n2", "n3")))
        assertTrue("old196" in next)
        assertFalse("old197" in next)
    }

    @Test fun seenIdsKeepSmallSetsWhole() {
        assertEquals(setOf("a", "b", "c"), NewPostWorker.nextSeenIds(listOf(post("a"), post("b")), setOf("c")))
    }

    // PM chốt (#120, #124): bài vượt MAX_PER_RUN vẫn ghi là đã thấy, không báo lại ở lượt sau.
    @Test fun postsOverRunLimitStillCountAsSeen() {
        val posts = (1..7).map { post("p$it") }
        assertEquals(posts.map { it.id }.toSet(), NewPostWorker.nextSeenIds(posts, emptySet()))
    }
}
