package vn.aow.monika.config

import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Kiểm dữ liệu gốc trước parse: coerceInputValues không được làm mất giá trị sai kiểu. */
internal object ConfigValidation {
    private val runners = setOf("libretro", "web", "apk", "external", "j2me")
    private val hash = Regex("[0-9a-fA-F]{64}")
    private val urlFields = setOf("endpoint", "systemfiles", "script", "facebookgroup", "discord")
    /** Nhánh config chứa URL tải **mã/gói** — phải thuộc [ALLOWED_DOWNLOAD_HOSTS]. */
    private val downloadSections = listOf("config.cores.", "config.modules.", "config.packs.", "config.webPlayers.")

    /**
     * V206: URL mà app **tự tải rồi thực thi/giải nén** (lõi, module, gói, APK cập nhật, systemFiles, script
     * web player) — nhóm này phải qua cả tiền tố đường dẫn. Link mời cài app ngoài/plugin (`downloadUrl`) chỉ
     * cần đúng host vì người dùng mở bằng trình duyệt, không tự chạy.
     */
    private fun isPinnedArtifactUrl(path: String, field: String): Boolean = when {
        field == "apkUrl" || field == "systemFiles" -> true
        field == "script" -> path.startsWith("config.webPlayers.")
        field == "url" -> downloadSections.any { path.startsWith(it) }
        else -> false
    }

    private fun isDownloadUrl(path: String, field: String): Boolean = when {
        field == "apkUrl" || field == "systemFiles" -> true
        // V196: link tải/mời cài của app ngoài và plugin cũng phải qua allowlist (link aow.vn hoặc rỗng).
        field == "downloadUrl" -> true
        field == "script" -> path.startsWith("config.webPlayers.")
        field == "url" -> downloadSections.any { path.startsWith(it) }
        else -> false
    }

    fun parse(text: String, minimumVersion: Int): MonikaConfig {
        val root = ConfigRepository.json.parseToJsonElement(text)
        require(root is JsonObject) { "Config phải là object" }
        visit(root, "config")
        val cfg = ConfigRepository.parse(text)
        cfg.saveTransfer.validate()
        require(cfg.configVersion >= minimumVersion) { "configVersion thấp hơn bản đang dùng" }
        return cfg
    }

    private fun visit(value: JsonElement, path: String, field: String = "", hashes: Boolean = false) {
        if (field == "errorPatterns") {
            require(value is JsonArray && value.size <= 32 && value.all {
                it is JsonPrimitive && it.isString && it.content.isNotBlank() && it.content.length <= 160
            }) { "$path: errorPatterns phải là mảng tối đa 32 chuỗi không rỗng, <=160 ký tự" }
            // Mẫu là literal, không phải URL; không lặp quy tắc mảng trên từng chuỗi.
            return
        }
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
                        if (field == "directUrl") {
                            // V196b: URL tệp do config tạo, đi thẳng vào trình quản lý tải của hệ thống.
                            require(isAllowedDirectUrlHost(url!!.host.lowercase())) { "$path: host tải tệp không nằm trong danh sách cho phép" }
                        } else if (isDownloadUrl(path, field)) {
                            // Chỉ nhận host đã chốt trong APK; không in URL/host ra log (chỉ nêu đường dẫn trường).
                            val host = url!!.host.lowercase()
                            require(isAllowedDownloadHost(host)) { "$path: host tải không nằm trong danh sách cho phép" }
                            if (isPinnedArtifactUrl(path, field)) {
                                // V206: mã/gói phải nằm trong tiền tố chủ sở hữu; host CDN GitHub chỉ dùng làm đích redirect.
                                require(!isRedirectOnlyDownloadHost(host)) {
                                    "$path: host chỉ được dùng làm đích redirect từ URL gốc hợp lệ"
                                }
                                require(isAllowedDownloadPath(host, url.encodedPath)) {
                                    "$path: đường dẫn tải không nằm trong tiền tố cho phép của host"
                                }
                            }
                        }
                    }
                }
            }
        }
        if (field == "sha256ByAbi") require(value is JsonObject) { "$path: sha256ByAbi phải là object" }
    }
}
