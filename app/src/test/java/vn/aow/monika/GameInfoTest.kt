package vn.aow.monika

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.aow.monika.config.ConfigRepository
import vn.aow.monika.feed.FeedRepository
import vn.aow.monika.library.Game
import vn.aow.monika.library.GameInfoResolver
import vn.aow.monika.ui.TestApp
import java.io.File

/** Thư viện tự nhận tên + ảnh: đọc trong ROM/JAR, rồi khớp bài aow.vn. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34])
class GameInfoTest {
    @get:Rule val tmp = TemporaryFolder()
    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun feedReturning(vararg titles: String): Pair<FeedRepository, MutableList<String>> {
        val asked = mutableListOf<String>()
        val entries = titles.mapIndexed { i, t ->
            """{"id":{"${'$'}t":"tag:blogger.com,1999:blog-1.post-$i"},"title":{"${'$'}t":"$t"},"published":{"${'$'}t":""},
               "link":[{"rel":"alternate","href":"https://www.aow.vn/p$i.html"}],
               "media${'$'}thumbnail":{"url":"https://blogger.googleusercontent.com/img/b/X/s72-c/g$i.jpg"}}"""
        }
        val body = """{"feed":{"entry":[${entries.joinToString(",")}]}}"""
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            asked += chain.request().url.queryParameter("q").orEmpty()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        return FeedRepository(http, ConfigRepository(app, http)) to asked
    }

    /** ROM NDS tối thiểu: header trỏ tới banner có icon + tên tiếng Anh. */
    private fun fakeNds(name: String, title: String): File {
        val rom = ByteArray(0x1000 + 0x840)
        val off = 0x1000
        rom[0x68] = (off and 0xff).toByte(); rom[0x69] = (off shr 8).toByte()
        for (i in 0 until 512) rom[off + 0x20 + i] = 0x21 // chỉ số màu 1 và 2
        rom[off + 0x222] = 0x1f; rom[off + 0x224] = 0xe0.toByte(); rom[off + 0x225] = 0x03
        title.toByteArray(Charsets.UTF_16LE).copyInto(rom, off + 0x340)
        return File(tmp.root, "$name.nds").apply { writeBytes(rom) }
    }

    private fun game(f: File) = Game(f.parentFile!!, f.nameWithoutExtension, vn.aow.monika.config.SystemDef(id = "nds", name = "Nintendo DS", runner = "libretro"), f, external = true)

    @Test fun scoring() {
        assertEquals("Pokemon Fire Red", GameInfoResolver.cleanName("1234 - Pokemon_Fire_Red (USA) [v1.1]"))
        assertTrue(GameInfoResolver.score("Pokemon Fire Red", "Pokémon Fire Red") >= 0.9)
        assertTrue(GameInfoResolver.score("Pokemon Fire Red", "Pokemon Emerald") < 0.6)
        assertEquals(0.0, GameInfoResolver.score("Ys", "Ys Origin"), 0.0)
    }

    @Test fun ndsBannerThenAowPost() = runBlocking {
        val (feed, asked) = feedReturning("Game Pokemon Emerald Việt Hóa | GBA", "[MỚI] Game Pokémon HeartGold Việt Hóa | NDS Android PC")
        val r = GameInfoResolver(app, feed)
        val g = game(fakeNds("0001", "POKEMON HeartGold\nVersion\nNintendo"))
        r.resolveAll(listOf(g))
        val out = r.apply(g)
        assertEquals("Pokémon HeartGold", out.name)
        assertEquals("1", out.meta?.postId)
        assertTrue(out.meta?.cover!!.startsWith("https://"))
        // Tên file "0001" vô nghĩa → tìm bằng tên trong banner.
        assertEquals(listOf("POKEMON HeartGold Version"), asked)
    }

    @Test fun offlineFallsBackToRomIcon() = runBlocking {
        val (feed, _) = feedReturning() // không có bài khớp
        val r = GameInfoResolver(app, feed)
        val g = game(fakeNds("Mario Kart DS", "MARIO KART DS\nNintendo"))
        r.resolveAll(listOf(g))
        val out = r.apply(g)
        assertEquals("Mario Kart Ds", out.name)
        assertNotNull(out.meta?.cover)
        assertTrue(GameInfoResolver.isIcon(out.meta?.cover))
        val png = android.graphics.BitmapFactory.decodeFile(out.meta!!.cover!!.removePrefix("file://"))
        assertEquals(32, png.width)
        // Game đã tra → lần sau không gọi mạng nữa.
        val (feed2, asked2) = feedReturning()
        GameInfoResolver(app, feed2).resolveAll(listOf(g))
        assertEquals(emptyList<String>(), asked2)
    }

    @Test fun jarManifest() = runBlocking {
        val jar = File(tmp.root, "game.jar")
        java.util.zip.ZipOutputStream(jar.outputStream()).use { z ->
            z.putNextEntry(java.util.zip.ZipEntry("META-INF/MANIFEST.MF"))
            z.write("Manifest-Version: 1.0\r\nMIDlet-1: Ninja School, /icon.png, Main\r\nMIDlet-Name: Ninja School\r\n".toByteArray())
            z.putNextEntry(java.util.zip.ZipEntry("icon.png"))
            val bmp = android.graphics.Bitmap.createBitmap(16, 16, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(-0x10000) }
            val bytes = java.io.ByteArrayOutputStream().also { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            z.write(bytes)
        }
        val (feed, _) = feedReturning()
        val r = GameInfoResolver(app, feed)
        val g = Game(tmp.root, "game", vn.aow.monika.config.SystemDef(id = "j2me", name = "Java", runner = "j2me"), jar, external = true)
        r.resolveAll(listOf(g))
        val out = r.apply(g)
        assertEquals("Ninja School", out.name)
        assertTrue(GameInfoResolver.isIcon(out.meta?.cover))
    }
}
