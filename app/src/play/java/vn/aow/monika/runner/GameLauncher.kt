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
    data class NeedExperimentalConfirmation(val systemName: String) : LaunchResult
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

    fun launch(activity: Activity, game: Game, experimentalConfirmed: Boolean = false): LaunchResult {
        if (vn.aow.monika.library.GameSaveTransfer.busy) return LaunchResult.Failed("Đang chuyển save. Hãy chờ xong rồi mở game.")
        // V202: nhả bộ đệm tiến trình chính trước khi nhường RAM cho tiến trình game.
        vn.aow.monika.AppGraph.trimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        val system = game.system ?: return LaunchResult.Failed(
            if (game.needsExtract) "Game chưa được giải nén. Bấm \"Giải nén\" (nhập mật khẩu nếu có)."
            else "Chưa nhận diện được loại game này."
        )
        // Đọc cờ từ config hiện hành, kể cả thẻ game đang dùng bản system cũ trong cache.
        val currentSystem = configRepo.current.systems.firstOrNull { it.id == system.id } ?: system
        if (currentSystem.experimental && !experimentalConfirmed) return LaunchResult.NeedExperimentalConfirmation(
            if (currentSystem.id == "kirikiri") "Kirikiri" else currentSystem.name)
        val entry = game.entry ?: game.dir
        return when (system.runner) {
            "libretro" -> {
                // Engine nhúng (vd. Azahar cho 3DS) khi module có sẵn cho máy này; không thì lõi libretro như cũ.
                val azaharRoute = system.engine == vn.aow.monika.azahar.AzaharModule.ID &&
                    vn.aow.monika.AppGraph.azahar.available() && vn.aow.monika.AppGraph.prefs.coreOverride(system.id) == null
                // `.cia` là gói cài đặt: chỉ đường Azahar cài được, đưa vào lõi libretro là `core-error` (#301, #610).
                ciaBlockedReason(entry, azaharRoute, vn.aow.monika.AppGraph.azahar.available())?.let { return LaunchResult.Failed(it) }
                if (azaharRoute) {
                    vn.aow.monika.azahar.AzaharActivity.start(activity, entry, system.name, game.name, game.key)
                    return LaunchResult.Started
                }
                val usable = pickCore(
                    vn.aow.monika.AppGraph.prefs.coreOverride(system.id), configRepo.current.cores.keys, system.core, system.altCores,
                ) { vn.aow.monika.AppGraph.cores.supports(it) } ?: return LaunchResult.Failed("Cấu hình hệ ${system.name} thiếu 'core'.")
                RetroActivity.start(activity, usable, entry, system.name, game.name, system.pad, game.key, system.id)
                LaunchResult.Started
            }
            "web" -> {
                WebGameActivity.start(activity, entry, system.webPlayer ?: "html5", game.name, game.key)
                LaunchResult.Started
            }
            "j2me" -> {
                // J2ME Loader nhúng sẵn: lần đầu cài (chuyển .jar → .dex), các lần sau chạy luôn.
                // V135: đoán màn hình từ JAD/manifest/ảnh trong jar; J2ME Loader chỉ áp khi game chưa có config.
                val screen = J2meScreenDetector.detect(entry.takeIf { it.isFile }, configRepo.current.j2meScreen)
                activity.startActivity(
                    ru.playsoftware.j2meloader.J2meRuntime.openGameIntent(
                        activity, Installer.uriFor(activity, entry), screen.width, screen.height,
                    )
                )
                LaunchResult.Started
            }
            "external" -> {
                // Engine nhúng trước (E0.2). Hệ đã tắt app ngoài mà máy không chạy được engine → báo rõ, không mở app ngoài (E0.3).
                val route = EngineRoutes.usable(system.engine)
                when {
                    route != null -> launchEmbedded(activity, route, system.engine!!, game, entry)
                    !system.allowExternalApp -> LaunchResult.Failed(
                        if (system.engine != null && EngineRoutes.all.containsKey(system.engine))
                            "Máy này (${android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "?"}) chưa chạy được ${system.name} trong Monika."
                        else "Chưa có engine ${system.name} nhúng trong bản app này. Hãy cập nhật app."
                    )
                    else -> LaunchResult.Failed("Thành phần cho hệ máy này chưa được cài từ Google Play.")
                }
            }
            else -> LaunchResult.Failed("Kiểu trình chạy '${system.runner}' chưa được hỗ trợ ở bản app này. Hãy cập nhật app.")
        }
    }

    /** Engine Kirikiri nhúng: chưa có gói → đưa vào hàng tải theo luật mạng (≤15 MB tự tải, lớn hơn hỏi Wi-Fi/4G); có rồi → chạy luôn. */
    /** Một chạm là chơi: màn chuẩn bị tự tải gói (quy tắc mạng chung), xin quyền nếu cần, rồi tự mở game. */
    private fun launchEmbedded(activity: Activity, route: EngineRoute, engine: String, game: Game, entry: File): LaunchResult {
        EnginePrepActivity.start(activity, engine, entry, game.name, game.key)
        return LaunchResult.Started
    }

    companion object {
        /**
         * Tệp `.cia` là **gói cài đặt** (game, bản cập nhật, DLC), không phải ROM — chỉ đường Azahar cài được.
         * Đưa thẳng vào lõi libretro (citra) thì `retro_load_game` thất bại → `core-error` (#301, #610).
         * Trả về thông điệp cho người dùng; null = được chạy.
         */
        internal fun ciaBlockedReason(entry: File, azaharRoute: Boolean, azaharInstall: Boolean): String? =
            if (!azaharRoute && vn.aow.monika.azahar.AzaharCia.isCia(entry.path))
                // Lối cài thật: Cài đặt → nhóm "Nintendo 3DS" → "Chọn file .cia để cài".
                // Nhóm đó bị gate bởi AppGraph.azahar.available() (`if (... ) SettingGroup("n3ds"...)`) nên chỉ
                // được chỉ vào khi máy thật sự thấy nhóm; máy thiếu module Azahar thì chỉ nêu việc làm được.
                if (azaharInstall)
                    "Tệp .cia là gói cài đặt, không phải ROM nên không chạy trực tiếp được. Hãy cài ở Cài đặt → Nintendo 3DS → \"Chọn file .cia để cài\"."
                else
                    "Tệp .cia là gói cài đặt, không phải ROM nên không chạy trực tiếp được. Máy này chưa cài được .cia — hãy dùng bản .3ds/.cci/.cxi."
            else null

        /**
         * Chọn lõi libretro: lõi user chọn trong Cài đặt (nếu còn trong cấu hình) → lõi mặc định của hệ.
         * Lõi đã chọn không chạy được trên máy này (vd. melondsds chỉ arm64) → lấy lõi thay thế đầu tiên chạy được.
         * Trả null khi hệ không có lõi nào.
         */
        internal fun pickCore(override: String?, configured: Collection<String>, defaultCore: String?, altCores: List<String>, supports: (String) -> Boolean): String? {
            val core = override?.takeIf { it in configured } ?: defaultCore ?: return null
            return if (supports(core)) core else altCores.firstOrNull { it != core && supports(it) } ?: core
        }
    }
}

object Installer {
    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)

}
