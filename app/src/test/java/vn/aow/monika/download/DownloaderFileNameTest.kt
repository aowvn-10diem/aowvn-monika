package vn.aow.monika.download

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.ui.TestApp

/** Tên file tải về từ URL và Content-Disposition (hàm thuần, không mạng). URLUtil là API Android nên cần Robolectric. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class DownloaderFileNameTest {
    @Test fun utf8DispositionNameWinsAndIsDecoded() {
        assertEquals(
            "Trường Chơi.apk",
            Downloader.fileNameFrom("https://x.test/api/file/1", "attachment; filename*=UTF-8''Tr%C6%B0%E1%BB%9Dng%20Ch%C6%A1i.apk"),
        )
    }

    @Test fun plusSignInDispositionNameIsLiteralNotSpace() {
        assertEquals("a+b.apk", Downloader.fileNameFrom("https://x.test/api/file/1", "attachment; filename*=UTF-8''a+b.apk"))
    }

    @Test fun plusSignAndEncodedSpaceInSameNameStayDistinct() {
        assertEquals("a+b c.zip", Downloader.fileNameFrom("https://x.test/api/file/1", "attachment; filename*=UTF-8''a+b%20c.zip"))
    }

    @Test fun reservedCharsFromDispositionBecomeUnderscores() {
        assertEquals("a_b_c.apk", Downloader.fileNameFrom("https://x.test/api/file/1", "attachment; filename*=UTF-8''a%2Fb%3Ac.apk"))
    }

    @Test fun noDispositionUsesUrlPathWithoutQuery() {
        assertEquals("game.zip", Downloader.fileNameFrom("https://x.test/files/game.zip?v=2", null))
    }

    @Test fun badPercentEncodingFallsBackToUrlInsteadOfCrashing() {
        assertEquals("a.zip", Downloader.fileNameFrom("https://x.test/files/a.zip", "attachment; filename*=UTF-8''%ZZ.apk"))
    }
}
