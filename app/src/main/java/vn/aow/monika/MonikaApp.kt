package vn.aow.monika

import android.app.Application
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.playsoftware.j2meloader.J2meRuntime
import vn.aow.monika.library.CacheCleanWorker
import vn.aow.monika.library.StorageCleaner
import vn.aow.monika.notify.NewPostWorker
import vn.aow.monika.notify.Notifier

open class MonikaApp : Application(), coil.ImageLoaderFactory {
    /** Ảnh toàn app: hiện dần (crossfade), bộ đệm RAM 20% + đĩa 250 MB → quay lại màn cũ ảnh có ngay. */
    override fun newImageLoader(): coil.ImageLoader = coil.ImageLoader.Builder(this)
        .crossfade(280)
        .memoryCache { coil.memory.MemoryCache.Builder(this).maxSizePercent(0.20).build() }
        .diskCache { coil.disk.DiskCache.Builder().directory(cacheDir.resolve("images")).maxSizeBytes(250L * 1024 * 1024).build() }
        .respectCacheHeaders(false) // Ảnh Blogger không đổi theo link → giữ lâu.
        .build()

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // Bắt lỗi trước mọi thứ (kể cả khởi tạo J2ME) → crash lúc mở app cũng xem được nội dung lỗi.
        if (!isCrashProcess()) CrashReporter.install(this)
        // J2ME Loader nhúng sẵn cần context sớm, ở mọi tiến trình (kể cả ":midlet" chạy game Java).
        if (isCrashProcess()) return
        // Tiến trình ":game" chỉ chạy lõi libretro → không cần J2ME.
        if (!isLibretroProcess()) initJ2me()
    }

    override fun onCreate() {
        super.onCreate()
        if (isCrashProcess()) return
        AppGraph.init(this)
        // Tiến trình ":midlet" chỉ để chạy game Java → không đặt lịch/tải cấu hình ở đó.
        if (isGameProcess()) return
        // Quét sẵn thư viện ở nền → mở tab Thư viện / "Đang chơi dở" hiện ngay.
        scope.launch(Dispatchers.IO) { runCatching { AppGraph.library.list() } }
        Notifier.createChannels(this)
        // Làm nóng hồ sơ tài khoản → điểm danh hiện tức thì.
        scope.launch(Dispatchers.IO) { runCatching { AppGraph.aow.cachedProfile(); if (AppGraph.account.session.value != null) AppGraph.aow.profile() } }
        NewPostWorker.schedule(this, AppGraph.config.current.feed.pollMinutes)
        CacheCleanWorker.schedule(this)
        scope.launch(Dispatchers.IO) { runCatching { StorageCleaner.clean(this@MonikaApp) } }
        // Lần đầu: tải sẵn các lõi phổ biến (GBA, NES...) kèm thông báo; đã đủ lõi thì worker thoát ngay.
        runCatching { vn.aow.monika.library.CorePrefetchWorker.enqueue(this) }
        scope.launch {
            // Lấy cấu hình mới; nếu chu kỳ kiểm tra bài đổi thì đặt lịch lại.
            AppGraph.config.refresh().onSuccess {
                NewPostWorker.schedule(this@MonikaApp, it.feed.pollMinutes)
                // Config mới có thể thêm lõi tải sẵn / nâng version lõi.
                runCatching { vn.aow.monika.library.CorePrefetchWorker.enqueue(this@MonikaApp) }
            }
        }
    }

    /** Tách riêng để test khởi động (Robolectric không nạp được lớp javax.* của J2ME). */
    protected open fun initJ2me() {
        J2meRuntime.init(this)
        // Menu trong game Java = menu popup Monika (cùng thiết kế với giả lập khác).
        J2meRuntime.menuPresenter = vn.aow.monika.runner.J2meMenu
    }

    private fun isCrashProcess(): Boolean =
        Build.VERSION.SDK_INT >= 28 && getProcessName().endsWith(":crash")

    private fun isGameProcess(): Boolean =
        Build.VERSION.SDK_INT >= 28 && (getProcessName().endsWith(":midlet") || getProcessName().endsWith(":game"))

    private fun isLibretroProcess(): Boolean =
        Build.VERSION.SDK_INT >= 28 && getProcessName().endsWith(":game")
}
