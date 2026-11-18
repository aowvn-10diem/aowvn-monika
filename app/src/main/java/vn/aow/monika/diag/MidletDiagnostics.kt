package vn.aow.monika.diag

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
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
 *  - đường quay về Monika: [cleanupAfterCollect] dọn phiên `midlet` của lần thoát **bình thường** (xem dưới).
 *
 * Vì sao cần dọn: nút thoát game gọi `MidletThread.notifyDestroyed()` → `finish()` rồi
 * `Process.killProcess(myPid())` ngay, nên `onDestroy` (và [Diagnostics.end]) thường **không kịp** chạy;
 * phiên còn lại sẽ bị lần mở Monika kế tiếp hiểu là "game chết bất thường". Dọn **chỉ sau** khi
 * [Diagnostics.collect]/[Diagnostics.collectProcessDeaths] đã chạy và **chỉ khi** Android xác nhận người
 * dùng tự thoát — thà còn một báo cáo giả hơn che mất crash/ANR thật của game Java.
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
    /** Cùng đường dẫn với `Diagnostics.sessionFile`/`deathSessionFile` (private bên đó). */
    private const val DIAG_DIR = "diag"
    private const val SESSION_FILE = "$DIAG_DIR/active-session.json"

    @Volatile private var installed = false
    private val json = Json { ignoreUnknownKeys = true }

    /** Tên lớp của hoạt động chơi game Java (hàm thuần, không cần Android). */
    fun isMidletActivity(className: String): Boolean = className == MIDLET_ACTIVITY

    /** Tiến trình hiện tại có phải tiến trình chơi game Java không. */
    fun isMidletProcess(processName: String?): Boolean = processName?.endsWith(PROCESS_SUFFIX) == true

    /** Chỉ gắn phiên khi API đủ mới (`getProcessName()`) và đúng tiến trình `:midlet` (hàm thuần). */
    internal fun shouldInstall(sdk: Int, processName: String?): Boolean = sdk >= 28 && isMidletProcess(processName)

    /**
     * Gọi trong `MonikaApp.onCreate`, sau `AppGraph.init` và **trước** `if (isGameProcess()) return`.
     * Ở tiến trình khác: không làm gì (dọn phiên nằm ở [cleanupAfterCollect], không được chạy trước `collect`).
     * Không bao giờ ném: chẩn đoán hỏng không được làm hỏng việc chơi game.
     */
    fun install(app: Application) {
        // getProcessName() chỉ có từ API 28; máy cũ chạy như trước (không có phiên).
        if (Build.VERSION.SDK_INT < 28) return
        val process = runCatching { Application.getProcessName() }.getOrNull()
        if (!shouldInstall(Build.VERSION.SDK_INT, process) || installed) return
        installed = true
        // diag: không ghi báo cáo lỗi — chỉ thiếu phiên chẩn đoán, game vẫn chạy bình thường.
        runCatching { app.registerActivityLifecycleCallbacks(Callbacks()) }
    }

    /**
     * Có được xoá phiên `midlet` không. **Chỉ** khi đã chứng minh người dùng tự thoát: tiến trình đã chết,
     * và Android ghi nhận lý do là [ApplicationExitInfo.REASON_USER_REQUESTED] hoặc
     * [ApplicationExitInfo.REASON_EXIT_SELF]. ANR, crash, hết RAM, `REASON_OTHER`, hay **không có** dữ liệu
     * (kể cả API < 30) → **giữ nguyên** để `collect`/`collectProcessDeaths` báo.
     */
    internal fun shouldClear(kind: String, pidAlive: Boolean, exitReason: Int?, sdk: Int): Boolean {
        if (sdk < 30 || kind != KIND || pidAlive) return false
        return exitReason == ApplicationExitInfo.REASON_USER_REQUESTED || exitReason == ApplicationExitInfo.REASON_EXIT_SELF
    }

    /**
     * Gọi **ngay sau** `Diagnostics.collect` + `collectProcessDeaths` trong `MainActivity.onResume` (cả
     * `github` lẫn `play`). Chỉ dọn phiên `midlet` đã thoát bình thường; API < 30 không xoá gì vì không có
     * dữ liệu để phân biệt (đánh đổi có chủ ý: máy Android 9–10 có thể còn một báo cáo `killed` giả sau khi
     * thoát game Java — chấp nhận, miễn không che crash thật).
     */
    fun cleanupAfterCollect(app: Application) {
        if (Build.VERSION.SDK_INT < 30) return
        // diag: không ghi báo cáo lỗi — chỉ dọn dấu vết lần thoát bình thường, không đụng dữ liệu người chơi.
        runCatching { cleanupAfterCollect(app, Build.VERSION.SDK_INT, exitReasonOf = { exitReason(app, it) }) }
    }

    /** Phần kiểm thử được: tách [sdk]/[exitReasonOf]/[aliveOf] để không cần dựng [ApplicationExitInfo] thật. */
    internal fun cleanupAfterCollect(
        app: Application,
        sdk: Int,
        exitReasonOf: (Int) -> Int?,
        aliveOf: (Int) -> Boolean = { isAlive(app, it) },
    ) {
        val file = File(app.filesDir, SESSION_FILE)
        if (!file.isFile) return
        val session = runCatching {
            json.decodeFromString(Diagnostics.Session.serializer(), file.readText())
        }.getOrNull() ?: return
        if (!shouldClear(session.kind, aliveOf(session.pid), exitReasonOf(session.pid), sdk)) return
        file.delete()
        // Ảnh chụp của pid đã thoát bình thường không còn ý nghĩa (không phải để lại cho lần quét sau).
        File(File(app.filesDir, DIAG_DIR), "sessions/${session.pid}.json").delete()
    }

    private fun exitReason(app: Application, pid: Int): Int? {
        if (Build.VERSION.SDK_INT < 30) return null
        return runCatching {
            app.getSystemService(ActivityManager::class.java)
                .getHistoricalProcessExitReasons(app.packageName, pid, 1)
                .firstOrNull()?.reason
        }.getOrNull()
    }

    private fun isAlive(app: Application, pid: Int): Boolean = runCatching {
        pidAlive(app.getSystemService(ActivityManager::class.java).runningAppProcesses, pid)
    }.getOrDefault(true) // Ném khi đọc → coi như còn sống, không xoá phiên.

    /**
     * Tiến trình [pid] còn sống không. Danh sách không đọc được (`null`) → coi như **còn sống**
     * (hướng an toàn: không xoá phiên, để lần chết thật sau đó vẫn có bằng chứng).
     */
    internal fun pidAlive(processes: List<ActivityManager.RunningAppProcessInfo>?, pid: Int): Boolean =
        processes?.any { it.pid == pid } ?: true

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

    /** Đã đăng ký callback trong tiến trình này chưa (dùng cho test). */
    internal fun isRegistered(): Boolean = installed

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
