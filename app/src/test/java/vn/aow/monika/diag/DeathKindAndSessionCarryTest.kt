package vn.aow.monika.diag

import android.app.ApplicationExitInfo
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.ui.TestApp
import java.io.File

/**
 * #302 (`unknown/resource`, 261 lượt): hai lỗi làm báo cáo của tiến trình `:midlet` mất lõi.
 *
 * (a) `collect()` xếp `REASON_EXCESSIVE_RESOURCE_USAGE` vào `killed` trong khi `collectProcessDeaths`
 *     gọi cùng lần chết là `resource` → `Diagnostics.collectKind`.
 * (b) Báo cáo bị ghi hai lần cùng `id`: lần đầu có phiên (`collect`), lần sau không phiên
 *     (`collectProcessDeaths`) ghi đè và xoá lõi → `Diagnostics.carrySession` dùng trong `record`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class DeathKindAndSessionCarryTest {
    private val at = 1_700_000_000_000L

    @Before fun clean() {
        val c = ApplicationProvider.getApplicationContext<Context>()
        Diagnostics.pending.value = null
        File(c.filesDir, "diag").deleteRecursively()
    }

    private val midletSession = Diagnostics.Session(
        7, 10, "midlet", "midlet", system = "J2ME", stage = "playing", stageAt = 20, lastAlive = 30,
    )

    private fun report(
        id: Long = at,
        kind: String = "resource",
        session: Diagnostics.Session? = null,
        component: String = "app:midlet",
        fromGame: Boolean = false,
    ) = Diagnostics.Report(id, id, kind, "fixture", "0.8.14", "fake", session, component = component, fromGame = fromGame)

    // ---------- (a) nhãn nhóm ----------

    @Test
    fun resourceUsageIsResourceNotKilledAndInitFailureIsInitfail() {
        assertEquals("resource", Diagnostics.collectKind(ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE))
        assertEquals("initfail", Diagnostics.collectKind(ApplicationExitInfo.REASON_INITIALIZATION_FAILURE))
        // Các nhánh cũ không được đổi.
        assertEquals("native", Diagnostics.collectKind(ApplicationExitInfo.REASON_CRASH_NATIVE))
        assertEquals("java", Diagnostics.collectKind(ApplicationExitInfo.REASON_CRASH))
        assertEquals("anr", Diagnostics.collectKind(ApplicationExitInfo.REASON_ANR))
        assertEquals("lowmem", Diagnostics.collectKind(ApplicationExitInfo.REASON_LOW_MEMORY))
        assertEquals("killed", Diagnostics.collectKind(ApplicationExitInfo.REASON_OTHER))
        assertEquals("unknown", Diagnostics.collectKind(null))
    }

    @Test
    fun userExitStillProducesNoReport() {
        assertNull(Diagnostics.collectKind(ApplicationExitInfo.REASON_USER_REQUESTED))
        assertNull(Diagnostics.collectKind(ApplicationExitInfo.REASON_USER_STOPPED))
        assertNull(Diagnostics.collectKind(ApplicationExitInfo.REASON_EXIT_SELF))
    }

    // ---------- (b) bản ghi sau không được xoá phiên ----------

    @Test
    fun secondRecordOfSameDeathKeepsTheSessionAndItsCore() {
        val c = ApplicationProvider.getApplicationContext<Context>()
        Diagnostics.record(c, report(session = midletSession, component = "engine:j2me", fromGame = true))
        // Lần ghi sau: cùng `id`, không có phiên (đúng hình dạng bản ghi của collectProcessDeaths).
        val last = Diagnostics.record(c, report(component = "app:midlet"))
        assertEquals(midletSession, last.session)
        assertEquals("engine:j2me", last.component)
        assertTrue(last.fromGame)
        // Bản ghi trên đĩa — thứ Worker/bot đọc — cũng phải còn lõi.
        val stored = Diagnostics.list(c).single { it.id == at }
        assertEquals(midletSession, stored.session)
        assertEquals("midlet", stored.core)
        assertEquals("J2ME", stored.system)
    }

    @Test
    fun newerReportWithItsOwnSessionIsNotOverwritten() {
        val newer = Diagnostics.Session(7, 40, "libretro", "melondsds", system = "NDS", stageAt = 50, lastAlive = 60)
        val kept = Diagnostics.carrySession(report(session = newer), report(session = midletSession, component = "engine:j2me"))
        assertEquals(newer, kept.session)
        assertEquals("melondsds", kept.session?.core)
    }

    @Test
    fun nonDeathReportsNeverBorrowASession() {
        val death = report(kind = "anr", session = midletSession, component = "engine:j2me", fromGame = true)
        val opFail = report(kind = "op-fail", component = "op:va-rom")
        assertEquals(opFail, Diagnostics.carrySession(opFail, death))
        assertFalse(Diagnostics.carrySession(opFail, death).fromGame)
        assertEquals(report(kind = "native"), Diagnostics.carrySession(report(kind = "native"), opFail))
    }
}
