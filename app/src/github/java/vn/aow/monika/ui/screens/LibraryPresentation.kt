package vn.aow.monika.ui.screens

import android.net.Uri
import android.content.Intent
import android.provider.Settings
import vn.aow.monika.AppGraph
import vn.aow.monika.library.Game

/** Android quyết định quyền; không dùng resultCode để giả định người dùng đã cho phép. */
internal fun allFilesAccessIntent(context: android.content.Context, appSpecific: Boolean = true): Intent =
    if (appSpecific) Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + context.packageName))
    else Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)

internal const val FILTER_ALL = "Tất cả"
internal const val FILTER_RECENT = "Chơi gần đây"
internal const val FILTER_FREQUENT = "Chơi thường xuyên"
internal const val FILTER_SYSTEM = "__system"

/** "2 giờ 5 phút" / "12 phút" / "dưới 1 phút". */
internal fun formatPlayTime(ms: Long): String {
    val min = ms / 60_000
    return when {
        min < 1 -> "dưới 1 phút"
        min < 60 -> "$min phút"
        min % 60 == 0L -> "${min / 60} giờ"
        else -> "${min / 60} giờ ${min % 60} phút"
    }
}

/** Lõi libretro cần cho các game (theo lõi user chọn hoặc lõi mặc định của hệ). */
internal fun coreIdsOf(games: List<Game>): List<String> = games.mapNotNull { g ->
    val sys = g.system?.takeIf { it.runner == "libretro" } ?: return@mapNotNull null
    AppGraph.prefs.coreOverride(sys.id)?.takeIf { it in AppGraph.config.current.cores } ?: sys.core
}.distinct()
