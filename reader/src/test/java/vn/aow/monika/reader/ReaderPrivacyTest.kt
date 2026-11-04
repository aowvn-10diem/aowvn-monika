package vn.aow.monika.reader

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * V142 — Luna 4 (#457): **không ghi dữ liệu người dùng vào log**. Mọi báo cáo của module chỉ
 * mang **mã lỗi**, không tên tệp / đường dẫn / kích thước.
 */
class ReaderPrivacyTest {

    private val reports = mutableListOf<String>()
    private val original = ReaderDiag.onError

    @After fun restore() {
        ReaderDiag.onError = original
    }

    private fun capture() {
        ReaderDiag.onError = { what, _ -> reports += what }
    }

    @Test fun openingABrokenPdfReportsOnlyAnErrorCode() {
        capture()
        val secretName = "bi-mat-ca-nhan-cua-toi"
        val dir = File(System.getProperty("java.io.tmpdir"), "v142-privacy").apply { mkdirs() }
        val file = File(dir, "$secretName.pdf").apply { writeText("day khong phai pdf") }

        val opened = PdfBookOpener.open(file)
        assertTrue("Tệp rác phải mở thất bại", opened.isFailure)
        assertTrue("Phải có báo cáo cho lỗi mở", reports.isNotEmpty())

        for (report in reports) {
            assertFalse("Không được ghi tên tệp vào log: $report", report.contains(secretName))
            assertFalse("Không được ghi đuôi tệp vào log: $report", report.contains(".pdf"))
            assertFalse(
                "Không được ghi đường dẫn vào log: $report",
                report.contains(dir.absolutePath) || report.contains(file.absolutePath),
            )
        }
        assertTrue("Phải dùng mã lỗi (pdf-khong-trang / mo-pdf-loi): $reports",
            reports.any { it == "pdf-khong-trang" || it == "mo-pdf-loi" })
    }

    @Test fun missingFileReportsCodeNotPath() {
        capture()
        val missing = File(System.getProperty("java.io.tmpdir"), "v142-khong-co-bi-mat.pdf")
        assertTrue(PdfBookOpener.open(missing).isFailure)
        assertTrue("Thiếu tệp thì không có báo cáo (không phải lỗi hệ thống)", reports.isEmpty())
    }
}
