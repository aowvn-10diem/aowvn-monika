package vn.aow.monika.config

import kotlinx.serialization.Serializable

/**
 * Ánh xạ 1-1 với config/monika-config.json.
 * Thêm trường mới: luôn cho giá trị mặc định để file cấu hình cũ vẫn đọc được.
 */
@Serializable
data class MonikaConfig(
    val configVersion: Int = 0,
    val app: AppInfo = AppInfo(),
    val feed: FeedConfig = FeedConfig(),
    val systems: List<SystemDef> = emptyList(),
    val engines: List<EngineRule> = emptyList(),
    val cores: Map<String, CoreDef> = emptyMap(),
    val webPlayers: Map<String, WebPlayerDef> = emptyMap(),
    val externalApps: List<ExternalApp> = emptyList(),
    val downloadHosts: List<DownloadHost> = emptyList(),
) {
    fun system(id: String): SystemDef? = systems.firstOrNull { it.id == id }
    fun externalApp(id: String): ExternalApp? = externalApps.firstOrNull { it.id == id }

    /** Nhãn blog dùng làm bộ lọc, giữ thứ tự khai báo, bỏ trùng. */
    fun feedLabels(): List<String> = systems.flatMap { it.labels }.distinct()
}

@Serializable
data class AppInfo(
    val latestVersionCode: Int = 0,
    val latestVersionName: String = "",
    val minVersionCode: Int = 0,
    val apkUrl: String = "",
    val changelog: String = "",
)

@Serializable
data class FeedConfig(
    val url: String = "https://www.aow.vn/feeds/posts/default",
    val pageSize: Int = 20,
    val pollMinutes: Long = 60,
)

@Serializable
data class SystemDef(
    val id: String,
    val name: String,
    /** libretro | web | apk | external */
    val runner: String,
    val core: String? = null,
    val webPlayer: String? = null,
    val externalApp: String? = null,
    val extensions: List<String> = emptyList(),
    val labels: List<String> = emptyList(),
)

@Serializable
data class EngineRule(
    val system: String,
    val markers: List<String>,
    val entry: List<String> = emptyList(),
)

@Serializable
data class CoreDef(
    val version: String,
    val url: String,
    val systemFiles: String? = null,
)

@Serializable
data class WebPlayerDef(val script: String)

@Serializable
data class ExternalApp(
    val id: String,
    val name: String,
    val description: String = "",
    val packageNames: List<String> = emptyList(),
    val downloadUrl: String = "",
    val plugins: List<Plugin> = emptyList(),
    val guide: List<String> = emptyList(),
)

@Serializable
data class Plugin(val name: String, val downloadUrl: String = "")

@Serializable
data class DownloadHost(
    val name: String,
    val host: String,
    /** direct | browser */
    val mode: String,
    val pattern: String? = null,
    val directUrl: String? = null,
)
