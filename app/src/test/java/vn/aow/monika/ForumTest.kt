package vn.aow.monika

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.aow.monika.config.CommunityConfig
import vn.aow.monika.forum.ForumRepository
import java.time.OffsetDateTime

class ForumTest {
    private fun res(name: String) = javaClass.classLoader!!.getResource("forum/$name")!!.readText()

    @Test fun parsesRealFlarumDiscussions() {
        val list = ForumRepository.parseTopics(res("discussions.json"), "https://forum.aowvn.org", listOf("Nhà Tù AowVN"))
        assertEquals(5, list.size)
        val first = list.first { it.id == "303" }
        assertEquals("Deltarune Việt hóa Full Chapter [Hoàn Thành]", first.title)
        assertEquals("https://forum.aowvn.org/d/303-deltarune-viet-hoa-full-chapter-hoan-thanh", first.url)
        assertEquals(130, first.replies) // commentCount 131 gồm bài mở đầu
        assertTrue(first.views > 20000)
        assertTrue(first.tags.isNotEmpty())
        assertTrue(first.author.isNotBlank())
    }

    @Test fun parsesTagsAndHidesUnwanted() {
        val tags = ForumRepository.parseTags(res("tags.json"), "https://forum.aowvn.org", listOf("Nhà Tù AowVN"))
        assertTrue(tags.isNotEmpty())
        assertEquals("Sảnh Chính", tags.first().name) // nhiều chủ đề nhất đứng đầu
        assertEquals("https://forum.aowvn.org/t/sanh-chinh", tags.first().url)
        assertFalse(tags.any { it.name == "Nhà Tù AowVN" || it.topics == 0 })
    }

    @Test fun relativeTimeAndNumbers() {
        val now = OffsetDateTime.parse("2026-09-29T12:00:00+00:00")
        assertEquals("vừa xong", ForumRepository.relative("2026-09-29T11:59:40+00:00", now))
        assertEquals("5 phút trước", ForumRepository.relative("2026-09-29T11:55:00+00:00", now))
        assertEquals("3 giờ trước", ForumRepository.relative("2026-09-29T09:00:00+00:00", now))
        assertEquals("13 ngày trước", ForumRepository.relative("2026-09-16T12:00:00+00:00", now))
        assertEquals("2 tuần trước", ForumRepository.relative("2026-09-15T12:00:00+00:00", now))
        assertEquals("830", ForumRepository.compact(830))
        assertEquals("24,1K", ForumRepository.compact(24127))
    }

    @Test fun emojiPerTag() {
        assertEquals("fluent3d_classical_building", ForumRepository.emojiFor(listOf("Sảnh Chính")))
        assertEquals("fluent3d_sparkles", ForumRepository.emojiFor(listOf("Trung Tâm Việt Hoá")))
        assertEquals("fluent3d_books", ForumRepository.emojiFor(listOf("Hướng dẫn")))
        assertEquals("fluent3d_pushpin", ForumRepository.emojiFor(listOf("Sảnh Chính"), sticky = true))
    }

    @Test fun communityChannelsFallBackToLegacyFields() {
        val c = CommunityConfig(facebookGroup = "https://facebook.com/groups/x", discord = "")
        assertEquals(listOf("facebook"), c.shown().map { it.id }) // discord trống → ẩn
        val d = CommunityConfig(facebookGroup = "a", discord = "https://discord.gg/abc")
        assertEquals(listOf("facebook", "discord"), d.shown().map { it.id })
    }
}
