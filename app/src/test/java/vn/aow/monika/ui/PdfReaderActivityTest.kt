package vn.aow.monika.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.aow.monika.AppGraph
import vn.aow.monika.reader.PdfBookOpener
import vn.aow.monika.reader.PdfReaderActivity
import vn.aow.monika.reader.ReaderError
import vn.aow.monika.reader.ReaderException
import java.io.File

/**
 * V142: phần app của màn đọc PDF — Prefs nhớ trang, route riêng, mở tệp hỏng ra lỗi có loại.
 * (Màn hình + trạng thái lỗi được kiểm trong module :reader bằng Robolectric.)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34])
class PdfReaderActivityTest {

    @Test fun prefsRememberThePagePerDocument() {
        val book = "/storage/Download/AowVN Monika/Game/sách/Bài giảng.pdf"
        AppGraph.prefs.setReadingPage(book, 6)
        assertEquals(6, AppGraph.prefs.readingPage(book))
        assertEquals(0, AppGraph.prefs.readingPage("/storage/khac.pdf"))
        AppGraph.prefs.setReadingPage(book, 0)
        assertEquals(0, AppGraph.prefs.readingPage(book))
    }

    @Test fun routeEncodesThePathForNavigation() {
        val path = "/storage/Download/AowVN Monika/Game/a b.pdf"
        assertEquals("reader/{path}", PdfReaderActivity.ROUTE)
        assertEquals("reader/" + android.net.Uri.encode(path), PdfReaderActivity.route(path))
        assertEquals("reader_path", PdfReaderActivity.EXTRA_PATH)
        assertEquals("reader_name", PdfReaderActivity.EXTRA_NAME)
    }

    @Test fun corruptFileBecomesATypedErrorInsteadOfACrash() {
        val context = RuntimeEnvironment.getApplication()
        val file = File(context.cacheDir, "hong.pdf").apply { writeText("day khong phai pdf") }
        val opened = PdfBookOpener.open(file)
        assertTrue(opened.isFailure)
        assertEquals(ReaderError.CORRUPT, (opened.exceptionOrNull() as ReaderException).error)
    }

    @Test fun missingFileBecomesMissingError() {
        val context = RuntimeEnvironment.getApplication()
        val opened = PdfBookOpener.open(File(context.cacheDir, "khong-co.pdf"))
        assertTrue(opened.isFailure)
        assertEquals(ReaderError.MISSING, (opened.exceptionOrNull() as ReaderException).error)
    }
}
