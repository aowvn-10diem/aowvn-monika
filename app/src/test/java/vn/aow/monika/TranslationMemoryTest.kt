package vn.aow.monika

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vn.aow.monika.translate.TranslationMemory
import java.io.File

class TranslationMemoryTest {
    @Test fun remembersPerProviderAndPersists() {
        val f = File.createTempFile("tmem", ".json").apply { deleteOnExit() }
        val m = TranslationMemory(f)
        m.put("mlkit", "en", "Hello", "Xin chào")
        assertEquals("Xin chào", m.get("mlkit", "en", "Hello"))
        assertNull(m.get("google", "en", "Hello")) // khác nhà cung cấp → không dùng lẫn
        assertEquals("Xin chào", TranslationMemory(f).get("mlkit", "en", "Hello"))
    }

    @Test fun keepsOnlyNewestEntries() {
        val f = File.createTempFile("tmem", ".json").apply { deleteOnExit() }
        val m = TranslationMemory(f)
        repeat(TranslationMemory.MAX + 5) { m.put("p", "en", "t$it", "v$it") }
        assertEquals(TranslationMemory.MAX, m.size); assertNull(m.get("p", "en", "t0"))
    }
}
