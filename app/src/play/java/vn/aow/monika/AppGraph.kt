package vn.aow.monika

import android.app.Application
import okhttp3.OkHttpClient
import vn.aow.monika.config.ConfigRepository
import vn.aow.monika.feed.FeedRepository
import vn.aow.monika.library.GameLibrary
import vn.aow.monika.runner.CoreManager
import vn.aow.monika.runner.GameLauncher
import java.time.Clock
import java.util.concurrent.TimeUnit

/**
 * Nơi duy nhất tạo các thành phần của app (DI thủ công, không cần thư viện).
 * Muốn thay 1 thành phần: sửa đúng 1 dòng ở đây.
 */
object AppGraph {
    lateinit var app: Application
        private set

    fun init(application: Application) {
        app = application
        vn.aow.monika.diag.Diagnostics.allowDebugSend = prefs.debugSendCrash
        // V112: báo cáo lỗi thao tác người dùng (op-fail) gửi ngầm cùng đường, cùng công tắc với lõi sập.
        vn.aow.monika.diag.Diagnostics.opSender = { c, r ->
            val cr = config.current.crash
            vn.aow.monika.diag.Diagnostics.autoSend(c, http, r, cr.endpoint, cr.autoSend, prefs.autoSendCrash)
        }
    }

    /**
     * V202: nhả bộ đệm tiến trình chính khi Android báo thiếu RAM hoặc app vừa ra nền.
     * Bản Play không có trình duyệt nên không có AdBlock.
     */
    fun trimMemory(level: Int) {
        if (level < android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) return
        // diag: bỏ qua lỗi nhả bộ đệm ảnh — đây chỉ là tối ưu RAM, không ảnh hưởng dữ liệu người chơi.
        runCatching { coil.Coil.imageLoader(app).memoryCache?.clear() }
        // diag: bỏ qua lỗi nhả cache feed — mở lại bài sẽ tải lại từ đĩa/mạng.
        runCatching { feed.trimMemory() }
    }

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "AowVN-Monika/${BuildConfig.VERSION_NAME} (Android)")
                        .build()
                )
            }
            .build()
    }

    val prefs by lazy { Prefs(app) }
    val config by lazy { ConfigRepository(app, http) }

    /** V193 G1: lựa chọn người dùng thắng config; mặc định tắt (config.feed.enabled = false). */
    val feedEnabled: Boolean get() = prefs.feedEnabled ?: config.current.feed.enabled

    fun setFeedEnabled(on: Boolean) {
        prefs.feedEnabled = on
        // Play không đặt lịch thông báo bài mới; chỉ lưu lựa chọn cho màn bài viết.
    }

    val feed by lazy { FeedRepository(http, config, java.io.File(app.cacheDir, "feed").apply { mkdirs() }, app) { feedEnabled } }
    val forum by lazy { vn.aow.monika.forum.ForumRepository(http, config, java.io.File(app.cacheDir, "forum").apply { mkdirs() }) }
    val cores by lazy { CoreManager(app, http, config) }
    val packs by lazy { vn.aow.monika.pack.SimpleModule(app, http, config) }
    val azahar by lazy { vn.aow.monika.azahar.AzaharModule(app, http, config) }
    val gameInfo by lazy {
        vn.aow.monika.library.GameInfoResolver(app, feed, boxArts = vn.aow.monika.library.BoxArts(http, java.io.File(app.filesDir, "boxarts")))
    }
    val library by lazy { GameLibrary(app, config, gameInfo) }
    val launcher by lazy { GameLauncher(config) }
    /** File mở từ app khác ("Mở bằng Aow Monika") đang chờ màn Thư viện nhận vào. */
    val pendingImports = kotlinx.coroutines.flow.MutableStateFlow<List<android.net.Uri>>(emptyList())
    val account by lazy { vn.aow.monika.account.Account(app, http, config) }
    val clock: Clock by lazy { Clock.systemDefaultZone() }
    val aow by lazy { vn.aow.monika.account.AowApi(http, config, account, clock) }
    val ra by lazy { vn.aow.monika.achievements.RaAccount(app, raApi) }
    val raApi by lazy { vn.aow.monika.achievements.RaApi(http) }
    val raIndex by lazy { vn.aow.monika.achievements.RaGameIndex(app, raApi, ra) }
    val raVi by lazy { vn.aow.monika.achievements.RaTranslations(app, http) }
    val translateSettings by lazy { vn.aow.monika.translate.TranslateSettings(app) }
    val translateMemory by lazy { vn.aow.monika.translate.TranslationMemory(app) }
    val screenTranslator by lazy { vn.aow.monika.translate.ScreenTranslator(http, translateSettings, translateMemory) }
}
