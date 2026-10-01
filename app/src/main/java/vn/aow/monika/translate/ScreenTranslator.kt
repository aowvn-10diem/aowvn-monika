package vn.aow.monika.translate

import android.graphics.Bitmap
import okhttp3.OkHttpClient

/** Điều phối: chụp khung hình → (OCR) → dịch bằng nhà cung cấp người dùng chọn → danh sách (gốc, Việt). */
class ScreenTranslator(http: OkHttpClient, private val s: TranslateSettings, private val memory: TranslationMemory? = null) {
    private val google = GoogleTranslator(http)
    private val ai = AiTranslator(http)

    suspend fun translateFrame(bmp: Bitmap): List<TLine> {
        if (s.provider == "ai" && s.vision) {
            requireKey()
            return ai.translateImage(bmp, s.sourceLang, s.baseUrl, s.apiKey, s.model)
        }
        val lines = MlKit.read(bmp, s.sourceLang)
        if (lines.isEmpty()) return emptyList()
        return lines.zip(translateTexts(lines)) { o, v -> TLine(o, v) }
    }

    /** Câu đã có trong bộ nhớ dịch thì dùng lại; chỉ gửi/dịch phần còn thiếu. */
    suspend fun translateTexts(lines: List<String>): List<String> {
        val p = s.provider; val src = s.sourceLang
        val known = lines.map { memory?.get(p, src, it) }
        val missing = lines.filterIndexed { i, _ -> known[i] == null }.distinct()
        val fresh = if (missing.isEmpty()) emptyMap() else missing.zip(callProvider(missing)).toMap()
        fresh.forEach { (o, v) -> memory?.put(p, src, o, v) }
        return lines.mapIndexed { i, o -> known[i] ?: fresh[o].orEmpty() }
    }

    private suspend fun callProvider(lines: List<String>): List<String> = when (s.provider) {
        "google" -> { requireKey(); google.translate(lines, s.sourceLang, s.apiKey) }
        "ai" -> { requireKey(); ai.translate(lines, s.sourceLang, s.baseUrl, s.apiKey, s.model) }
        else -> MlKit.translate(lines, s.sourceLang)
    }

    private fun requireKey() { if (s.apiKey.isBlank()) throw TranslateException("Chưa nhập khóa API trong Cài đặt → Dịch màn hình") }
}
