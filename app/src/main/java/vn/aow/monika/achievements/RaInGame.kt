package vn.aow.monika.achievements

import android.os.Build
import com.swordfish.libretrodroid.RetroAchievements
import vn.aow.monika.AppGraph
import vn.aow.monika.BuildConfig
import java.io.File

/** Dựng cấu hình RetroAchievements trong game cho [RetroActivity]; null = không bật (chưa đăng nhập / hệ máy chưa hỗ trợ). */
object RaInGame {
    fun config(systemId: String, game: File, hardcore: Boolean, listener: RetroAchievements.Listener): RetroAchievements.Config? {
        val creds = AppGraph.ra.creds.value ?: return null
        val token = AppGraph.ra.token() ?: return null
        val console = RaHasher.consoleFor(systemId, game) ?: return null
        return RetroAchievements.Config(
            consoleId = console,
            userAgent = "AowMonika/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE})",
            hardcore = hardcore,
            user = creds.user,
            token = token,
            listener = listener,
        )
    }
}
