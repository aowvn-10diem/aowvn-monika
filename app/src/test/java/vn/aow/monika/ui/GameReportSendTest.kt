package vn.aow.monika.ui

import java.io.IOException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import vn.aow.monika.diag.Diagnostics

class GameReportSendTest {
    @Test fun creationOrSavingFailureReturnsReasonInsteadOfEscaping() {
        val result = safelySendUserReport { throw IOException("fixture save failure") }
        assertFalse(result.ok); assertEquals("IOException", result.error)
    }
    @Test fun httpFailureAndSuccessfulSendKeepTheirActualResult() {
        val failure = Diagnostics.SendResult(false, "HTTP 503")
        assertEquals(failure, safelySendUserReport { failure })
        assertTrue(safelySendUserReport { Diagnostics.SendResult(true) }.ok)
    }
    @Test fun leavingScreenStillCancelsSend() {
        val cancellation = CancellationException("fixture leave screen")
        try { safelySendUserReport { throw cancellation }; fail("Cancellation swallowed") }
        catch (e: CancellationException) { assertSame(cancellation, e) }
    }
}
