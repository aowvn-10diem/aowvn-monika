package vn.aow.monika.achievements

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

/** Logic RetroAchievements không cần Android: băm file, mô hình dữ liệu, gọi API qua máy chủ giả (không mạng, không khóa thật). */
class RaLogicTest {
    @get:Rule val tmp = TemporaryFolder()

    /** Trả lời theo [reply]; mọi yêu cầu được ghi vào [seen]. */
    private class Fake(val reply: (Request) -> Pair<Int, String>) {
        val seen = ArrayList<Request>()
        val client: OkHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
            val req = chain.request()
            seen += req
            val (code, body) = reply(req)
            Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(code).message("x")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
    }

    // ---------- Băm file ----------

    @Test fun consoleIdsFollowSystemForNonAmbiguousSystems() {
        assertEquals(5, RaHasher.consoleFor("gba", File("a.gba")))
        assertEquals(3, RaHasher.consoleFor("snes", File("a.sfc")))
        assertEquals(1, RaHasher.consoleFor("genesis", File("a.md")))
        assertEquals(11, RaHasher.consoleFor("sms", File("a.sms")))
        assertEquals(15, RaHasher.consoleFor("gg", File("a.gg")))
        assertEquals(14, RaHasher.consoleFor("ngp", File("a.ngp")))
        assertEquals(53, RaHasher.consoleFor("ws", File("a.ws")))
        assertEquals(25, RaHasher.consoleFor("a2600", File("a.a26")))
        assertNull(RaHasher.consoleFor("psp", File("a.iso")))
    }

    @Test fun gameBoyAndNesNeedTheRightExtension() {
        assertEquals(6, RaHasher.consoleFor("gbc", File("a.gbc")))
        assertEquals(4, RaHasher.consoleFor("gbc", File("a.gb")))
        assertNull(RaHasher.consoleFor("gbc", File("a.zip")))
        assertEquals(7, RaHasher.consoleFor("nes", File("A.NES")))
        assertNull(RaHasher.consoleFor("nes", File("a.zip")))
    }

    @Test fun headersAreSkippedOnlyWhenPresent() {
        val nesHead = byteArrayOf(0x4E, 0x45, 0x53, 0x1A) + ByteArray(12)
        assertEquals(16, RaHasher.headerSize(7, 16L + 100, nesHead))
        // Kích thước không lớn hơn header: không bỏ byte nào.
        assertEquals(0, RaHasher.headerSize(7, 16L, nesHead))
        // Không có chữ ký NES: không bỏ byte nào.
        assertEquals(0, RaHasher.headerSize(7, 100L, ByteArray(16)))
        // SNES: header 512 byte khi phần dư theo 8 KB là đúng 512.
        assertEquals(512, RaHasher.headerSize(3, 0x2000L * 3 + 512, ByteArray(0)))
        assertEquals(0, RaHasher.headerSize(3, 0x2000L * 3, ByteArray(0)))
        assertEquals(0, RaHasher.headerSize(1, 0x2000L * 3 + 512, ByteArray(0)))
    }

    @Test fun hashIsMd5OfWholeFileOrAfterHeader() {
        val plain = File(tmp.root, "plain.bin").apply { writeText("abc") }
        assertEquals("900150983cd24fb0d6963f7d28e17f72", RaHasher.hash(plain, 1))
        val nes = File(tmp.root, "game.nes").apply {
            writeBytes(byteArrayOf(0x4E, 0x45, 0x53, 0x1A) + ByteArray(12) + "abc".toByteArray())
        }
        assertEquals("900150983cd24fb0d6963f7d28e17f72", RaHasher.hash(nes, 7))
        assertNull(RaHasher.hash(File(tmp.root, "missing.bin"), 1))
        assertNull(RaHasher.hash(File(tmp.root, "empty.bin").apply { writeBytes(ByteArray(0)) }, 1))
    }

    @Test fun md5SkipsRequestedBytesAndStopsAtEnd() {
        assertEquals("900150983cd24fb0d6963f7d28e17f72", RaHasher.md5(ByteArrayInputStream("xabc".toByteArray()), 1))
        // Bỏ nhiều hơn độ dài file: băm phần còn lại (rỗng).
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", RaHasher.md5(ByteArrayInputStream("ab".toByteArray()), 5))
    }

    // ---------- Mô hình ----------

    @Test fun earnedWhenEitherModeIsUnlocked() {
        assertFalse(RaAchievement(id = 1).earned)
        assertTrue(RaAchievement(id = 1, dateEarned = "2026-01-01").earned)
        assertTrue(RaAchievement(id = 1, dateEarnedHardcore = "2026-01-02").earned)
    }

    @Test fun orderedFollowsRetroAchievementsDisplayOrder() {
        val game = RaGameProgress(achievements = mapOf(
            "1" to RaAchievement(id = 1, title = "B", displayOrder = 2),
            "2" to RaAchievement(id = 2, title = "A", displayOrder = 1),
        ))
        assertEquals(listOf("A", "B"), game.ordered.map { it.title })
    }

    @Test fun mediaAndBadgeUrls() {
        assertEquals("https://media.retroachievements.org/Badge/12345.png", RaApi.badge("12345"))
        assertEquals("https://media.retroachievements.org/Badge/12345_lock.png", RaApi.badge("12345", locked = true))
        assertEquals("https://media.retroachievements.org/Images/x.png", RaApi.media("/Images/x.png"))
        assertEquals("https://other.example/x.png", RaApi.media("https://other.example/x.png"))
    }

    // ---------- API qua máy chủ giả ----------

    @Test fun profileSendsUserAndKeyAndReadsPascalCase() = runBlocking {
        val fake = Fake { _ -> (200 to """{"User":"lan","TotalPoints":12,"Motto":null,"Extra":1}""") }
        val p = RaApi(fake.client).profile("lan", "KEY")
        assertEquals("lan", p.user)
        assertEquals(12, p.totalPoints)
        val req = fake.seen.single()
        assertTrue(req.url.encodedPath.endsWith("/API/API_GetUserProfile.php"))
        assertEquals("lan", req.url.queryParameter("u"))
        assertEquals("KEY", req.url.queryParameter("y"))
    }

    @Test fun unknownUserAndBadKeyAreAuthErrors() {
        val blank = Fake { _ -> (200 to "{}") }
        val e1 = assertThrows(RaAuthException::class.java) { runBlocking { RaApi(blank.client).profile("nobody", "k") } }
        assertEquals("Không tìm thấy người dùng RetroAchievements này", e1.message)
        val denied = Fake { _ -> (401 to "") }
        val e2 = assertThrows(RaAuthException::class.java) { runBlocking { RaApi(denied.client).profile("lan", "bad") } }
        assertEquals("Khóa web API hoặc tên người dùng không đúng", e2.message)
    }

    @Test fun serverErrorIsPlainIoExceptionNotAuth() {
        val fake = Fake { _ -> (500 to "") }
        val e = assertThrows(IOException::class.java) { runBlocking { RaApi(fake.client).profile("lan", "k") } }
        assertFalse(e is RaAuthException)
        assertEquals("RetroAchievements trả lỗi 500", e.message)
    }

    @Test fun recentGamesAndUnlocksSendCountAndDefaultWindow() = runBlocking {
        val fake = Fake { req ->
            if (req.url.encodedPath.endsWith("API_GetUserRecentlyPlayedGames.php"))
                (200 to """[{"GameID":5,"Title":"Mario","ConsoleName":"GBA","NumAchieved":2}]""")
            else (200 to "[]")
        }
        val api = RaApi(fake.client)
        val games = api.recentGames("lan", "k")
        assertEquals("Mario", games.single().title)
        assertEquals(2, games.single().numAchieved)
        assertEquals("20", fake.seen.first().url.queryParameter("c"))
        assertEquals(emptyList<RaUnlock>(), api.recentUnlocks("lan", "k"))
        // Mặc định 7 ngày = 10 080 phút.
        assertEquals("10080", fake.seen.last().url.queryParameter("m"))
    }

    @Test fun gameListSendsConsoleAndHashFlags() = runBlocking {
        val fake = Fake { _ -> (200 to """[{"Title":"Tetris","ID":9,"NumAchievements":3,"Hashes":["abc123"]}]""") }
        val list = RaApi(fake.client).gameList(4, "k")
        assertEquals("abc123", list.single().hashes.single())
        val req = fake.seen.single()
        assertEquals("4", req.url.queryParameter("i"))
        assertEquals("1", req.url.queryParameter("h"))
        assertEquals("1", req.url.queryParameter("f"))
    }

    @Test fun gameProgressDecodesAchievementMapInDisplayOrder() = runBlocking {
        val body = """{"ID":5,"Title":"G","NumAchievements":2,"Achievements":{
            "1":{"ID":1,"Title":"B","DisplayOrder":2,"DateEarned":"2026-01-01"},
            "2":{"ID":2,"Title":"A","DisplayOrder":1}}}"""
        val fake = Fake { _ -> (200 to body) }
        val g = RaApi(fake.client).gameProgress(5, "lan", "k")
        assertEquals(listOf("A", "B"), g.ordered.map { it.title })
        assertEquals(listOf(false, true), g.ordered.map { it.earned })
        assertEquals("5", fake.seen.single().url.queryParameter("g"))
    }

    @Test fun loginReturnsTokenAndPostsCredentialsInForm() = runBlocking {
        val fake = Fake { _ -> (200 to """{"Success":true,"Token":"tok123"}""") }
        assertEquals("tok123", RaApi(fake.client).loginWithPassword(" lan ", "pw"))
        val req = fake.seen.single()
        assertEquals("POST", req.method)
        assertTrue(req.url.encodedPath.endsWith("/dorequest.php"))
        val form = Buffer().also { req.body!!.writeTo(it) }.readUtf8()
        assertTrue(form.contains("r=login2"))
        assertTrue(form.contains("u=lan"))
        assertTrue(form.contains("p=pw"))
    }

    @Test fun loginFailureUsesServerMessageOrDefault() {
        val bad = Fake { _ -> (200 to """{"Success":false,"Error":"Mật khẩu sai"}""") }
        val e1 = assertThrows(RaAuthException::class.java) { runBlocking { RaApi(bad.client).loginWithPassword("lan", "x") } }
        assertEquals("Mật khẩu sai", e1.message)
        val silent = Fake { _ -> (200 to """{"Success":false}""") }
        val e2 = assertThrows(RaAuthException::class.java) { runBlocking { RaApi(silent.client).loginWithPassword("lan", "x") } }
        assertEquals("Sai tên hoặc mật khẩu", e2.message)
        val junk = Fake { _ -> (200 to "not json") }
        val e3 = assertThrows(IOException::class.java) { runBlocking { RaApi(junk.client).loginWithPassword("lan", "x") } }
        assertEquals("Phản hồi RetroAchievements không đọc được", e3.message)
    }
}
