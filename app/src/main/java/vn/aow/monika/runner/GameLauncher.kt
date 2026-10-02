package vn.aow.monika.runner

import vn.aow.monika.AppGraph
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import vn.aow.monika.config.ConfigRepository
import vn.aow.monika.config.ExternalApp
import vn.aow.monika.library.Game
import java.io.File

sealed interface LaunchResult {
    data object Started : LaunchResult
    /** Máy chưa cài app ngoài cần thiết → UI hiện link tải + hướng dẫn. */
    data class NeedApp(val app: ExternalApp) : LaunchResult
    /** Đã mở app ngoài nhưng user cần tự chọn thư mục game trong app đó. */
    data class OpenedApp(val app: ExternalApp, val gamePath: String) : LaunchResult
    data class Failed(val message: String) : LaunchResult
}

/**
 * Chọn trình chạy theo trường "runner" của hệ máy trong cấu hình.
 * Thêm kiểu trình chạy mới: thêm 1 nhánh trong [launch].
 */
class GameLauncher(private val configRepo: ConfigRepository) {

    fun launch(activity: Activity, game: Game): LaunchResult {
        val system = game.system ?: return LaunchResult.Failed(
            if (game.needsExtract) "Game chưa được giải nén. Bấm \"Giải nén\" (nhập mật khẩu nếu có)."
            else "Chưa nhận diện được loại game này."
        )
        val entry = game.entry ?: game.dir
        return when (system.runner) {
            "libretro" -> {
                // Engine nhúng (vd. Azahar cho 3DS) khi module có sẵn cho máy này; không thì lõi libretro như cũ.
                if (system.engine == vn.aow.monika.azahar.AzaharModule.ID && vn.aow.monika.AppGraph.azahar.available() &&
                    vn.aow.monika.AppGraph.prefs.coreOverride(system.id) == null) {
                    vn.aow.monika.azahar.AzaharActivity.start(activity, entry, system.name, game.name, game.key)
                    return LaunchResult.Started
                }
                // Lõi user chọn trong Cài đặt (nếu còn trong config), không thì lõi mặc định.
                val core = vn.aow.monika.AppGraph.prefs.coreOverride(system.id)?.takeIf { it in configRepo.current.cores }
                    ?: system.core ?: return LaunchResult.Failed("Cấu hình hệ ${system.name} thiếu 'core'.")
                // Lõi mặc định không chạy được trên kiến trúc máy này (vd. melondsds chỉ arm64) → dùng lõi thay thế đầu tiên chạy được.
                val usable = if (vn.aow.monika.AppGraph.cores.supports(core)) core
                    else system.altCores.firstOrNull { it != core && vn.aow.monika.AppGraph.cores.supports(it) } ?: core
                RetroActivity.start(activity, usable, entry, system.name, game.name, system.pad, game.key, system.id)
                LaunchResult.Started
            }
            "web" -> {
                WebGameActivity.start(activity, entry, system.webPlayer ?: "html5", game.name, game.key)
                LaunchResult.Started
            }
            "apk" -> {
                // Game đã cài sẵn (cùng hoặc mới hơn bản trong gói) → mở luôn, khỏi qua màn cài.
                vn.aow.monika.apkinstall.ApkInstallFlow.installedLaunch(activity, entry)?.let { activity.startActivity(it); return LaunchResult.Started }
                // Thư mục game (có thể kèm OBB/Data) → trình cài mới; file lẻ thì đưa cả thư mục chứa nó nếu là thư mục game.
                vn.aow.monika.apkinstall.ApkInstallFlow.start(activity, if (game.dir.isDirectory && entry.isFile) game.dir else entry)
                LaunchResult.Started
            }
            "j2me" -> {
                // J2ME Loader nhúng sẵn: lần đầu cài (chuyển .jar → .dex), các lần sau chạy luôn.
                activity.startActivity(ru.playsoftware.j2meloader.J2meRuntime.openGameIntent(activity, Installer.uriFor(activity, entry)))
                LaunchResult.Started
            }
            "external" -> launchExternal(activity, system.externalApp, entry)
            else -> LaunchResult.Failed("Kiểu trình chạy '${system.runner}' chưa được hỗ trợ ở bản app này. Hãy cập nhật app.")
        }
    }

    private fun launchExternal(activity: Activity, appId: String?, entry: File): LaunchResult {
        val app = appId?.let { configRepo.current.externalApp(it) }
            ?: return LaunchResult.Failed("Cấu hình thiếu app ngoài '$appId'.")
        val pkg = ExternalApps.installedPackage(activity, app) ?: return installFromPack(activity, app)

        // Kirikiroid2 bản Aow Monika dựng (có vá): nhận thẳng đường dẫn game qua Intent, khỏi chọn thư mục trong app.
        if (pkg == KIRIKIRI_AOW_PACKAGE && entry.isFile) {
            val open = activity.packageManager.getLaunchIntentForPackage(pkg)
            if (open != null) {
                activity.startActivity(open.putExtra(KIRIKIRI_EXTRA_GAME_PATH, entry.absolutePath))
                return LaunchResult.Started
            }
        }

        if (entry.isFile) {
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(Installer.uriFor(activity, entry), "*/*")
                .setPackage(pkg)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            try {
                activity.startActivity(intent)
                return LaunchResult.Started
            } catch (_: ActivityNotFoundException) {
                // App không nhận mở file trực tiếp → mở app, user tự chọn game.
            }
        }
        val open = activity.packageManager.getLaunchIntentForPackage(pkg)
            ?: return LaunchResult.Failed("Không mở được ${app.name}.")
        activity.startActivity(open)
        return LaunchResult.OpenedApp(app, (if (entry.isFile) entry.parentFile!! else entry).absolutePath)
    }
}

const val KIRIKIRI_AOW_PACKAGE = "vn.aow.monika.kirikiri"
const val KIRIKIRI_EXTRA_GAME_PATH = "aow_game_path"

/**
 * App ngoài có gói riêng trong config (`ExternalApp.pack`, vd. Kirikiri): chưa cài thì tự lo hết —
 * chưa tải → đưa vào hàng tải theo luật mạng (≤15 MB tự tải, lớn hơn hỏi Wi-Fi/4G); đã tải → mở trình cài APK.
 * Máy không hợp gói (vd. 32-bit) hoặc gói chưa có link → quay về cách cũ (hướng dẫn tải tay).
 */
private fun installFromPack(activity: Activity, app: ExternalApp): LaunchResult {
    val pm = vn.aow.monika.pack.PackManager
    if (app.pack.isBlank() || !AppGraph.packs.supported(app.pack)) return LaunchResult.NeedApp(app)
    if (pm.needed(app.pack)) {
        pm.request(activity.applicationContext, app.pack)
        return LaunchResult.Failed("Đang tải thành phần ${app.name} (chỉ lần đầu). Xong sẽ có thông báo để cài, rồi bấm Chơi lại.")
    }
    val apk = java.io.File(AppGraph.packs.dir(app.pack), vn.aow.monika.pack.PackManager.KIRIKIRI_APK)
    return when (val r = Installer.install(activity, apk)) {
        LaunchResult.Started -> LaunchResult.Failed("Đang mở trình cài ${app.name}. Cài xong, quay lại bấm Chơi.")
        else -> r
    }
}

object ExternalApps {
    fun installedPackage(context: Context, app: ExternalApp): String? =
        app.packageNames.firstOrNull { pkg ->
            try {
                context.packageManager.getPackageInfo(pkg, 0); true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }
}

object Installer {
    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    fun installIntent(context: Context, apk: File): Intent =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uriFor(context, apk), "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)

    fun install(activity: Activity, apk: File): LaunchResult {
        if (!apk.isFile) return LaunchResult.Failed("Không tìm thấy file APK.")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
            activity.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
            )
            return LaunchResult.Failed("Hãy bật 'Cho phép cài ứng dụng' cho AowVN Monika rồi bấm Chơi lại.")
        }
        activity.startActivity(installIntent(activity, apk))
        return LaunchResult.Started
    }
}
