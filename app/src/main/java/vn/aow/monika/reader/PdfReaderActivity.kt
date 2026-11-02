package vn.aow.monika.reader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vn.aow.monika.AppGraph
import vn.aow.monika.ui.theme.Monika
import vn.aow.monika.ui.theme.MonikaTheme
import vn.aow.monika.ui.theme.Radius
import vn.aow.monika.ui.theme.primaryGradient
import java.io.File

/**
 * Màn đọc PDF đứng độc lập (V142): nhận đường dẫn tệp qua intent, mở bằng `PdfRenderer`,
 * nhớ trang đang đọc dở trong `Prefs`, báo lỗi rõ khi PDF hỏng/có mật khẩu.
 *
 * Chưa nối vào mục "Đọc" của Thư viện — `LibraryScreen.kt` đang bị PR #434 giữ;
 * nối sau khi #434 gộp bằng [start].
 */
class PdfReaderActivity : ComponentActivity() {

    /** Sách đang mở để đóng (giải phóng PdfRenderer + file descriptor) khi rời màn. */
    @Volatile private var openedBook: PdfBook? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val path = intent?.getStringExtra(EXTRA_PATH).orEmpty()
        val name = intent?.getStringExtra(EXTRA_NAME)?.takeIf { it.isNotBlank() } ?: File(path).name
        setContent {
            MonikaTheme {
                val context = LocalContext.current
                var state by remember(path) { mutableStateOf<ReaderOpenState>(ReaderOpenState.Opening) }
                LaunchedEffect(path) {
                    state = if (path.isBlank()) {
                        ReaderOpenState.Failed(ReaderError.MISSING)
                    } else {
                        withContext(Dispatchers.IO) {
                            PdfBookOpener.open(File(path)).fold(
                                onSuccess = { book -> openedBook = book; ReaderOpenState.Ready(book) },
                                onFailure = { ReaderOpenState.Failed((it as? ReaderException)?.error ?: ReaderError.CORRUPT) },
                            )
                        }
                    }
                }
                BackHandler { finish() }
                ReaderScreen(
                    title = name,
                    book = (state as? ReaderOpenState.Ready)?.book,
                    error = (state as? ReaderOpenState.Failed)?.error,
                    initialPage = AppGraph.prefs.readingPage(path),
                    style = monikaReaderStyle(),
                    onPageChanged = { page -> AppGraph.prefs.setReadingPage(path, page) },
                    onClose = { finish() },
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Đóng sách: không giữ handle của tệp sau khi rời màn đọc.
        openedBook?.let { runCatching { it.close() } }
        openedBook = null
    }

    companion object {
        const val EXTRA_PATH = "reader_path"
        const val EXTRA_NAME = "reader_name"

        /** Route riêng của màn đọc, để sau này nối vào điều hướng của app. */
        const val ROUTE = "reader/{path}"

        fun route(path: String): String = "reader/" + android.net.Uri.encode(path)

        /** Mở màn đọc cho một tệp PDF. */
        fun start(context: Context, file: File) {
            context.startActivity(
                Intent(context, PdfReaderActivity::class.java)
                    .putExtra(EXTRA_PATH, file.absolutePath)
                    .putExtra(EXTRA_NAME, file.name),
            )
        }
    }
}

/** Trạng thái mở tài liệu: đang mở · đọc được · lỗi đã phân loại. */
private sealed interface ReaderOpenState {
    data object Opening : ReaderOpenState
    class Ready(val book: PdfBook) : ReaderOpenState
    class Failed(val error: ReaderError) : ReaderOpenState
}

/** Bảng style của màn đọc lấy đúng token "Aow Monika". */
@androidx.compose.runtime.Composable
private fun monikaReaderStyle(): ReaderStyle {
    val c = Monika.colors
    val t = Monika.type
    return ReaderStyle(
        background = c.bg,
        surface = c.surface,
        text = c.text,
        textSecondary = c.textSecondary,
        chip = c.chip,
        chipText = c.chipText,
        accent = c.accentCoral,
        accentGradient = primaryGradient(),
        onAccent = Color.White,
        card = Radius.large,
        pill = Radius.pill,
        title = t.cardTitle,
        body = t.body,
        caption = t.caption,
        button = t.button,
    )
}
