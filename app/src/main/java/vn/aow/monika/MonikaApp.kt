package vn.aow.monika

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import vn.aow.monika.library.CacheCleanWorker
import vn.aow.monika.library.StorageCleaner
import vn.aow.monika.notify.NewPostWorker
import vn.aow.monika.notify.Notifier

class MonikaApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        Notifier.createChannels(this)
        NewPostWorker.schedule(this, AppGraph.config.current.feed.pollMinutes)
        CacheCleanWorker.schedule(this)
        scope.launch(Dispatchers.IO) { runCatching { StorageCleaner.clean(this@MonikaApp) } }
        scope.launch {
            // Lấy cấu hình mới; nếu chu kỳ kiểm tra bài đổi thì đặt lịch lại.
            AppGraph.config.refresh().onSuccess { NewPostWorker.schedule(this@MonikaApp, it.feed.pollMinutes) }
        }
    }
}
