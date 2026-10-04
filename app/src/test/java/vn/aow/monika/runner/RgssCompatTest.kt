package vn.aow.monika.runner

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [34])
class RgssCompatTest {
    private fun game(block: (Context, File) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(context.cacheDir, "compat-${System.nanoTime()}").apply { mkdirs() }
        try { block(context, dir) } finally { dir.deleteRecursively() }
    }

    @Test fun vxAddsRuntimePreloadWithoutChangingGameBytes() = game { context, dir ->
        val archive = File(dir, "Game.rgss2a").apply { writeText("synthetic, not a game") }
        val config = File(dir, "mkxp.json").apply { writeText("{\"rgssVersion\":2,\"custom\":true}") }
        val before = config.readBytes()
        val preloads = RgssCompat.preloads(context, dir)
        assertEquals(listOf("monika-win32api.rb", "monika-ruby18.rb"), preloads.map { File(it).name })
        assertTrue(preloads.all { File(it).isFile })
        assertEquals("synthetic, not a game", archive.readText())
        assertArrayEquals(before, config.readBytes())
    }

    @Test fun xpAceAndExplicitOverrideKeepWin32Only() = game { context, dir ->
        for (name in listOf("Game.rgssad", "Game.rgss3a")) {
            File(dir, name).writeText("synthetic")
            assertEquals(listOf("monika-win32api.rb"), RgssCompat.preloads(context, dir).map { File(it).name })
            File(dir, name).delete()
        }
        File(dir, "Game.rgss2a").writeText("synthetic")
        File(dir, "mkxp.json").writeText("{\"rgssVersion\":3}")
        assertEquals(listOf("monika-win32api.rb"), RgssCompat.preloads(context, dir).map { File(it).name })
    }
}
