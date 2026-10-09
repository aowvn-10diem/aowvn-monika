package vn.aow.monika.account

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.config.ConfigRepository
import vn.aow.monika.ui.TestApp
import java.io.IOException
import java.time.ZoneId

/** Đăng nhập, phiên và API tài khoản AowVN, không mạng: máy chủ giả trong bộ nhớ ghi lại mọi yêu cầu. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class AccountApiTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val vietnam = ZoneId.of("Asia/Ho_Chi_Minh")
    private val day = 24L * 60 * 60 * 1000
    private val invalid = "Phiên đăng nhập không hợp lệ. Hãy bấm Đăng nhập lại trong app."

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

    private fun rig(http: OkHttpClient): Pair<Account, AowApi> {
        val config = ConfigRepository(app, OkHttpClient())
        val acc = Account(app, http, config)
        return acc to AowApi(http, config, acc)
    }

    /** Giả lập bước mở trang đăng nhập: ghi state và thời điểm như [Account.startLogin]. */
    private fun arm(state: String = "st", at: Long = System.currentTimeMillis()) {
        app.getSharedPreferences("account", Context.MODE_PRIVATE).edit()
            .putString("pending_state", state).putLong("pending_at", at).apply()
    }

    private fun signIn(acc: Account) {
        arm()
        assertNull(acc.complete("state=st&uid=u1&id_token=tok&refresh_token=ref&name=Lan&email=lan%40example.com"))
    }

    private fun storeSession(s: Session) {
        app.getSharedPreferences("account", Context.MODE_PRIVATE).edit()
            .putString("session", Json.encodeToString(Session.serializer(), s)).apply()
    }

    // ---------- Đăng nhập và phiên ----------

    @Test fun completeRejectsCallbackWithoutLoginStarted() {
        assertEquals(invalid, rig(OkHttpClient()).first.complete("state=st&uid=u1&id_token=t&refresh_token=r"))
    }

    @Test fun wrongStateIsRejectedAndForgotten() {
        val acc = rig(OkHttpClient()).first
        arm()
        assertEquals(invalid, acc.complete("state=khac&uid=u1&id_token=t&refresh_token=r"))
        // Lượt thử đã bị xóa: state đúng gửi sau đó cũng không được nhận.
        assertEquals(invalid, acc.complete("state=st&uid=u1&id_token=t&refresh_token=r"))
        assertNull(acc.session.value)
    }

    @Test fun expiredLoginIsRejected() {
        val acc = rig(OkHttpClient()).first
        arm(at = System.currentTimeMillis() - 31 * 60_000)
        assertEquals("Phiên đăng nhập đã quá hạn. Hãy thử lại.", acc.complete("state=st&uid=u1&id_token=t&refresh_token=r"))
        assertNull(acc.session.value)
    }

    @Test fun missingFieldsAreRejected() {
        val acc = rig(OkHttpClient()).first
        arm()
        assertEquals("Thiếu thông tin đăng nhập. Hãy thử lại.", acc.complete("state=st&uid=u1&id_token=t"))
    }

    @Test fun successfulLoginSurvivesRestartUntilSignOut() {
        val acc = rig(OkHttpClient()).first
        arm()
        assertNull(acc.complete("state=st&uid=u1&id_token=tok&refresh_token=ref&name=Lan&email=lan%40example.com&photo=p"))
        val s = acc.session.value!!
        assertEquals("u1", s.uid)
        assertEquals("lan@example.com", s.email)
        assertTrue(s.idTokenExp > System.currentTimeMillis() + 50 * 60_000)
        // Tạo lại Account (như khi mở lại app) vẫn còn đăng nhập.
        assertEquals("u1", rig(OkHttpClient()).first.session.value?.uid)
        acc.signOut()
        assertNull(rig(OkHttpClient()).first.session.value)
    }

    // ---------- Token ----------

    @Test fun signedOutHasNoToken() = runBlocking {
        assertNull(rig(OkHttpClient()).first.idToken())
    }

    @Test fun freshTokenIsUsedWithoutNetwork() = runBlocking {
        val fake = Fake { error("không được gọi mạng") }
        val acc = rig(fake.client).first
        signIn(acc)
        assertEquals("tok", acc.idToken())
        assertTrue(fake.seen.isEmpty())
    }

    @Test fun expiredTokenIsRefreshedAndSaved() = runBlocking {
        storeSession(Session("u1", "Lan", "lan@example.com", "p", "old", 0L, "ref"))
        val fake = Fake { _ -> (200 to """{"id_token":"new","refresh_token":"ref2","expires_in":"3600"}""") }
        val acc = rig(fake.client).first
        assertEquals("new", acc.idToken())
        assertEquals("ref2", rig(OkHttpClient()).first.session.value?.refreshToken)
        assertEquals("securetoken.googleapis.com", fake.seen.single().url.host)
    }

    @Test fun rejectedRefreshSignsOut() = runBlocking {
        storeSession(Session("u1", "Lan", "lan@example.com", "p", "old", 0L, "ref"))
        val acc = rig(Fake { _ -> (400 to "{}") }.client).first
        assertNull(acc.idToken())
        assertNull(acc.session.value)
    }

    @Test fun networkErrorKeepsOldToken() = runBlocking {
        storeSession(Session("u1", "Lan", "lan@example.com", "p", "old", 0L, "ref"))
        val acc = rig(Fake { throw IOException("mất mạng") }.client).first
        assertEquals("old", acc.idToken())
        assertEquals("u1", acc.session.value?.uid)
    }

    // ---------- Điểm danh ----------

    @Test fun checkinStepsFollowStreakRulesAndAwardAtFourteen() {
        val now = System.currentTimeMillis()
        val today = Profile(5, 3, 2, now)
        assertEquals(CheckinResult(today, pointAwarded = false, already = true), AowApi.nextCheckin(today, now, vietnam))
        // Bỏ lỡ một ngày: chuỗi và chu kỳ về 1.
        assertEquals(Profile(5, 1, 1, now), AowApi.nextCheckin(Profile(5, 9, 7, now - 2 * day), now, vietnam).profile)
        // Liên tiếp: chuỗi và chu kỳ +1.
        assertEquals(Profile(5, 10, 8, now), AowApi.nextCheckin(Profile(5, 9, 7, now - day), now, vietnam).profile)
        // Đủ 14 ngày: +1 điểm tích lũy, chu kỳ về 0.
        val full = AowApi.nextCheckin(Profile(5, 13, 13, now - day), now, vietnam)
        assertEquals(Profile(6, 14, 0, now), full.profile)
        assertTrue(full.pointAwarded)
        // Chưa điểm danh lần nào.
        assertEquals(Profile(0, 1, 1, now), AowApi.nextCheckin(Profile(0, 0, 0, 0), now, vietnam).profile)
    }

    @Test fun checkinReadsProfileThenWritesPatch() = runBlocking {
        val yesterday = System.currentTimeMillis() - day
        val fake = Fake { req ->
            if (req.method == "GET") (200 to """{"points":3,"checkin":{"streak":2,"progress":5,"lastCheckinTime":$yesterday}}""")
            else (200 to "null")
        }
        val (acc, api) = rig(fake.client)
        signIn(acc)
        val r = api.checkin(vietnam)
        assertEquals(3L, r.profile.points)
        assertEquals(3, r.profile.streak)
        assertEquals(6, r.profile.progress)
        assertFalse(r.pointAwarded)
        assertFalse(r.already)
        assertEquals(3, api.cachedProfile()?.streak)
        assertEquals(listOf("GET", "PATCH"), fake.seen.map { it.method })
        assertEquals("tok", fake.seen.last().url.queryParameter("auth"))
    }

    @Test fun checkinTwiceSameDayAwardsNothing() = runBlocking {
        val now = System.currentTimeMillis()
        val fake = Fake { _ -> (200 to """{"points":3,"checkin":{"streak":4,"progress":2,"lastCheckinTime":$now}}""") }
        val (acc, api) = rig(fake.client)
        signIn(acc)
        val r = api.checkin(vietnam)
        assertTrue(r.already)
        assertFalse(r.pointAwarded)
        assertEquals(listOf("GET"), fake.seen.map { it.method })
    }

    // ---------- Đánh giá bản dịch ----------

    @Test fun reviewsSortNewestFirstAndFillDefaults() = runBlocking {
        val body = """{"a":{"uid":"u1","displayName":"","recommended":false,"content":"hay","createdAt":1,"updatedAt":5},
            "b":{"uid":"u2","displayName":"Minh","photoURL":"p","content":"ok","teamName":"T","createdAt":2,"updatedAt":9}}"""
        val fake = Fake { _ -> (200 to body) }
        val rs = rig(fake.client).second.reviews("123")
        assertEquals(listOf("u2", "u1"), rs.map { it.uid })
        assertEquals("Game thủ AowVN", rs[1].name)
        assertFalse(rs[1].recommended)
        assertTrue(rs[0].recommended)
        assertTrue(fake.seen.single().url.encodedPath.endsWith("/post_reviews/123.json"))
    }

    @Test fun emptyReviewsGiveEmptyList() = runBlocking {
        assertEquals(emptyList<Review>(), rig(Fake { _ -> (200 to "null") }.client).second.reviews("1"))
    }

    @Test fun serverErrorIsReportedWithCode() {
        val e = assertThrows(IllegalStateException::class.java) {
            runBlocking { rig(Fake { _ -> (500 to "{}") }.client).second.reviews("1") }
        }
        assertEquals("Lỗi máy chủ (HTTP 500)", e.message)
    }

    @Test fun permissionDeniedIsReported() {
        val e = assertThrows(IllegalStateException::class.java) {
            runBlocking { rig(Fake { _ -> (200 to """{"error":"Permission denied"}""") }.client).second.reviews("1") }
        }
        assertEquals("Máy chủ từ chối (không có quyền)", e.message)
    }

    @Test fun saveAndDeleteReviewWriteToSignedInPath() = runBlocking {
        val fake = Fake { _ -> (200 to "null") }
        val (acc, api) = rig(fake.client)
        signIn(acc)
        api.saveReview("123", recommended = false, content = "  tốt  ", createdAt = 7L)
        val put = fake.seen.single()
        assertEquals("PUT", put.method)
        assertTrue(put.url.encodedPath.endsWith("/post_reviews/123/u1.json"))
        val body = Buffer().also { put.body!!.writeTo(it) }.readUtf8()
        assertTrue(body.contains("\"content\":\"tốt\""))
        assertTrue(body.contains("\"recommended\":false"))
        assertTrue(body.contains("\"createdAt\":7"))
        api.deleteReview("123")
        assertEquals("DELETE", fake.seen.last().method)
        assertEquals("tok", fake.seen.last().url.queryParameter("auth"))
    }

    @Test fun accountCallsNeedSignIn() {
        val api = rig(OkHttpClient()).second
        assertThrows(AowApi.NotSignedIn::class.java) { runBlocking { api.profile() } }
        assertThrows(AowApi.NotSignedIn::class.java) { runBlocking { api.checkin(vietnam) } }
        assertThrows(AowApi.NotSignedIn::class.java) { runBlocking { api.saveReview("1", true, "x", null) } }
        assertThrows(AowApi.NotSignedIn::class.java) { runBlocking { api.deleteReview("1") } }
    }

    // ---------- Donate ----------

    @Test fun donateMemoAndQrCarryGameAndAmount() {
        val api = rig(OkHttpClient()).second
        val d = ConfigRepository(app, OkHttpClient()).current.account.donate
        assertEquals("${d.memoPrefix} DONATE GUEST g1", api.donateMemo("g1"))
        val qr = api.donateQr("g1", 50_000)
        assertTrue(qr.startsWith("https://img.vietqr.io/image/${d.bankId}-${d.accountNo}-${d.template}.png?amount=50000&addInfo="))
        assertTrue(qr.contains("addInfo=" + Uri.encode(api.donateMemo("g1"))))
        assertTrue(qr.endsWith("&accountName=" + Uri.encode(d.accountName)))
    }
}
