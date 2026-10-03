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
    /** Lõi tải sẵn ngay lần đầu mở app (kèm thông báo tiến độ) → chơi được liền, không chờ tải khi bấm Chơi. */
    val prefetchCores: List<String> = emptyList(),
    /** Đuôi file → gói phụ trợ (id trong [modules]) tải trước ngay khi bắt đầu tải, vd. "7z" → ["archive"], "jar" → ["java"]. */
    val prefetchByExtension: Map<String, List<String>> = emptyMap(),
    /** Link cộng đồng AowVN (group Facebook, Discord). Để trống = ẩn nút. */
    val community: CommunityConfig = CommunityConfig(),
    val forum: ForumConfig = ForumConfig(),
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
    /** SHA-256 (hex) của gói .zip tải về; trống = không kiểm (chỉ nên trống ở bản thử). */
    val sha256: String = "",
    /** Dung lượng gói (byte) để hỏi người dùng trước khi tải; 0 = chưa biết. */
    val size: Long = 0,
    /** Gói theo kiến trúc: [url] chứa "{abi}"; SHA-256 / dung lượng riêng từng ABI (khóa = arm64-v8a, armeabi-v7a…). */
    val sha256ByAbi: Map<String, String> = emptyMap(),
    val sizeByAbi: Map<String, Long> = emptyMap(),
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
    /** Các kênh cộng đồng hiện ở Trang chủ (mỗi kênh 1 emoji 3D Fluent). Trống = tự dựng từ facebookGroup + discord. */
    val channels: List<CommunityChannel> = emptyList(),
) {
    /** Danh sách kênh để hiển thị (bỏ kênh trống link). */
    fun shown(): List<CommunityChannel> = channels.ifEmpty {
        listOf(
            CommunityChannel("facebook", "Group Facebook", "Hỏi đáp, xin game", facebookGroup, "fluent3d_busts_in_silhouette"),
            CommunityChannel("discord", "Discord", "Chat, nhóm dịch", discord, "fluent3d_speech_balloon"),
        )
    }.filter { it.url.isNotBlank() }
}

/** 1 kênh cộng đồng: tên, mô tả ngắn, link và tên emoji 3D (tệp drawable `fluent3d_*`). */
@Serializable
data class CommunityChannel(
    val id: String,
    val name: String,
    val subtitle: String = "",
    val url: String,
    val emoji: String = "fluent3d_speech_balloon",
)

/** Diễn đàn AowVN (Flarum): Trang chủ hiện chủ đề mới nhất + lối tắt chuyên mục. */
@Serializable
data class ForumConfig(
    val enabled: Boolean = true,
    val baseUrl: String = "https://forum.aowvn.org",
    /** Số chủ đề hiện ở Trang chủ. */
    val limit: Int = 6,
    /** Tên chuyên mục (tag) không hiện ở app. */
    val hiddenTags: List<String> = emptyList(),
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
    /**
     * false = hệ này KHÔNG bao giờ mở app ngoài: có engine nhúng (và gói hỗ trợ máy) thì chạy nhúng, không thì báo lỗi rõ.
     * Mặc định true để đọc được config cũ; bật tắt theo từng hệ khi engine nhúng của hệ đó đã phát hành.
     */
    val allowExternalApp: Boolean = true,
    /**
     * Đuôi chỉ nhận khi file đúng loại thật (vd. kirikiri: "exe" phải là exe có XP3 gắn trong hoặc có .xp3 cùng thư mục;
     * exe thường của Windows/RPG Maker/Ren'Py thì không). Để RIÊNG khỏi [extensions] để app cũ không nhận nhầm mọi file .exe.
     */
    val extensionsSniffed: List<String> = emptyList(),
    /** Tên file (regex, so với tên đầy đủ) KHÔNG được làm lối vào game, vd. bản vá "^patch\\d*\\.xp3$". */
    val entryExclude: List<String> = emptyList(),
    /** Cách chọn lối vào khi một thư mục có nhiều file hợp đuôi: first = đầu tiên · largest = lớn nhất · paired = trùng tên với file đi kèm ([entryPairExt]) rồi lớn nhất. */
    val entryPick: String = "first",
    /** Với entryPick = paired: đuôi file đi kèm, vd. ["exe"] (karanoshojo.exe ↔ karanoshojo.xp3). */
    val entryPairExt: List<String> = emptyList(),
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
    /** Bộ lọc hình: "sharp" (nét, hợp game điểm ảnh), "crt", "lcd" hoặc trống = mặc định. */
    val shader: String = "",
    /**
     * Tùy chọn riêng theo sức máy, đè lên [options] (user chỉnh trong game vẫn đè lên cuối cùng):
     * "lite" = máy yếu (RAM < 3,5 GB, ít nhân hoặc đang bật tiết kiệm pin) · "full" = máy mạnh (RAM ≥ 7 GB, ≥ 8 nhân).
     * Máy trung bình dùng [options] như cũ. Khóa/giá trị phải có thật trong lõi — `scripts/audit-cores.py` kiểm.
     */
    val perf: Map<String, Map<String, String>> = emptyMap(),
    /** Âm thanh độ trễ thấp (Oboe). Lõi nặng đặt false để bộ đệm âm thanh lớn hơn, đỡ rè khi máy chưa kịp. */
    val lowLatencyAudio: Boolean = true,
    /** Các "kiểu hiển thị" người chơi đổi được trong game (vd. GBA: LCD cổ điển / Sắc nét / Mượt). Null = chỉ dùng [shader]. */
    val display: CoreDisplay? = null,
)

/** Màn hình của máy gốc + các kiểu hiển thị của lõi. Hết kiểu cuối thì quay lại kiểu đầu tiên trong [styles]. */
@Serializable
data class CoreDisplay(
    /** Độ phân giải gốc [rộng, cao] của màn máy gốc (GBA 240×160) — dùng để co giãn theo bội số nguyên và căn lưới LCD. */
    val native: List<Int> = emptyList(),
    /** Khóa kiểu mặc định; không khớp thì lấy kiểu đầu tiên. */
    val default: String = "",
    val styles: Map<String, DisplayStyle> = emptyMap(),
)

@Serializable
data class DisplayStyle(
    /** Tên hiện cho người chơi. */
    val label: String,
    /** Bộ lọc hình: "sharp" | "lcd" | "crt" | trống = nội suy mượt mặc định của LibretroDroid. */
    val shader: String = "",
    /** Co giãn theo bội số nguyên của độ phân giải gốc: điểm ảnh vuông đều, không nhòe/không gợn (hình nhỏ hơn khung vài %, viền đen). */
    val integer: Boolean = false,
    /** Độ đậm lưới điểm ảnh LCD phủ lên hình (0 = tắt, ~0,3 = vừa). Chỉ vẽ khi co giãn số nguyên ≥ 3×, vì lưới lệch nhịp sẽ gợn. */
    val grid: Float = 0f,
    /** Tùy chọn lõi đi kèm (vd. mgba_color_correction). Người chơi chỉnh tay trong "Tùy chọn giả lập" thì đè lên. */
    val options: Map<String, String> = emptyMap(),
)

@Serializable
data class WebPlayerDef(
    val script: String = "",
    /** Trình chạy nằm trong gói tải khi cần (id trong [MonikaConfig.modules]), vd. "onsyuri" cho ONScripter. */
    val module: String? = null,
)

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
