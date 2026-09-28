package vn.aow.monika

import org.junit.Assert.assertEquals
import org.junit.Test
import vn.aow.monika.feed.Thumbs

class ThumbsTest {
    private val b = "https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsE"
    @Test fun allBloggerFormats() {
        assertEquals("$b/w800-h450-c-rw/game.jpg", Thumbs.card("$b/s72-c/game.jpg"))
        assertEquals("$b/w800-h450-c-rw/game.webp", Thumbs.card("$b/s72-c-rw/game.webp"))
        assertEquals("$b/w800-h450-c-rw/game+%25281%2529.jpg", Thumbs.card("$b/s72-c-d/game+%25281%2529.jpg"))
        assertEquals("$b/w800-h450-c-rw/game.jpg", Thumbs.card("$b/w640-h360-c/game.jpg"))
        assertEquals("https://blogger.googleusercontent.com/img/a/AVvXsEgF=w1200-h1100-c-rw", Thumbs.hero("https://blogger.googleusercontent.com/img/a/AVvXsEgF=s72-c-rw"))
    }
    @Test fun untouched() {
        assertEquals("$b/game-original.webp", Thumbs.card("$b/game-original.webp"))
        assertEquals("https://i.ytimg.com/vi/x/hqdefault.jpg", Thumbs.card("https://i.ytimg.com/vi/x/hqdefault.jpg"))
        assertEquals(null, Thumbs.card(null))
    }
}
