package vn.aow.monika.runner

import android.app.Activity
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import vn.aow.monika.R
import vn.aow.monika.ui.theme.MonikaMenuSheet
import vn.aow.monika.ui.theme.MonikaTheme
import vn.aow.monika.ui.theme.SheetAction

/**
 * Đặt Compose lên màn hình game Kirikiri. Kirikiri/cocos2d-x là Activity thường (không phải ComponentActivity) nên phải tự
 * cấp "chủ vòng đời" cho cây View thì Compose mới chạy được. Gọi [Host.resume]/[Host.pause]/[Host.destroy] theo vòng đời Activity.
 */
class ComposeHost(private val activity: Activity) : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    init { saved.performAttach(); saved.performRestore(null); registry.currentState = Lifecycle.State.CREATED }

    fun attach(parent: ViewGroup, content: @Composable () -> Unit) {
        val decor = activity.window.decorView
        decor.setViewTreeLifecycleOwner(this); decor.setViewTreeSavedStateRegistryOwner(this); decor.setViewTreeViewModelStoreOwner(this)
        parent.addView(ComposeView(activity).apply { setContent(content) }, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    fun resume() { registry.currentState = Lifecycle.State.RESUMED }
    fun pause() { registry.currentState = Lifecycle.State.STARTED }
    fun destroy() { registry.currentState = Lifecycle.State.DESTROYED; store.clear() }
}

/** Nút menu nhỏ + menu popup chung của Monika (tiếng Việt) trên màn chơi Kirikiri. */
@Composable
fun KirikiriOverlay(
    title: String,
    open: Boolean,
    fastForward: Boolean,
    onOpen: (Boolean) -> Unit,
    onGameMenu: () -> Unit,
    onFastForward: () -> Unit,
    onExit: () -> Unit,
) {
    MonikaTheme {
        Box(Modifier.fillMaxSize()) {
            GameMenuButton(open, { onOpen(!open) }, Modifier.align(Alignment.TopEnd))
            MonikaMenuSheet(
                open, { onOpen(false) }, title = title, subtitle = "Kirikiri (visual novel)",
                actions = listOf(
                    SheetAction("Chơi tiếp", R.drawable.ic_fluent_play_24_regular, highlight = true) {},
                    SheetAction("Menu game (lưu/tải/cài đặt)", R.drawable.ic_fluent_grid_24_regular, onClick = onGameMenu),
                    SheetAction(if (fastForward) "Tua nhanh thoại: ĐANG BẬT" else "Tua nhanh thoại: tắt", R.drawable.ic_fluent_arrow_clockwise_24_regular,
                        highlight = fastForward, keepOpen = true, onClick = onFastForward),
                    vn.aow.monika.ui.gameReportAction { onOpen(false) },
                    SheetAction("Thoát game", R.drawable.ic_fluent_door_arrow_left_24_regular, onClick = onExit),
                ),
            )
        }
    }
}
