package vn.aow.monika.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import vn.aow.monika.R
import vn.aow.monika.ui.screens.FeedScreen
import vn.aow.monika.ui.screens.LibraryScreen
import vn.aow.monika.ui.screens.PostScreen
import vn.aow.monika.ui.screens.ToolsScreen
import vn.aow.monika.ui.theme.Fluent
import vn.aow.monika.ui.theme.FluentDivider
import vn.aow.monika.ui.theme.MonikaTheme

class MainActivity : ComponentActivity() {
    /** Yêu cầu mở từ thông báo: mã bài viết hoặc "library". */
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
        if (intent?.getBooleanExtra(EXTRA_OPEN_LIBRARY, false) == true) deepLink.value = Routes.LIBRARY
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
    const val FEED = "feed"
    const val LIBRARY = "library"
    const val TOOLS = "tools"
    const val POST = "post/{id}"
}

private data class Tab(val route: String, val label: String, @DrawableRes val icon: Int, @DrawableRes val iconSelected: Int)

private val tabs = listOf(
    Tab(Routes.FEED, "Bài viết", R.drawable.ic_fluent_news_24_regular, R.drawable.ic_fluent_news_24_filled),
    Tab(Routes.LIBRARY, "Thư viện", R.drawable.ic_fluent_games_24_regular, R.drawable.ic_fluent_games_24_filled),
    Tab(Routes.TOOLS, "Trình chạy", R.drawable.ic_fluent_wrench_24_regular, R.drawable.ic_fluent_wrench_24_filled),
)

@Composable
private fun MonikaNav(deepLink: String?, onDeepLinkHandled: () -> Unit) {
    val nav = rememberNavController()
    val current by nav.currentBackStackEntryAsState()
    val c = Fluent.colors

    LaunchedEffect(deepLink) {
        deepLink ?: return@LaunchedEffect
        nav.navigate(deepLink)
        onDeepLinkHandled()
    }

    Column(Modifier.fillMaxSize().background(c.background2)) {
        NavHost(nav, startDestination = Routes.FEED, modifier = Modifier.weight(1f)) {
            composable(Routes.FEED) { FeedScreen(onOpen = { nav.navigate("post/${it.id}") }) }
            composable(Routes.POST) { entry -> PostScreen(entry.arguments?.getString("id").orEmpty()) { nav.popBackStack() } }
            composable(Routes.LIBRARY) { LibraryScreen() }
            composable(Routes.TOOLS) { ToolsScreen() }
        }
        // Thanh tab dưới kiểu Fluent: nền trung tính, viền trên, mục chọn đổi icon đặc + màu thương hiệu.
        FluentDivider()
        Row(Modifier.fillMaxWidth().background(c.background1).navigationBarsPadding().height(56.dp)) {
            tabs.forEach { tab ->
                val selected = current?.destination?.route == tab.route
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable(role = Role.Tab) {
                        nav.navigate(tab.route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val tint = if (selected) c.brandForeground else c.foreground3
                    Icon(painterResource(if (selected) tab.iconSelected else tab.icon), null, Modifier.size(24.dp), tint = tint)
                    Text(tab.label, style = Fluent.type.caption2, color = tint)
                }
            }
        }
    }
}
