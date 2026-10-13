package vn.aow.monika.diag

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import android.os.Process
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeathContextTest {
    private fun report(kind: String, session: Diagnostics.Session? = null) =
        Diagnostics.Report(123, 123, kind, "fixture", "0.8.3", "fake", session)

    @Test fun lowmemAndAnrRetainActualSessionFieldsInSerializedSample() {
        val session = Diagnostics.Session(7, 10, "libretro", "melondsds", system = "NDS", stage = "first-frame", stageAt = 20, lastAlive = 30)
        for (kind in listOf("lowmem", "anr")) {
            val r = Diagnostics.enrichDeathReport(report(kind, session))
            assertEquals(session, r.session)
            val json = Json.encodeToJsonElement(Diagnostics.Report.serializer(), r).jsonObject
            assertEquals("melondsds", json["core"]!!.jsonPrimitive.content)
            assertEquals("NDS", json["system"]!!.jsonPrimitive.content)
            assertEquals("first-frame", json["stage"]!!.jsonPrimitive.content)
        }
    }

    @Test fun missingEvidenceIsExplicitUnknownAndDoesNotInventSession() {
        for (kind in listOf("lowmem", "anr")) {
            val r = Diagnostics.enrichDeathReport(report(kind))
            assertNull(r.session)
            assertEquals(listOf("unknown", "unknown", "unknown"), listOf(r.core, r.system, r.stage))
        }
        assertEquals(report("java"), Diagnostics.enrichDeathReport(report("java")))
    }

    @Test fun pidReuseAndNewerSnapshotCannotBeAttributedToOlderDeath() {
        val s = Diagnostics.Session(7, 10, "libretro", "melondsds", stageAt = 20, lastAlive = 30)
        assertEquals(s, Diagnostics.matchingDeathSession(s, 7, 40))
        assertNull(Diagnostics.matchingDeathSession(s, 8, 40))
        assertNull(Diagnostics.matchingDeathSession(s, 7, 9))
        assertNull(Diagnostics.matchingDeathSession(s, 7, 25))
        assertNull(Diagnostics.matchingDeathSession(s.copy(lastAlive = -1), 7, 40))
    }

    @Test fun perPidSnapshotSurvivesActivePointerLossUpdatesStageAndNormalExitClearsIt() {
        val c = ApplicationProvider.getApplicationContext<Context>()
        File(c.filesDir, "diag/sessions").deleteRecursively()
        Diagnostics.begin(c, "libretro", "melondsds", "fake", "fixture", "NDS")
        Diagnostics.stage(c, "core-ready")
        val pid = Process.myPid()
        File(c.filesDir, "diag/active-session.json").delete()
        val s = Diagnostics.readDeathSession(c, pid, System.currentTimeMillis() + 1000)
        assertNotNull(s); assertEquals("melondsds", s!!.core); assertEquals("NDS", s.system); assertEquals("core-ready", s.stage)
        assertNull(Diagnostics.readDeathSession(c, pid + 1, System.currentTimeMillis() + 1000))
        Diagnostics.end(c)
        assertNull(Diagnostics.readDeathSession(c, pid, System.currentTimeMillis() + 1000))
    }
}
