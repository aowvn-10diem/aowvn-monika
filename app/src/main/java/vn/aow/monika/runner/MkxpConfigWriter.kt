package vn.aow.monika.runner

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File

/**
 * Sinh `mkxp.json` cho game RPG Maker XP/VX/Ace (mkxp-z). Chỉ đặt khóa cần thiết, GIỮ nguyên mọi khóa khác của game
 * (kể cả khóa lạ). Tên khóa theo `mkxp.json` mẫu của mkxp-z: rgssVersion, RTP, fontSub, pathCache.
 */
object MkxpConfigWriter {
    private val lenient = Json { isLenient = true; ignoreUnknownKeys = true; allowTrailingComma = true }
    private val pretty = Json { prettyPrint = true }

    /** Đoán phiên bản RGSS từ tệp có trong thư mục game (0 = không đoán được). Ưu tiên bản mới (Ace > VX > XP). */
    fun detectRgss(gameDir: File): Int {
        val names = (gameDir.list().orEmpty().toList() + File(gameDir, "Data").list().orEmpty()).map { it.lowercase() }
        return when {
            "game.rgss3a" in names || names.any { it.endsWith(".rvdata2") } -> 3
            "game.rgss2a" in names || names.any { it.endsWith(".rvdata") } -> 2
            "game.rgssad" in names || names.any { it.endsWith(".rxdata") } -> 1
            else -> 0
        }
    }

    /** Tệp mkxp.json của game có chú thích kiểu `//` nguyên dòng → bỏ chú thích rồi đọc. Hỏng → coi như rỗng. */
    internal fun parseExisting(text: String?): JsonObject {
        if (text.isNullOrBlank()) return JsonObject(emptyMap())
        val stripped = text.lineSequence().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
        return runCatching { lenient.parseToJsonElement(stripped) as? JsonObject }.getOrNull() ?: JsonObject(emptyMap())
    }

    /**
     * @param existing nội dung mkxp.json có sẵn của game (nếu có)
     * @param rgss phiên bản đoán được (0 = để mkxp-z tự đoán); khóa `rgssVersion` có sẵn khác 0 của game được tôn trọng
     * @param rtp các thư mục RTP người chơi đã thêm (gộp, không trùng)
     * @param fontSub luật thay phông, dạng "Gốc>Thay" (vd. "MS Gothic>Noto Sans")
     */
    fun build(existing: String?, rgss: Int, rtp: List<String>, fontSub: List<String> = emptyList()): String {
        val old = parseExisting(existing)
        val out = LinkedHashMap<String, JsonElement>(old)
        val oldVersion = old["rgssVersion"]?.jsonPrimitive?.runCatching { int }?.getOrNull() ?: 0
        if (oldVersion == 0 && rgss in 1..3) out["rgssVersion"] = JsonPrimitive(rgss)
        out["RTP"] = JsonArray(union(old["RTP"], rtp).map(::JsonPrimitive))
        if (fontSub.isNotEmpty()) out["fontSub"] = JsonArray(union(old["fontSub"], fontSub).map(::JsonPrimitive))
        if ("pathCache" !in out) out["pathCache"] = JsonPrimitive(true)
        return pretty.encodeToString(JsonObject.serializer(), JsonObject(out))
    }

    private fun union(old: JsonElement?, extra: List<String>): List<String> =
        ((old as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content } ?: emptyList())
            .plus(extra).distinct()

    /** Ghi vào `<gameDir>/mkxp.json`. Trả đường dẫn tệp. */
    fun write(gameDir: File, rtp: List<String>, fontSub: List<String> = emptyList()): File {
        val f = File(gameDir, "mkxp.json")
        f.writeText(build(f.takeIf { it.isFile }?.readText(), detectRgss(gameDir), rtp, fontSub))
        return f
    }
}
