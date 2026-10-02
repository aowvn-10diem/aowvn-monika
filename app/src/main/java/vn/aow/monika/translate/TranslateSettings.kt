package vn.aow.monika.translate

import android.content.Context
import android.util.Base64
import vn.aow.monika.apkinstall.repack.AndroidKeystoreWrap
import vn.aow.monika.apkinstall.repack.KeyWrap

/**
 * Cài đặt dịch màn hình. Khóa API do người dùng tự nhập, mã hóa bằng Android Keystore, chỉ lưu trên máy này.
 * provider: "google" (Google Cloud Translation) | "ai" (API kiểu OpenAI: OpenAI, Gemini, OpenRouter…)
 */
class TranslateSettings(context: Context, private val wrap: KeyWrap = AndroidKeystoreWrap) {
    private val sp = context.getSharedPreferences("translate", Context.MODE_PRIVATE)

    var provider: String
        get() = (sp.getString("provider", "ai") ?: "ai").let { if (it == "mlkit") "ai" else it }
        set(v) = sp.edit().putString("provider", v).apply()

    /** Ngôn ngữ chữ trong game: "en" | "ja" (OCR của ML Kit có Latin + Nhật). */
    var sourceLang: String
        get() = sp.getString("src", "en") ?: "en"
        set(v) = sp.edit().putString("src", v).apply()

    var baseUrl: String
        get() = sp.getString("base", DEFAULT_BASE) ?: DEFAULT_BASE
        set(v) = sp.edit().putString("base", v.trim().trimEnd('/')).apply()

    var model: String
        get() = sp.getString("model", "gpt-4o-mini") ?: "gpt-4o-mini"
        set(v) = sp.edit().putString("model", v.trim()).apply()

    /** AI nhìn thẳng ảnh chụp (bỏ OCR): đọc chữ pixel tốt hơn, nhưng gửi cả ảnh game cho nhà cung cấp AI. */
    var vision: Boolean
        get() = sp.getBoolean("vision", false)
        set(v) = sp.edit().putBoolean("vision", v).apply()

    var apiKey: String
        get() = sp.getString("key", null)?.let { runCatching { String(wrap.unwrap(Base64.decode(it, Base64.NO_WRAP))) }.getOrNull() }.orEmpty()
        set(v) = sp.edit().putString("key", if (v.isBlank()) null else Base64.encodeToString(wrap.wrap(v.trim().toByteArray()), Base64.NO_WRAP)).apply()

    companion object { const val DEFAULT_BASE = "https://api.openai.com/v1" }
}
