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

class MonikaApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // J2ME Loader nhúng sẵn cần context sớm, ở mọi tiến trình (kể cả ":midlet" chạy game Java).
        J2meRuntime.init(this)
    }

    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        // Tiến trình ":midlet" chỉ để chạy game Java → không đặt lịch/tải cấu hình ở đó.
        if (isGameProcess()) return
        Notifier.createChannels(this)
        NewPostWorker.schedule(this, AppGraph.config.current.feed.pollMinutes)
        CacheCleanWorker.schedule(this)
        scope.launch(Dispatchers.IO) { runCatching { StorageCleaner.clean(this@MonikaApp) } }
        scope.launch {
            // Lấy cấu hình mới; nếu chu kỳ kiểm tra bài đổi thì đặt lịch lại.
            AppGraph.config.refresh().onSuccess { NewPostWorker.schedule(this@MonikaApp, it.feed.pollMinutes) }
        }
    }

    private fun isGameProcess(): Boolean =
        Build.VERSION.SDK_INT >= 28 && getProcessName().endsWith(":midlet")
}
