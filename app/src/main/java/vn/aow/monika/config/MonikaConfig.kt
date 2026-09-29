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
    /** Mật khẩu file nén app tự thử khi giải nén (vd. mật khẩu quen dùng của aow.vn). */
    val archivePasswords: List<String> = emptyList(),
    /** Link cộng đồng AowVN (group Facebook, Discord). Để trống = ẩn nút. */
    val community: CommunityConfig = CommunityConfig(),
    /** Chặn quảng cáo trong trình duyệt nhúng. */
    val adblock: AdBlockConfig = AdBlockConfig(),
    /** Tài khoản AowVN (Firebase của web aow.vn): đăng nhập, điểm danh, đánh giá, vote/donate. */
    val account: AccountConfig = AccountConfig(),
    /** Bản dịch tên/giá trị tùy chọn lõi giả lập (tiếng Anh → tiếng Việt). */
    val coreOptionText: CoreOptionText = CoreOptionText(),
    /** Báo lỗi game / lõi giả lập. */
    val crash: CrashConfig = CrashConfig(),
    /** Module engine tải khi cần (thư viện native ngoài lõi libretro), vd. "azahar" cho 3DS. */
    val modules: Map<String, ModuleDef> = emptyMap(),
) {
    fun system(id: String): SystemDef? = systems.firstOrNull { it.id == id }
    fun externalApp(id: String): ExternalApp? = externalApps.firstOrNull { it.id == id }

    /** Nhãn blog dùng làm bộ lọc, giữ thứ tự khai báo, bỏ trùng. */
    fun feedLabels(): List<String> = systems.flatMap { it.labels }.distinct()
}

/** Module engine tải khi người chơi có game: gói .zip chứa thư viện native + manifest. */
@Serializable
data class ModuleDef(
    /** Đổi version → app tải lại module. */
    val version: String,
    /** Link gói .zip (vd. asset trong GitHub Releases của workflow build-engines). Trống = module chưa sẵn sàng. */
    val url: String = "",
    /** Kiến trúc CPU hỗ trợ; trống = mọi kiến trúc. */
    val abis: List<String> = emptyList(),
)

@Serializable
data class CrashConfig(
    /** Địa chỉ nhận báo lỗi (POST JSON, ẩn danh). Trống = người chơi tự chép báo lỗi dán vào nhóm AowVN. */
    val endpoint: String = "",
    /** true = tự gửi báo lỗi game sập (nếu có endpoint) không cần hỏi. */
    val autoSend: Boolean = false,
)

@Serializable
data class CoreOptionText(
    val labels: Map<String, String> = emptyMap(),
    val values: Map<String, String> = emptyMap(),
)

@Serializable
data class AccountConfig(
    val enabled: Boolean = true,
    /** Firebase Web API key (công khai, giống trên web). */
    val apiKey: String = "",
    val databaseUrl: String = "",
    /** Trang đăng nhập trên web (docs/web/device-login.html). */
    val loginUrl: String = "https://www.aow.vn/p/device-login.html",
    /** Trang vote/donate trên web (mở khi cần tính năng app chưa có). */
    val voteUrl: String = "https://www.aow.vn/p/vote-game.html",
    /** Nút "Ủng hộ AowVN" ở Trang chủ mở trang này. Trống = dùng [voteUrl]. */
    val donateUrl: String = "",
    val donate: DonateConfig = DonateConfig(),
)

@Serializable
data class DonateConfig(
    /** Mã BIN ngân hàng (VietQR), số tài khoản, tên, mẫu QR, tiền tố nội dung — giống CONFIG của trang vote. */
    val bankId: String = "",
    val accountNo: String = "",
    val accountName: String = "",
    val template: String = "compact2",
    val memoPrefix: String = "AowVN",
    val amounts: List<Long> = listOf(10_000, 20_000, 50_000, 100_000),
)

@Serializable
data class AdBlockConfig(
    val enabled: Boolean = true,
    /** Bộ lọc: tên miền / hosts / ||domain^ và luật Adblock Plus (đường dẫn, tùy chọn, ẩn phần tử). */
    val lists: List<String> = emptyList(),
    /** Tên miền không bao giờ chặn (kể cả tên miền con). */
    val allow: List<String> = emptyList(),
    val updateHours: Int = 72,
    /** Ẩn khung quảng cáo còn sót bằng CSS (luật ẩn phần tử của bộ lọc). */
    val cosmetic: Boolean = true,
    /** Chặn cửa sổ bật lên / chuyển hướng quảng cáo không do người dùng bấm. */
    val popups: Boolean = true,
)

@Serializable
data class CommunityConfig(
    val facebookGroup: String = "",
    val discord: String = "",
)

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
    /** Kho ảnh bìa libretro-thumbnails (vd. "Nintendo - Nintendo DS"). Trống = mặc định theo id hệ máy. */
    val thumbnails: List<String> = emptyList(),
    /** Engine nhúng thay cho lõi libretro (vd. "azahar" cho 3DS). Chưa sẵn sàng / máy không hợp → tự dùng [core] libretro. */
    val engine: String? = null,
    /** Bố cục tay cầm ảo: gb | gba | nds | ps | rpg. Null = tự chọn theo lõi. */
    val pad: String? = null,
    /** Lõi thay thế user được chọn trong Cài đặt (vd. NDS: desmume, melonds). */
    val altCores: List<String> = emptyList(),
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
    /** Tỉ lệ khung hình (rộng/cao) để xếp màn chơi dọc. Null = dùng mặc định theo lõi. */
    val aspectRatio: Float? = null,
    /** Tùy chọn lõi mặc định (key libretro → giá trị). User chỉnh trong game sẽ đè lên. */
    val options: Map<String, String> = emptyMap(),
    /** Kiến trúc CPU lõi hỗ trợ (vd. ["arm64-v8a"] cho Citra). Trống = mọi kiến trúc. */
    val abis: List<String> = emptyList(),
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
