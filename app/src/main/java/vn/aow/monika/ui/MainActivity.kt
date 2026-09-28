package vn.aow.monika.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import vn.aow.monika.ui.screens.FeedScreen
import vn.aow.monika.ui.screens.LibraryScreen
import vn.aow.monika.ui.screens.PostScreen
import vn.aow.monika.ui.screens.ToolsScreen
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

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.FEED, "Bài viết", Icons.Filled.Article),
    Tab(Routes.LIBRARY, "Thư viện", Icons.Filled.SportsEsports),
    Tab(Routes.TOOLS, "Trình chạy", Icons.Filled.Build),
)

@Composable
private fun MonikaNav(deepLink: String?, onDeepLinkHandled: () -> Unit) {
    val nav = rememberNavController()
    val current by nav.currentBackStackEntryAsState()

    LaunchedEffect(deepLink) {
        deepLink ?: return@LaunchedEffect
        nav.navigate(deepLink)
        onDeepLinkHandled()
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = current?.destination?.route == tab.route,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        }
    ) { padding ->
        NavHost(nav, startDestination = Routes.FEED, modifier = Modifier.padding(padding)) {
            composable(Routes.FEED) { FeedScreen(onOpen = { nav.navigate("post/${it.id}") }) }
            composable(Routes.POST) { entry -> PostScreen(entry.arguments?.getString("id").orEmpty()) { nav.popBackStack() } }
            composable(Routes.LIBRARY) { LibraryScreen() }
            composable(Routes.TOOLS) { ToolsScreen() }
        }
    }
}
