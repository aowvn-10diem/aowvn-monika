package vn.aow.monika.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import vn.aow.monika.R
import vn.aow.monika.ui.screens.DownloadsScreen
import vn.aow.monika.ui.screens.GamesScreen
import vn.aow.monika.ui.screens.HomeScreen
import vn.aow.monika.ui.screens.LibraryScreen
import vn.aow.monika.ui.screens.PostScreen
import vn.aow.monika.ui.screens.SettingsScreen
import vn.aow.monika.ui.theme.DockItem
import vn.aow.monika.ui.theme.FloatingDock
import vn.aow.monika.ui.theme.Monika
import vn.aow.monika.ui.theme.MonikaMotion
import vn.aow.monika.ui.theme.MonikaTheme
import vn.aow.monika.ui.theme.dockOffset

class MainActivity : ComponentActivity() {
    /** Yêu cầu mở từ thông báo: mã bài viết hoặc "emulator". */
    private val deepLink = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        askPermissions()
        handleIntent(intent)
        setContent { MonikaTheme { MonikaNav(deepLink.value) { deepLink.value = null } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(EXTRA_POST_ID)?.let { deepLink.value = "post/$it" }
        if (intent?.getBooleanExtra(EXTRA_OPEN_LIBRARY, false) == true) deepLink.value = Routes.EMULATOR
    }

    private fun askPermissions() {
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT <= 28) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (perms.isNotEmpty()) {
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}.launch(perms.toTypedArray())
        }
    }

    companion object {
        const val EXTRA_POST_ID = "post_id"
        const val EXTRA_OPEN_LIBRARY = "open_library"
    }
}

object Routes {
    const val HOME = "home"
    const val GAMES = "games"
    const val SEARCH = "search"
    const val EMULATOR = "emulator"
    const val DOWNLOADS = "downloads"
    const val SETTINGS = "settings"
    const val POST = "post/{id}"
    val tabs = listOf(HOME, GAMES, SEARCH, EMULATOR, DOWNLOADS)
}

private val dockItems = listOf(
    DockItem("Trang chủ", R.drawable.ic_fluent_home_24_regular, R.drawable.ic_fluent_home_24_filled),
    DockItem("Game", R.drawable.ic_fluent_games_24_regular, R.drawable.ic_fluent_games_24_filled),
    DockItem("Tìm kiếm", R.drawable.ic_fluent_search_24_regular, R.drawable.ic_fluent_search_24_filled),
    DockItem("Giả lập", R.drawable.ic_fluent_xbox_controller_24_regular, R.drawable.ic_fluent_xbox_controller_24_filled),
    DockItem("Tải xuống", R.drawable.ic_fluent_arrow_download_24_regular, R.drawable.ic_fluent_arrow_download_24_filled),
)

/** Điều hướng tab qua menu nổi, giữ trạng thái từng tab. */
fun NavHostController.goTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun MonikaNav(deepLink: String?, onDeepLinkHandled: () -> Unit) {
    val nav = rememberNavController()
    val current by nav.currentBackStackEntryAsState()
    val route = current?.destination?.route
    val motion = Monika.motion

    LaunchedEffect(deepLink) {
        deepLink ?: return@LaunchedEffect
        if (deepLink in Routes.tabs) nav.goTab(deepLink) else nav.navigate(deepLink)
        onDeepLinkHandled()
    }

    Box(Modifier.fillMaxSize()) {
        NavHost(
            nav, startDestination = Routes.HOME, modifier = Modifier.fillMaxSize(),
            enterTransition = { enter(motion) }, exitTransition = { exit(motion) },
            popEnterTransition = { enter(motion) }, popExitTransition = { exit(motion) },
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenPost = { nav.navigate("post/${it.id}") },
                    onGo = { r -> if (r in Routes.tabs) nav.goTab(r) else nav.navigate(r) },
                )
            }
            composable(Routes.GAMES) { GamesScreen(onOpen = { nav.navigate("post/${it.id}") }) }
            // Tab Tìm kiếm: cùng màn Game nhưng bật sẵn bàn phím ở ô tìm.
            composable(Routes.SEARCH) { GamesScreen(onOpen = { nav.navigate("post/${it.id}") }, focusSearch = true) }
            composable(Routes.EMULATOR) { LibraryScreen(onSettings = { nav.navigate(Routes.SETTINGS) }) }
            composable(Routes.DOWNLOADS) { DownloadsScreen() }
            composable(Routes.SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.POST) { entry -> PostScreen(entry.arguments?.getString("id").orEmpty()) { nav.popBackStack() } }
        }
        // Menu nổi: chỉ hiện ở 5 tab chính; đọc bài / cài đặt thì trượt xuống ẩn đi.
        FloatingDock(
            items = dockItems,
            selected = Routes.tabs.indexOf(route),
            onSelect = { nav.goTab(Routes.tabs[it]) },
            modifier = Modifier.align(Alignment.BottomCenter).dockOffset(route in Routes.tabs),
        )
    }
}

private fun tabIndex(entry: NavBackStackEntry) = Routes.tabs.indexOf(entry.destination.route)

/**
 * Chuyển cảnh theo cấu hình máy:
 * - Tab ↔ tab: trượt ngang theo hướng tab (máy khỏe) / mờ dần (máy yếu).
 * - Mở trang con (bài viết, cài đặt): trượt lên + phóng nhẹ.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.enter(m: MonikaMotion): EnterTransition {
    if (!m.enabled) return EnterTransition.None
    val from = tabIndex(initialState)
    val to = tabIndex(targetState)
    val fade = fadeIn(tween(m.normal, easing = m.easing))
    if (!m.rich) return fade
    return when {
        from >= 0 && to >= 0 -> fade + slideInHorizontally(tween(m.slow, easing = m.easing)) { w -> if (to > from) w / 6 else -w / 6 }
        to < 0 -> fade + slideInVertically(tween(m.slow, easing = m.easing)) { h -> h / 10 } + scaleIn(tween(m.slow, easing = m.easing), 0.96f)
        else -> fade
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.exit(m: MonikaMotion): ExitTransition {
    if (!m.enabled) return ExitTransition.None
    val from = tabIndex(initialState)
    val to = tabIndex(targetState)
    val fade = fadeOut(tween(m.fast, easing = m.easing))
    if (!m.rich) return fade
    return when {
        from >= 0 && to >= 0 -> fade + slideOutHorizontally(tween(m.slow, easing = m.easing)) { w -> if (to > from) -w / 6 else w / 6 }
        from < 0 -> fade + slideOutVertically(tween(m.normal, easing = m.easing)) { h -> h / 10 }
        else -> fade
    }
}
