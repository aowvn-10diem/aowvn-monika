package vn.aow.monika.translate

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

class TranslatorsParseTest {
    private val google = GoogleTranslator(OkHttpClient())
    private val ai = AiTranslator(OkHttpClient())

    /** Máy chủ giả trong bộ nhớ: không mở kết nối mạng nào. */
    private fun http(code: Int, body: String) = OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("x").body(body.toResponseBody("application/json".toMediaType())).build()
    }.build()

    @Test fun emptyInputReturnsWithoutCallingNetwork() = runBlocking {
        assertEquals(emptyList<String>(), GoogleTranslator(http(500, "{}")).translate(emptyList(), "en", "KEY"))
        assertEquals(emptyList<String>(), AiTranslator(http(500, "{}")).translate(emptyList(), "en", "https://x/v1", "k", "m"))
    }

    @Test fun googleErrorWithoutMessageReportsHttpCode() {
        try {
            runBlocking { GoogleTranslator(http(500, "{}"), "https://g.test/v2").translate(listOf("a"), "en", "k") }
            fail()
        } catch (e: TranslateException) { assertEquals("Google Dịch trả lỗi 500", e.message) }
    }

    @Test fun aiErrorWithoutMessageReportsHttpCode() {
        try {
            runBlocking { AiTranslator(http(500, "{}")).translate(listOf("a"), "en", "https://x/v1", "k", "m") }
            fail()
        } catch (e: TranslateException) { assertEquals("API AI trả lỗi 500", e.message) }
    }

    @Test fun googleMissingTextBecomesEmptyString() {
        assertEquals(listOf("Một", ""), google.parse("""{"data":{"translations":[{"translatedText":"Một"},{}]}}""", 2))
    }

    @Test fun googleRejectsUnexpectedShape() {
        try { google.parse("""{"data":{}}""", 1); fail() } catch (e: TranslateException) { assertEquals("Google Dịch trả về dữ liệu lạ", e.message) }
    }

    @Test fun googleRejectsMissingLines() {
        try { google.parse("""{"data":{"translations":[{"translatedText":"a"}]}}""", 2); fail() } catch (e: TranslateException) { assertEquals("Google Dịch trả thiếu đoạn", e.message) }
    }

    @Test fun aiReadsFirstChoiceOnly() {
        assertEquals("hi", ai.extractContent("""{"choices":[{"message":{"content":"hi"}},{"message":{"content":"x"}}]}"""))
    }

    @Test fun aiRejectsResponseWithoutChoices() {
        try { ai.extractContent("""{"choices":[]}"""); fail() } catch (e: TranslateException) { assertEquals("API AI trả về dữ liệu lạ", e.message) }
    }

    @Test fun aiFindsArrayInsideProse() {
        assertEquals(2, ai.jsonArrayIn("Kết quả: [\"a\", \"b\"] (hết)").size)
    }

    @Test fun aiTextWithoutArrayIsRejected() {
        try { ai.jsonArrayIn("không có mảng"); fail() } catch (e: TranslateException) { assertEquals("AI không trả về danh sách dịch", e.message) }
    }

    @Test fun aiNonStringItemsBecomeEmptyStrings() {
        assertEquals(listOf("", "một"), ai.parseStrings("""[{"x":1}, "một"]""", 2))
    }

    @Test fun visionPairsSkipNonObjectsAndBlankTranslations() {
        val pairs = ai.parsePairs("""[ "rác", {"vi":"Máu"}, {"o":"HP","vi":"  "} ]""")
        assertEquals(listOf(TLine("", "Máu")), pairs)
    }

    @Test fun systemPromptNamesSourceLanguageAndOutputShape() {
        assertTrue(AiTranslator.systemPrompt("ja", vision = false).contains("tiếng Nhật"))
        assertTrue(AiTranslator.systemPrompt("en", vision = false).contains("tiếng Anh"))
        assertFalse(AiTranslator.systemPrompt("en", vision = false).contains("Ảnh là màn hình"))
        val vision = AiTranslator.systemPrompt("en", vision = true)
        assertTrue(vision.contains("Ảnh là màn hình game"))
        assertTrue(vision.contains("""{"o":"bản gốc","vi":"bản tiếng Việt"}"""))
    }
}
