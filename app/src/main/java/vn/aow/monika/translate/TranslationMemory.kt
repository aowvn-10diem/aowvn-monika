package vn.aow.monika.translate

import android.content.Context
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Bộ nhớ dịch trên máy: câu đã dịch rồi thì lấy lại ngay, không gọi API và không chạy mô hình lần nữa
 * (hộp thoại game lặp rất nhiều). Khóa theo (nhà cung cấp + ngôn ngữ nguồn + câu gốc); giữ tối đa [MAX] câu mới nhất.
 */
class TranslationMemory(private val file: File) {
    constructor(context: Context) : this(File(context.filesDir, "translate/memory.json"))

    private val ser = MapSerializer(String.serializer(), String.serializer())
    private val map = LinkedHashMap<String, String>().apply {
        runCatching { putAll(Json.decodeFromString(ser, file.readText())) }
    }

    private fun key(provider: String, src: String, text: String) = "$provider|$src|$text"

    @Synchronized fun get(provider: String, src: String, text: String): String? = map[key(provider, src, text)]

    @Synchronized fun put(provider: String, src: String, text: String, vi: String) {
        val k = key(provider, src, text)
        map.remove(k); map[k] = vi
        while (map.size > MAX) map.remove(map.keys.first())
        runCatching { file.parentFile?.mkdirs(); file.writeText(Json.encodeToString(ser, map)) }
    }

    @Synchronized fun clear() { map.clear(); runCatching { file.delete() } }
    val size: Int @Synchronized get() = map.size

    companion object { const val MAX = 3000 }
}
