package vn.aow.monika.translate

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException

/** 1 đoạn chữ trên màn game: bản gốc + bản tiếng Việt. */
data class TLine(val original: String, val vi: String)

class TranslateException(message: String) : IOException(message)

private val json = Json { ignoreUnknownKeys = true }
private val JSON_MEDIA = "application/json".toMediaType()

/** Google Cloud Translation v2 (khóa API của người dùng). */
class GoogleTranslator(private val http: OkHttpClient, private val endpoint: String = "https://translation.googleapis.com/language/translate/v2") {
    suspend fun translate(lines: List<String>, src: String, key: String): List<String> = withContext(Dispatchers.IO) {
        if (lines.isEmpty()) return@withContext emptyList()
        val body = FormBody.Builder().apply {
            lines.forEach { add("q", it) }
            add("source", src); add("target", "vi"); add("format", "text"); add("key", key)
        }.build()
        http.newCall(Request.Builder().url(endpoint).post(body).build()).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw TranslateException(errorOf(text) ?: "Google Dịch trả lỗi ${r.code}")
            parse(text, lines.size)
        }
    }

    internal fun parse(text: String, expected: Int): List<String> {
        val arr = json.parseToJsonElement(text).jsonObject["data"]?.jsonObject?.get("translations")?.jsonArray
            ?: throw TranslateException("Google Dịch trả về dữ liệu lạ")
        val out = arr.map { it.jsonObject["translatedText"]?.jsonPrimitive?.contentOrNull.orEmpty() }
        if (out.size != expected) throw TranslateException("Google Dịch trả thiếu đoạn")
        return out
    }

    private fun errorOf(text: String) = runCatching { json.parseToJsonElement(text).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content }.getOrNull()
}

/** API kiểu OpenAI (`/chat/completions`): OpenAI, Gemini (đường dẫn tương thích OpenAI), OpenRouter… do người dùng nhập địa chỉ + khóa + mô hình. */
class AiTranslator(private val http: OkHttpClient) {
    suspend fun translate(lines: List<String>, src: String, base: String, key: String, model: String): List<String> {
        if (lines.isEmpty()) return emptyList()
        val user = buildJsonArray { lines.forEach { add(JsonPrimitive(it)) } }.toString()
        val out = parseStrings(chat(base, key, model, systemPrompt(src, vision = false), JsonPrimitive(user)), lines.size)
        return out
    }

    /** AI đọc ảnh trực tiếp: trả về các cặp (gốc, tiếng Việt). */
    suspend fun translateImage(image: Bitmap, src: String, base: String, key: String, model: String): List<TLine> {
        val content = buildJsonArray {
            add(buildJsonObject { put("type", "text"); put("text", "Dịch toàn bộ chữ trong ảnh game này.") })
            add(buildJsonObject { put("type", "image_url"); put("image_url", buildJsonObject { put("url", "data:image/jpeg;base64," + jpegBase64(image)) }) })
        }
        return parsePairs(chat(base, key, model, systemPrompt(src, vision = true), content))
    }

    private suspend fun chat(base: String, key: String, model: String, system: String, userContent: JsonElement): String = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("model", model)
            put("temperature", 0.2)
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", system) })
                add(buildJsonObject { put("role", "user"); put("content", userContent) })
            })
        }.toString().toRequestBody(JSON_MEDIA)
        val req = Request.Builder().url("${base.trimEnd('/')}/chat/completions").header("Authorization", "Bearer $key").post(body).build()
        http.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (r.code == 401 || r.code == 403) throw TranslateException("Khóa API không đúng hoặc không có quyền")
            if (!r.isSuccessful) throw TranslateException(errorOf(text) ?: "API AI trả lỗi ${r.code}")
            extractContent(text)
        }
    }

    internal fun extractContent(response: String): String =
        json.parseToJsonElement(response).jsonObject["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
            ?: throw TranslateException("API AI trả về dữ liệu lạ")

    /** Lấy mảng JSON đầu tiên trong câu trả lời (model hay bọc trong ```json). */
    internal fun jsonArrayIn(text: String): JsonArray {
        val a = text.indexOf('['); val b = text.lastIndexOf(']')
        if (a < 0 || b <= a) throw TranslateException("AI không trả về danh sách dịch")
        return json.parseToJsonElement(text.substring(a, b + 1)).jsonArray
    }

    internal fun parseStrings(text: String, expected: Int): List<String> {
        val out = jsonArrayIn(text).map { (it as? JsonPrimitive)?.contentOrNull.orEmpty() }
        if (out.size != expected) throw TranslateException("AI trả ${out.size} đoạn, cần $expected")
        return out
    }

    internal fun parsePairs(text: String): List<TLine> = jsonArrayIn(text).mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        val vi = o["vi"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        TLine(o["o"]?.jsonPrimitive?.contentOrNull.orEmpty(), vi)
    }

    private fun errorOf(text: String) = runCatching { json.parseToJsonElement(text).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content }.getOrNull()

    private fun jpegBase64(src: Bitmap): String {
        val max = 1024f
        val s = minOf(1f, max / maxOf(src.width, src.height))
        val b = if (s < 1f) Bitmap.createScaledBitmap(src, (src.width * s).toInt(), (src.height * s).toInt(), true) else src
        val bos = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.JPEG, 85, bos)
        return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }

    companion object {
        fun systemPrompt(src: String, vision: Boolean): String {
            val lang = if (src == "ja") "tiếng Nhật" else "tiếng Anh"
            val base = "Bạn là người dịch game. Dịch sang tiếng Việt tự nhiên, ngắn gọn như trong game, giữ nguyên tên riêng, số, ký hiệu điều khiển và mã trong ngoặc."
            return if (vision) "$base Ảnh là màn hình game (chữ chủ yếu bằng $lang). Đọc mọi đoạn chữ có nghĩa (bỏ chỉ số, thanh máu). Chỉ trả về MỘT mảng JSON, mỗi phần tử {\"o\":\"bản gốc\",\"vi\":\"bản tiếng Việt\"}, không giải thích."
            else "$base Chữ gốc bằng $lang. Đầu vào là mảng JSON các đoạn. Chỉ trả về MỘT mảng JSON các chuỗi tiếng Việt cùng số phần tử, cùng thứ tự, không giải thích."
        }
    }
}
