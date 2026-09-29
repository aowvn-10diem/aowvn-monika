package vn.aow.monika

import android.app.Application
import okhttp3.OkHttpClient
import vn.aow.monika.config.ConfigRepository
import vn.aow.monika.download.Downloader
import vn.aow.monika.feed.FeedRepository
import vn.aow.monika.library.GameLibrary
import vn.aow.monika.runner.CoreManager
import vn.aow.monika.runner.GameLauncher
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
    val feed by lazy { FeedRepository(http, config, java.io.File(app.cacheDir, "feed").apply { mkdirs() }) }
    val cores by lazy { CoreManager(app, http, config) }
    val azahar by lazy { vn.aow.monika.azahar.AzaharModule(app, http, config) }
    val downloader by lazy { Downloader(app, http, prefs) }
    val gameInfo by lazy {
        vn.aow.monika.library.GameInfoResolver(app, feed, boxArts = vn.aow.monika.library.BoxArts(http, java.io.File(app.filesDir, "boxarts")))
    }
    val library by lazy { GameLibrary(app, config, gameInfo) }
    val launcher by lazy { GameLauncher(config) }
    /** File mở từ app khác ("Mở bằng Aow Monika") đang chờ màn Thư viện nhận vào. */
    val pendingImports = kotlinx.coroutines.flow.MutableStateFlow<List<android.net.Uri>>(emptyList())
    val account by lazy { vn.aow.monika.account.Account(app, http, config) }
    val aow by lazy { vn.aow.monika.account.AowApi(http, config, account) }
    val adblock by lazy { vn.aow.monika.browser.AdBlock(app, http) }
}
