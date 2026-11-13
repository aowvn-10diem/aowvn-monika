package vn.aow.monika.diag

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.os.Process
import kotlinx.serialization.json.Json
import java.io.File

/**
 * #302: game Java chạy ở tiến trình riêng `:midlet` nên trước đây **không có phiên chẩn đoán** — mọi báo
 * cáo từ tiến trình đó mang lõi `unknown` (236 lượt). Module `j2me/` (nhúng, Apache-2.0) không phụ thuộc
 * `:app`, nên `:app` tự nhận tiến trình rồi gắn phiên bằng API công khai của [Diagnostics]:
 *  - tiến trình `:midlet`: đăng ký [Application.ActivityLifecycleCallbacks] và `begin` khi
 *    [MIDLET_ACTIVITY] mở — hoạt động **duy nhất** khai báo `android:process=":midlet"`
 *    (`j2me/src/main/AndroidManifest.xml`).
 *  - tiến trình khác: dọn phiên `midlet` còn sót của lần chơi trước (xem dưới).
 *
 * Vì sao phải dọn: nút thoát game gọi `MidletThread.notifyDestroyed()` → `finish()` rồi
 * `Process.killProcess(myPid())` ngay, nên `onDestroy` (và [Diagnostics.end]) thường **không kịp** chạy.
 * Phiên còn lại sẽ bị lần mở Monika kế tiếp hiểu là "game chết bất thường" (`killed`) dù người chơi thoát
 * bình thường. Tiến trình còn sống thì giữ nguyên phiên.
 */
object MidletDiagnostics {
    /** Hoạt động chơi game Java; khai báo `android:process=":midlet"` trong manifest của `j2me/`. */
    const val MIDLET_ACTIVITY = "javax.microedition.shell.MicroActivity"

    /** Khoá `Constants.KEY_MIDLET_NAME` của JL-Mod: tên hiển thị game, không phải đường dẫn. */
    private const val EXTRA_MIDLET_NAME = "midletName"
    private const val KIND = "midlet"
    /** Hậu tố tiến trình, khớp `MonikaApp.isGameProcess()`. */
    private const val PROCESS_SUFFIX = ":midlet"
    private const val CORE_INFO = "JL-Mod nhúng"
    private const val SESSION_FILE = "diag/active-session.json"

    @Volatile private var installed = false
    private val json = Json { ignoreUnknownKeys = true }

    /** Tên lớp của hoạt động chơi game Java (hàm thuần, không cần Android). */
    fun isMidletActivity(className: String): Boolean = className == MIDLET_ACTIVITY

    /** Tiến trình hiện tại có phải tiến trình chơi game Java không. */
    fun isMidletProcess(processName: String?): Boolean = processName?.endsWith(PROCESS_SUFFIX) == true

    /**
     * Gọi trong `MonikaApp.onCreate`, sau `AppGraph.init` và **trước** `if (isGameProcess()) return`.
     * Không bao giờ ném: chẩn đoán hỏng không được làm hỏng việc chơi game.
     */
    fun install(app: Application) {
        // getProcessName() chỉ có từ API 28; máy cũ chạy như trước (không có phiên).
        if (Build.VERSION.SDK_INT < 28) return
        val process = runCatching { Application.getProcessName() }.getOrNull()
        if (isMidletProcess(process)) {
            if (installed) return
            installed = true
            // diag: không ghi báo cáo lỗi — chỉ thiếu phiên chẩn đoán, game vẫn chạy bình thường.
            runCatching { app.registerActivityLifecycleCallbacks(Callbacks()) }
        } else {
            // diag: không ghi báo cáo lỗi — chỉ dọn dấu vết lần chơi trước, không đụng dữ liệu người chơi.
            runCatching { clearLeftoverSession(app) }
        }
    }

    /**
     * Phiên `midlet` còn lại mà tiến trình đã chết = lần chơi trước đã thoát (xem KDoc đầu tệp), không phải
     * sự cố. Chỉ xoá đúng phiên `midlet`; phiên của lõi khác vẫn do [Diagnostics.collect] xử lý như cũ.
     */
    private fun clearLeftoverSession(app: Application) {
        val file = File(app.filesDir, SESSION_FILE)
        if (!file.isFile) return
        val session = runCatching {
            json.decodeFromString(Diagnostics.Session.serializer(), file.readText())
        }.getOrNull() ?: return
        if (session.kind != KIND || session.pid == Process.myPid() || isAlive(app, session.pid)) return
        Diagnostics.end(app)
    }

    private fun isAlive(app: Application, pid: Int): Boolean = runCatching {
        app.getSystemService(ActivityManager::class.java).runningAppProcesses?.any { it.pid == pid } == true
    }.getOrDefault(true) // Không đọc được danh sách → coi như còn sống, không xoá phiên.

    /** Mở phiên cho 1 lượt chơi Java; tách khỏi callback để kiểm thử được. */
    internal fun begin(activity: Activity) {
        // diag: không ghi báo cáo lỗi — thiếu phiên chỉ mất thông tin chẩn đoán.
        runCatching { Diagnostics.begin(activity, KIND, KIND, CORE_INFO, gameName(activity), "J2ME") }
    }

    /** Đóng phiên khi người chơi thoát game (không đóng khi chỉ đổi cấu hình). */
    internal fun end(activity: Activity) {
        if (!activity.isFinishing || activity.isChangingConfigurations) return
        runCatching { Diagnostics.end(activity) }
    }

    private class Callbacks : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            if (isMidletActivity(activity.javaClass.name)) begin(activity)
        }

        override fun onActivityStarted(activity: Activity) = Unit

        override fun onActivityResumed(activity: Activity) {
            if (!isMidletActivity(activity.javaClass.name)) return
            // Vào (lại) game: giai đoạn "playing" để báo cáo ghi đúng "khi đang chơi".
            runCatching { Diagnostics.stage(activity, "playing") }
        }

        override fun onActivityPaused(activity: Activity) {
            if (!isMidletActivity(activity.javaClass.name)) return
            // Nhịp sống cuối = lúc rời game → báo cáo biết game đã sống được bao lâu.
            runCatching { Diagnostics.heartbeat(activity) }
        }

        override fun onActivityStopped(activity: Activity) = Unit

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

        override fun onActivityDestroyed(activity: Activity) {
            // Xoay máy/đổi cấu hình không phải thoát game: activity được tạo lại ngay sau đó.
            if (isMidletActivity(activity.javaClass.name)) end(activity)
        }
    }

    /** Tên game Monika gửi sang; không phải đường dẫn và chỉ đi vào [Diagnostics] (đã che thông tin riêng). */
    private fun gameName(activity: Activity): String =
        runCatching { activity.intent?.getStringExtra(EXTRA_MIDLET_NAME).orEmpty().trim() }.getOrDefault("")
}
