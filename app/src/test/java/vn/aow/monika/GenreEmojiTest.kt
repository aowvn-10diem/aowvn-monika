package vn.aow.monika

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.aow.monika.forum.ForumRepository

class GenreEmojiTest {
    @Test fun keywordsMatchWithoutAccents() {
        assertEquals("fluent3d_books", ForumRepository.genreEmoji("Visual Novel"))
        assertEquals("fluent3d_crown", ForumRepository.genreEmoji("Nhập vai"))
        assertEquals("fluent3d_stopwatch", ForumRepository.genreEmoji("Đua xe"))
    }

    @Test fun noFalseMatchInsideWords() {
        assertEquals(null, ForumRepository.genreEmoji("Game mới"))
    }

    @Test fun assignedEmojisAreDistinct() {
        val names = listOf("Nhập vai", "Hành động", "Game mới", "Khác", "Lạ 1", "Lạ 2", "Visual Novel", "Đua xe", "Giải đố", "Thể thao")
        val r = ForumRepository.assignGenreEmojis(names)
        assertEquals(names.size, r.toSet().size)
        assertTrue(r.all { it.startsWith("fluent3d_") })
        assertNotEquals(r[2], r[3])
    }
}
