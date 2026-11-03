package vn.aow.monika
import android.app.Application
import android.content.Context
import android.os.Build
import kotlinx.coroutines.*
/** Khởi động Play: không đặt lịch tải game, APK hoặc thành phần từ nguồn ngoài Play. */
open class MonikaApp : Application(), coil.ImageLoaderFactory {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    override fun newImageLoader(): coil.ImageLoader = coil.ImageLoader.Builder(this).build()
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        val process = if (Build.VERSION.SDK_INT >= 28) getProcessName() else ""
        if (!process.endsWith(":crash")) CrashReporter.install(this)
        if (!process.endsWith(":game") && !process.endsWith(":crash")) initJ2me()
    }
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 28 && getProcessName().endsWith(":crash")) return
        AppGraph.init(this)
        vn.aow.monika.notify.Notifier.createChannels(this)
        scope.launch(Dispatchers.IO) {
            vn.aow.monika.diag.Diagnostics.op(this@MonikaApp, "doc-thu-vien") { AppGraph.library.list() }
        }
    }
    protected open fun initJ2me() {
        ru.playsoftware.j2meloader.J2meRuntime.init(this)
        ru.playsoftware.j2meloader.J2meRuntime.menuPresenter = vn.aow.monika.runner.J2meMenu
    }
}
