package vn.aow.monika.config

import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Kiểm dữ liệu gốc trước parse: coerceInputValues không được làm mất giá trị sai kiểu. */
internal object ConfigValidation {
    private val runners = setOf("libretro", "web", "apk", "external", "j2me")
    private val hash = Regex("[0-9a-fA-F]{64}")
    private val urlFields = setOf("endpoint", "systemfiles", "script", "facebookgroup", "discord")

    fun parse(text: String, minimumVersion: Int): MonikaConfig {
        val root = ConfigRepository.json.parseToJsonElement(text)
        require(root is JsonObject) { "Config phải là object" }
        visit(root, "config")
        val cfg = ConfigRepository.parse(text)
        require(cfg.configVersion >= minimumVersion) { "configVersion thấp hơn bản đang dùng" }
        return cfg
    }

    private fun visit(value: JsonElement, path: String, field: String = "", hashes: Boolean = false) {
        when (value) {
            is JsonObject -> value.forEach { (key, child) ->
                visit(child, "$path.$key", key, hashes || field == "sha256ByAbi")
            }
            is JsonArray -> value.forEachIndexed { i, child -> visit(child, "$path[$i]", field, hashes) }
            is JsonPrimitive -> {
                if (field == "sha256" || hashes) {
                    require(value.isString && hash.matches(value.content)) { "$path: sha256 phải có 64 ký tự hex" }
                }
                if (field == "runner") {
                    require(value.isString && value.content in runners) { "$path: runner chưa biết" }
                }
                val isUrl = field.lowercase().let { it.endsWith("url") || it in urlFields || it == "lists" }
                if ((isUrl && value != JsonNull) || (value.isString && Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(value.content))) {
                    require(value.isString) { "$path: URL phải là chuỗi" }
                    val text = value.content
                    if (text.isNotEmpty()) {
                        val url = text.replace("{abi}", "arm64-v8a").toHttpUrlOrNull()
                        require(url != null && url.scheme == "https" && text.startsWith("https://")) { "$path: URL phải là HTTPS hợp lệ" }
                    }
                }
            }
        }
        if (field == "errorPatterns") {
            require(value is JsonArray && value.size <= 32 && value.all {
                it is JsonPrimitive && it.isString && it.content.isNotBlank() && it.content.length <= 160
            }) { "$path: errorPatterns phải là mảng tối đa 32 chuỗi không rỗng, <=160 ký tự" }
        }
        if (field == "sha256ByAbi") require(value is JsonObject) { "$path: sha256ByAbi phải là object" }
    }
}
