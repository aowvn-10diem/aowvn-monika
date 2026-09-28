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

class GameMetaSafeTest {
    @get:org.junit.Rule val tmp = org.junit.rules.TemporaryFolder()
    /** File .monika.json không đọc được (của bản cài trước) → null, không crash. */
    @Test fun unreadableMetaDoesNotThrow() {
        val dir = tmp.newFolder()
        val f = java.io.File(dir, ".monika.json").apply { writeText("{\"title\":\"x\"}"); setReadable(false) }
        org.junit.Assume.assumeFalse("chạy bằng root thì vẫn đọc được", f.canRead())
        assertEquals(null, vn.aow.monika.library.GameMeta.read(dir))
        org.junit.Assert.assertTrue(vn.aow.monika.library.GameDetector.isLocked(dir))
    }
    @Test fun brokenJson() {
        val dir = tmp.newFolder(); java.io.File(dir, ".monika.json").writeText("{hỏng")
        assertEquals(null, vn.aow.monika.library.GameMeta.read(dir))
    }
}
