package vn.aow.monika

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.achievements.RaAccount
import vn.aow.monika.achievements.RaApi
import vn.aow.monika.achievements.RaAuthException
import vn.aow.monika.achievements.RaHasher
import vn.aow.monika.achievements.raDateVn
import vn.aow.monika.apkinstall.repack.KeyWrap
import vn.aow.monika.ui.TestApp
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class RetroAchievementsTest {
    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    // Mẫu rút từ tài liệu https://api-docs.retroachievements.org (GetUserProfile, GetGameInfoAndUserProgress).
    private val profileJson = """{"User":"MaxMilyin","ULID":"00003EMFWR7XB8SDPEHB3K56ZQ","UserPic":"/UserPic/MaxMilyin.png","TotalPoints":399597,"TotalSoftcorePoints":0,"LastGameID":19504,"Motto":"x","Extra":1}"""
    private val gameJson = """{"ID":1,"Title":"Sonic the Hedgehog","ConsoleName":"Mega Drive","NumAchievements":23,"NumAwardedToUser":1,"UserCompletion":"4.35%",
        "Achievements":{"9":{"ID":9,"Title":"That Was Easy","Description":"Complete the first act","Points":3,"BadgeName":"250336","DisplayOrder":2,"type":"progression","DateEarned":"2016-03-12 17:47:29"},
        "8":{"ID":8,"Title":"First","Description":"d","Points":1,"BadgeName":"1","DisplayOrder":1}}}"""

    private fun api(code: Int, body: String) = RaApi(OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("x")
            .body(body.toResponseBody("application/json".toMediaType())).build()
    }.build(), base = "https://ra.test")

    @Test fun parsesProfileAndIgnoresUnknownKeys() = runBlocking {
        val p = api(200, profileJson).profile("MaxMilyin", "k")
        assertEquals("MaxMilyin", p.user); assertEquals(399597, p.totalPoints); assertEquals("00003EMFWR7XB8SDPEHB3K56ZQ", p.ulid)
    }

    @Test fun parsesGameProgressOrderedAndEarned() = runBlocking {
        val g = api(200, gameJson).gameProgress(1, "u", "k")
        assertEquals(listOf(8, 9), g.ordered.map { it.id })
        assertFalse(g.achievements["8"]!!.earned); assertTrue(g.achievements["9"]!!.earned)
        assertEquals("progression", g.achievements["9"]!!.type)
    }

    @Test fun badUnauthorizedThrowsAuthException() {
        try { runBlocking { api(401, "").profile("u", "bad") }; fail() } catch (_: RaAuthException) {}
    }

    @Test fun signInStoresAndReloadsKeyEncrypted() = runBlocking {
        val plainWrap = object : KeyWrap { // mã hóa giả: đảo byte, để chắc chắn khóa không nằm nguyên văn trong file lưu
            override fun wrap(plain: ByteArray) = plain.reversedArray()
            override fun unwrap(cipher: ByteArray) = cipher.reversedArray()
        }
        val acc = RaAccount(app, api(200, profileJson), plainWrap)
        acc.signIn("MaxMilyin", "SECRETKEY123")
        val raw = app.getSharedPreferences("ra", 0).all.values.joinToString()
        assertFalse(raw.contains("SECRETKEY123"))
        val again = RaAccount(app, api(200, profileJson), plainWrap)
        assertEquals("SECRETKEY123", again.creds.value!!.key)
        assertEquals("00003EMFWR7XB8SDPEHB3K56ZQ", again.idForApi)
        again.signOut(); assertNull(again.creds.value)
    }

    private fun md5(b: ByteArray) = MessageDigest.getInstance("MD5").digest(b).joinToString("") { "%02x".format(it) }
    private fun tmp(bytes: ByteArray, name: String) = File.createTempFile("rahash", name).apply { writeBytes(bytes); deleteOnExit() }

    @Test fun hashGbaIsWholeFile() {
        val data = ByteArray(4096) { (it * 7).toByte() }
        assertEquals(md5(data), RaHasher.hash(tmp(data, ".gba"), 5))
    }

    @Test fun hashNesSkipsHeader() {
        val body = ByteArray(2048) { it.toByte() }
        val withHeader = byteArrayOf(0x4E, 0x45, 0x53, 0x1A) + ByteArray(12) + body
        assertEquals(md5(body), RaHasher.hash(tmp(withHeader, ".nes"), 7))
        assertEquals(md5(body), RaHasher.hash(tmp(body, ".nes"), 7)) // không header → cả file
    }

    @Test fun hashSnesSkips512ByteHeaderOnly() {
        val body = ByteArray(0x2000 * 2) { (it % 251).toByte() }
        val headered = ByteArray(512) + body
        assertEquals(md5(body), RaHasher.hash(tmp(headered, ".sfc"), 3))
        assertEquals(md5(body), RaHasher.hash(tmp(body, ".sfc"), 3))
    }

    @Test fun consoleMappingFollowsRaIds() {
        assertEquals(5, RaHasher.consoleFor("gba", File("a.gba")))
        assertEquals(4, RaHasher.consoleFor("gbc", File("a.gb"))); assertEquals(6, RaHasher.consoleFor("gbc", File("a.gbc")))
        assertEquals(7, RaHasher.consoleFor("nes", File("a.nes"))); assertNull(RaHasher.consoleFor("nes", File("a.fds")))
        assertNull(RaHasher.consoleFor("nds", File("a.nds"))); assertNull(RaHasher.consoleFor("ps1", File("a.cue")))
    }

    @Test fun dateShownInVietnamTime() {
        assertEquals("13/03/2016 00:47", raDateVn("2016-03-12 17:47:29")) // UTC 17:47 → GMT+7 00:47 hôm sau
        assertNotNull(raDateVn("garbage"))
    }
}
