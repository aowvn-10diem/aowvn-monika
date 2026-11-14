package vn.aow.monika.reader

import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileNotFoundException

/** V142: màn đọc PDF — nhiều trang, chuyển trang, mở lại đúng trang cũ, PDF lỗi không crash. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class ReaderScreenTest {

    @get:Rule val rule = createComposeRule()

    private fun style() = ReaderStyle(
        background = Color(0xFFFFFBF6),
        surface = Color.White,
        text = Color(0xFF181719),
        textSecondary = Color(0xFF6B6560),
        chip = Color(0xFFF1ECE5),
        chipText = Color(0xFF181719),
        accent = Color(0xFFFF806E),
        accentGradient = SolidColor(Color(0xFFFF806E)),
        onAccent = Color.White,
        title = TextStyle(fontSize = 17.sp),
        body = TextStyle(fontSize = 15.sp),
        caption = TextStyle(fontSize = 12.sp),
        button = TextStyle(fontSize = 16.sp),
        card = RoundedCornerShape(24.dp),
        pill = RoundedCornerShape(percent = 50),
    )

    private fun context(): Context = RuntimeEnvironment.getApplication()

    @Before fun cleanProgress() {
        context().getSharedPreferences("monika", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun composeScreen(
        book: PdfBook?,
        error: ReaderError? = null,
        initialPage: Int = 0,
        onPageChanged: (Int) -> Unit = {},
        onClose: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        rule.setContent {
            ReaderScreen(
                title = "Tài liệu mẫu",
                book = book,
                error = error,
                initialPage = initialPage,
                style = style(),
                onPageChanged = onPageChanged,
                onClose = onClose,
                onRetry = onRetry,
            )
        }
    }

    /**
     * Chờ một câu chỉ hiện **sau khi dựng trang** rồi mới kiểm.
     *
     * Trang dựng trên [PdfRenderThread] (luồng riêng), nên `waitForIdle` của Compose không chờ
     * việc đó: ngay sau `performClick` màn còn đang Loading. Máy nhanh thì kịp, máy chậm (CI)
     * thì chưa — đây là nguồn flake của #618. Đợi theo điều kiện thay vì ngủ một khoảng đoán mò.
     */
    private fun awaitText(text: String) {
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(text).assertExists()
    }

    @Test fun zeroPageBookShowsAnErrorInsteadOfAnEndlessSpinner() {
        var retried = 0
        composeScreen(FakeBook(pageCount = 0), onRetry = { retried++ })

        // Không còn spinner: hiện khối lỗi có loại + nút thử lại/đóng.
        rule.onNodeWithText("Chưa đọc được tài liệu").assertExists()
        rule.onNodeWithText(ReaderError.CORRUPT.message).assertExists()
        rule.onNodeWithText("Thử lại").assertExists()
        rule.onNodeWithText("Đóng").assertExists()
        rule.onNodeWithText("Thử lại").performClick()
        assertEquals(1, retried)
    }

    @Test fun pageRenderFailureShowsThePageErrorAndCanBeRetried() {
        // Trang 2 dựng lỗi: màn phải báo lỗi trang đó (không quay spinner mãi), vẫn đi tiếp được.
        val book = FakeBook(pageCount = 3, failOnPage = 1)
        composeScreen(book)
        rule.onNodeWithText("Trang 1/3").assertExists()

        rule.onNodeWithText("Sau").performClick()
        awaitText("Không dựng được trang 2")
        rule.onNodeWithText(ReaderError.CORRUPT.message).assertExists()
        rule.onNodeWithText("Thử lại").performClick() // thử lại vẫn lỗi nhưng không treo
        awaitText("Không dựng được trang 2")

        rule.onNodeWithText("Sau").performClick()
        rule.onNodeWithText("Trang 3/3").assertExists()
    }

    @Test fun pageRenderFailureIsReportedForDiagnostics() {
        val reports = mutableListOf<String>()
        ReaderDiag.onError = { what, _ -> reports += what }
        try {
            composeScreen(FakeBook(pageCount = 2, failOnPage = 0))
            // Lỗi báo từ luồng dựng trang → chờ điều kiện, `waitForIdle` không chờ luồng đó.
            rule.waitUntil(timeoutMillis = 5_000) { reports.any { it == "dung-trang-loi" } }
            assertTrue(
                "Lỗi dựng trang phải được ghi nhận bằng mã (không kèm tên tệp), có: $reports",
                reports.any { it == "dung-trang-loi" },
            )
        } finally {
            ReaderDiag.onError = null
        }
    }

    @Test fun manyPagesNavigateForwardAndBackWithThePageLabel() {
        val book = FakeBook(pageCount = 5)
        val changed = mutableListOf<Int>()
        composeScreen(book, onPageChanged = { changed += it })

        rule.onNodeWithText("Tài liệu mẫu").assertExists()
        rule.onNodeWithText("Trang 1/5").assertExists()

        rule.onNodeWithText("Sau").performClick()
        rule.onNodeWithText("Trang 2/5").assertExists()
        rule.onNodeWithText("Sau").performClick()
        rule.onNodeWithText("Trang 3/5").assertExists()
        rule.onNodeWithText("Trước").performClick()
        rule.onNodeWithText("Trang 2/5").assertExists()

        assertEquals(listOf(1, 2, 1), changed)
        assertTrue("Trang phải được dựng", book.openedPages.isNotEmpty())
    }

    @Test fun reopeningTheSameDocumentStartsAtTheSavedPage() {
        val key = "/storage/Download/AowVN Monika/Game/sách/Bài giảng.pdf"
        val progress = ReadingProgress(context())
        progress.setPage(key, 3)

        // Mở lại: nơi gọi truyền đúng trang đã lưu từ Prefs.
        composeScreen(FakeBook(pageCount = 6), initialPage = progress.page(key))
        rule.onNodeWithText("Trang 4/6").assertExists()

        rule.onNodeWithText("Sau").performClick()
        progress.setPage(key, progress.page(key) + 1)
        rule.onNodeWithText("Trang 5/6").assertExists()
        assertEquals("Lần mở sau phải vào đúng trang 5", 4, progress.page(key))
    }

    @Test fun corruptPdfShowsAReadableErrorAndNeverCrashes() {
        val file = File(context().cacheDir, "hong.pdf").apply { writeText("day khong phai pdf") }
        val opened = PdfRendererBook.open(file)
        assertTrue(opened.isFailure)
        val error = (opened.exceptionOrNull() as ReaderException).error
        assertEquals(
            "PdfRenderer không dựng được tệp rác → phải báo hỏng, không ném ra ngoài",
            ReaderError.CORRUPT, error,
        )

        var closed = 0
        composeScreen(book = null, error = error, onClose = { closed++ })
        rule.onNodeWithText("Chưa đọc được tài liệu").assertExists()
        rule.onNodeWithText(ReaderError.CORRUPT.message).assertExists()
        rule.onNodeWithText("Đóng").performClick()
        assertEquals(1, closed)
    }

    @Test fun missingFileIsReportedAsMissing() {
        val opened = PdfRendererBook.open(File(context().cacheDir, "khong-co.pdf"))
        assertTrue(opened.isFailure)
        assertEquals(ReaderError.MISSING, (opened.exceptionOrNull() as ReaderException).error)
    }

    @Test fun systemErrorsAreMappedToReadableKinds() {
        assertEquals(ReaderError.ENCRYPTED, PdfRendererBook.errorFor(SecurityException("cần mật khẩu")))
        assertEquals(ReaderError.UNSUPPORTED, PdfRendererBook.errorFor(UnsatisfiedLinkError("thiếu thư viện")))
        assertEquals(ReaderError.CORRUPT, PdfRendererBook.errorFor(IllegalStateException("hỏng")))
        assertEquals(ReaderError.MISSING, PdfRendererBook.errorFor(FileNotFoundException("mất tệp")))
    }

    @Test fun progressKeyIsStableAcrossPathSeparatorsAndKeepsThePage() {
        val progress = ReadingProgress(context())
        progress.setPage("C:\\Docs\\Bài giảng.pdf", 7)
        assertEquals(7, progress.page("C:/Docs/Bài giảng.pdf"))
        assertEquals(7, progress.page("C:\\Docs\\Bài giảng.pdf"))
        assertNotNull(progress.lastName("C:/Docs/Bài giảng.pdf"))
        assertEquals("Bài giảng.pdf", progress.lastName("C:/Docs/Bài giảng.pdf"))
        assertEquals(0, progress.page("C:/Docs/khac.pdf"))
    }

    @Test fun unreadablePageDoesNotBreakNavigation() {
        val book = FakeBook(pageCount = 3, failOnPage = 1)
        composeScreen(book)
        rule.onNodeWithText("Trang 1/3").assertExists()
        rule.onNodeWithText("Sau").performClick()
        rule.onNodeWithText("Trang 2/3").assertExists()
        // Trang 2 dựng lỗi → vẫn đi tiếp được thay vì treo màn.
        rule.onNodeWithText("Sau").performClick()
        rule.onNodeWithText("Trang 3/3").assertExists()
    }
}
