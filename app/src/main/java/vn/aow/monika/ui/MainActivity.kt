package vn.aow.monika.ui

import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import vn.aow.monika.AppGraph
import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import vn.aow.monika.ui.screens.HubScreen
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
        // Màn chờ: logo M Portal trên nền than, rời đi bằng hiệu ứng phóng + mờ dần.
        installSplashScreen().setOnExitAnimationListener { splash ->
            splash.view.animate().alpha(0f).scaleX(1.08f).scaleY(1.08f).setDuration(260)
                .withEndAction { splash.remove() }.start()
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        askPermissions()
        handleIntent(intent)
        setContent { MonikaTheme { MonikaNav(deepLink.value) { deepLink.value = null } } }
    }

    override fun onResume() {
        super.onResume()
        // Vừa chơi xong → nhặt giờ chơi do tiến trình game gửi về, rồi cộng phiên ước lượng (game Java / app ngoài).
        AppGraph.prefs.mergeGameEvents()
        AppGraph.prefs.endSession()
        // Phiên game trước chết bất thường? (lõi native sập, hết RAM, treo…) → tạo báo cáo + hỏi người chơi.
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) { runCatching {
            vn.aow.monika.diag.Diagnostics.collect(applicationContext)?.let { r ->
                val cr = AppGraph.config.current.crash
                vn.aow.monika.diag.Diagnostics.autoSend(applicationContext, AppGraph.http, r, cr.endpoint, cr.autoSend, AppGraph.prefs.autoSendCrash)
            }
        } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(EXTRA_POST_ID)?.let { deepLink.value = "post/$it" }
        if (intent?.getBooleanExtra(EXTRA_OPEN_LIBRARY, false) == true) deepLink.value = Routes.EMULATOR
        // "Mở bằng Aow Monika": đưa file vào Thư viện (nhận diện hệ máy), không chạy thẳng.
        val uris = when (intent?.action) {
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            Intent.ACTION_SEND -> listOfNotNull(
                if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
            )
            else -> emptyList()
        }
        if (uris.isNotEmpty()) {
            AppGraph.pendingImports.value = uris
            deepLink.value = Routes.EMULATOR
            intent?.action = null // Xoay màn hình không nhận lại lần nữa.
        }
    }

    private fun askPermissions() {
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT <= 29) add(Manifest.permission.WRITE_EXTERNAL_STORAGE) // Android 9-10: cần để chép OBB/Data
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
    DockItem("Thư viện", R.drawable.ic_fluent_library_24_regular, R.drawable.ic_fluent_library_24_filled),
    // Nút cuối = menu popup (Tải xuống, Cài đặt, Thông báo…), không phải 1 tab.
    DockItem("Menu", R.drawable.ic_fluent_grid_24_regular, R.drawable.ic_fluent_grid_24_filled),
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
    // Tab cuối: 0 = Tải xuống, 1 = Cài đặt (giữ khi chuyển tab).
    val hubSegment = androidx.compose.runtime.saveable.rememberSaveable(saver = androidx.compose.runtime.saveable.Saver({ it.intValue }, { androidx.compose.runtime.mutableIntStateOf(it) })) { androidx.compose.runtime.mutableIntStateOf(0) }

    var menuOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val inboxTick by Inbox.tick.collectAsState()
    val unread = remember(inboxTick, route) { Inbox.unread().size }
    val go: (String) -> Unit = { r ->
        when (r) {
            Routes.SETTINGS -> { hubSegment.intValue = 1; nav.goTab(Routes.DOWNLOADS) }
            in Routes.tabs -> { if (r == Routes.DOWNLOADS) hubSegment.intValue = 0; nav.goTab(r) }
            else -> nav.navigate(r)
        }
    }

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
                    onGo = go,
                )
            }
            composable(Routes.GAMES) { GamesScreen(onOpen = { nav.navigate("post/${it.id}") }) }
            // Tab Tìm kiếm riêng: lịch sử, đã xem gần đây, gợi ý tức thì khi gõ.
            composable(Routes.SEARCH) { vn.aow.monika.ui.screens.SearchScreen(onOpenPost = { nav.navigate("post/${it.id}") }, onOpenLibrary = { nav.goTab(Routes.EMULATOR) }) }
            composable(Routes.EMULATOR) { LibraryScreen(onSettings = { hubSegment.intValue = 1; nav.goTab(Routes.DOWNLOADS) }) }
            composable(Routes.DOWNLOADS) { HubScreen(hubSegment, onOpenLibrary = { nav.goTab(Routes.EMULATOR) }) }
            composable(Routes.SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.POST) { entry -> PostScreen(entry.arguments?.getString("id").orEmpty(), onGo = go) { nav.popBackStack() } }
        }
        // Menu nổi: chỉ hiện ở 5 tab chính; đọc bài / cài đặt thì trượt xuống ẩn đi.
        FloatingDock(
            items = dockItems.mapIndexed { i, d -> if (i == dockItems.lastIndex && unread > 0) d.copy(badge = if (unread > 9) "9+" else "$unread") else d },
            selected = if (menuOpen) dockItems.lastIndex else Routes.tabs.indexOf(route),
            onSelect = { i -> if (i == dockItems.lastIndex) menuOpen = !menuOpen else { menuOpen = false; nav.goTab(Routes.tabs[i]) } },
            modifier = Modifier.align(Alignment.BottomCenter).dockOffset(route in Routes.tabs && !menuOpen && vn.aow.monika.ui.theme.SheetsOpen.count.intValue == 0),
        )
        CrashUi()
        AppMenuSheet(menuOpen, { menuOpen = false }, onGo = { menuOpen = false; go(it) }, onOpenPost = { menuOpen = false; nav.navigate("post/$it") })
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
