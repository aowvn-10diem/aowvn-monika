package vn.aow.monika.ui.screens

import android.app.Activity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import vn.aow.monika.AppGraph
import vn.aow.monika.library.Game
import vn.aow.monika.runner.LaunchResult
import vn.aow.monika.ui.theme.*

/** Cùng một cửa xác nhận cho Thư viện, Trang chủ và Tìm kiếm. */
@Composable
internal fun rememberGameLaunch(onResult: (Game, LaunchResult) -> Unit): (Game) -> Unit {
    val activity = LocalContext.current as Activity
    val result by rememberUpdatedState(onResult)
    var pending by remember { mutableStateOf<Pair<Game, String>?>(null) }
    fun launch(game: Game, confirmed: Boolean) {
        when (val outcome = AppGraph.launcher.launch(activity, game, experimentalConfirmed = confirmed)) {
            is LaunchResult.NeedExperimentalConfirmation -> pending = game to outcome.systemName
            else -> result(game, outcome)
        }
    }
    pending?.let { (game, systemName) ->
        ExperimentalGameWarning(systemName,
            onContinue = { pending = null; launch(game, confirmed = true) }, onCancel = { pending = null })
    }
    return { launch(it, confirmed = false) }
}

@Composable
internal fun ExperimentalGameWarning(systemName: String, onContinue: () -> Unit, onCancel: () -> Unit) {
    val c = Monika.colors
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("$systemName · Thử nghiệm", style = Monika.type.sectionTitle, color = c.text) },
        text = { Text("$systemName đang thử nghiệm, có thể chưa chạy được game của bạn. Gặp lỗi hãy bấm Báo lỗi",
            style = Monika.type.body, color = c.textSecondary) },
        confirmButton = { GradientButton("Tiếp tục", onContinue, height = 44.dp) },
        dismissButton = { DarkButton("Hủy", onCancel) },
        containerColor = c.surface, shape = Radius.large,
    )
}

@Composable
internal fun ExperimentalTag(experimental: Boolean, onDark: Boolean = false) {
    if (experimental) Tag("Thử nghiệm", onDark = onDark)
}

/** Cờ config hiện hành áp dụng cả thẻ game đã được cache trước khi cập nhật. */
internal fun gameIsExperimental(game: Game): Boolean {
    val system = game.system ?: return false
    return AppGraph.config.current.systems.firstOrNull { it.id == system.id }?.experimental ?: system.experimental
}
