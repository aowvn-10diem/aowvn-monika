package vn.aow.monika

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import vn.aow.monika.translate.AiTranslator
import vn.aow.monika.translate.GoogleTranslator
import vn.aow.monika.translate.TranslateException
import java.io.File

class ScreenTranslateTest {
    private fun http(code: Int, body: String, seen: MutableList<okhttp3.Request> = mutableListOf()) = OkHttpClient.Builder().addInterceptor { chain ->
        seen += chain.request()
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("x").body(body.toResponseBody("application/json".toMediaType())).build()
    }.build()

    @Test fun googleParsesTranslationsInOrder() = runBlocking {
        val seen = mutableListOf<okhttp3.Request>()
        val g = GoogleTranslator(http(200, """{"data":{"translations":[{"translatedText":"Xin chào"},{"translatedText":"Tạm biệt"}]}}""", seen), "https://g.test/v2")
        assertEquals(listOf("Xin chào", "Tạm biệt"), g.translate(listOf("Hello", "Bye"), "en", "KEY"))
        assertTrue(seen.single().url.toString().startsWith("https://g.test/v2"))
    }

    @Test fun googleErrorShowsServerMessage() {
        val g = GoogleTranslator(http(400, """{"error":{"message":"API key not valid"}}"""), "https://g.test/v2")
        try { runBlocking { g.translate(listOf("a"), "en", "bad") }; fail() } catch (e: TranslateException) { assertTrue(e.message!!.contains("API key not valid")) }
    }

    @Test fun aiSendsBearerAndParsesFencedJsonArray() = runBlocking {
        val seen = mutableListOf<okhttp3.Request>()
        val reply = "```json\n[\"Chào anh hùng\", \"Cuộc hành trình bắt đầu\"]\n```"
        val content = reply.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        val ai = AiTranslator(http(200, """{"choices":[{"message":{"content":"$content"}}]}""", seen))
        val out = ai.translate(listOf("Hello hero", "The journey begins"), "en", "https://api.test/v1/", "SK", "m")
        assertEquals(listOf("Chào anh hùng", "Cuộc hành trình bắt đầu"), out)
        assertEquals("Bearer SK", seen.single().header("Authorization"))
        assertEquals("https://api.test/v1/chat/completions", seen.single().url.toString())
    }

    @Test fun aiWrongCountIsRejected() {
        val ai = AiTranslator(http(200, """{"choices":[{"message":{"content":"[\"một\"]"}}]}"""))
        try { runBlocking { ai.translate(listOf("a", "b"), "en", "https://x/v1", "k", "m") }; fail() } catch (_: TranslateException) {}
    }

    @Test fun aiVisionPairsParsed() {
        val ai = AiTranslator(http(200, "{}"))
        val pairs = ai.parsePairs("""Đây: [{"o":"Attack","vi":"Tấn công"},{"o":"HP 10","vi":""}]""")
        assertEquals(1, pairs.size); assertEquals("Tấn công", pairs[0].vi)
    }

    @Test fun aiUnauthorizedMessage() {
        val ai = AiTranslator(http(401, "{}"))
        try { runBlocking { ai.translate(listOf("a"), "en", "https://x/v1", "k", "m") }; fail() } catch (e: TranslateException) { assertTrue(e.message!!.contains("Khóa API")) }
    }
}
