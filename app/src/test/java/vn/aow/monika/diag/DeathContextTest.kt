package vn.aow.monika.diag

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import android.os.Process
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import vn.aow.monika.ui.TestApp

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class DeathContextTest {
    private val generation = "00000000-0000-0000-0000-000000000001"
    @Before fun clean() {
        val c = ApplicationProvider.getApplicationContext<Context>()
        Diagnostics.end(c)
        File(c.filesDir, "diag").deleteRecursively()
        Diagnostics.pending.value = null
    }
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
        val snapshot = Diagnostics.DeathSnapshot(s, generation)
        val summary = Diagnostics.deathProcessSummary(generation)
        assertEquals(s, Diagnostics.matchingDeathSession(snapshot, 7, 40, summary))
        assertNull(Diagnostics.matchingDeathSession(snapshot, 8, 40, summary))
        assertNull(Diagnostics.matchingDeathSession(snapshot, 7, 9, summary))
        assertNull(Diagnostics.matchingDeathSession(snapshot, 7, 25, summary))
        assertNull(Diagnostics.matchingDeathSession(snapshot.copy(session = s.copy(lastAlive = -1)), 7, 40, summary))
    }

    @Test fun olderSamePidSnapshotRequiresAndroidGenerationEvidenceOrRemainsUnknown() {
        val old = Diagnostics.DeathSnapshot(Diagnostics.Session(7, 10, "libretro", "old-core", stageAt = 20, lastAlive = 30), generation)
        val newSummary = Diagnostics.deathProcessSummary("00000000-0000-0000-0000-000000000002")
        assertNull(Diagnostics.matchingDeathSession(old, 7, 1000, newSummary))
        assertNull(Diagnostics.matchingDeathSession(old, 7, 1000, null)) // new process dies before begin
        assertNull(Diagnostics.matchingDeathSession(old, 7, 1000, ByteArray(0)))
        assertEquals(old.session, Diagnostics.matchingDeathSession(old, 7, 1000, Diagnostics.deathProcessSummary(generation)))
        assertEquals("unknown", Diagnostics.enrichDeathReport(report("lowmem", Diagnostics.matchingDeathSession(old, 7, 1000, newSummary))).core)
    }

    @Test fun oldExitDoesNotDeleteNewGenerationSnapshotAndLegacySnapshotIsUntrusted() {
        val c = ApplicationProvider.getApplicationContext<Context>()
        val f = File(c.filesDir, "diag/sessions/7.json").apply { parentFile!!.mkdirs() }
        val snapshot = Diagnostics.DeathSnapshot(Diagnostics.Session(7, 10, "libretro", "new-core", lastAlive = 30), generation)
        f.writeText(Json.encodeToString(Diagnostics.DeathSnapshot.serializer(), snapshot))
        Diagnostics.clearDeathSession(c, 7, Diagnostics.deathProcessSummary("00000000-0000-0000-0000-000000000002"))
        assertTrue(f.exists())
        Diagnostics.clearDeathSession(c, 7, Diagnostics.deathProcessSummary(generation))
        assertFalse(f.exists())
        f.writeText(Json.encodeToString(Diagnostics.Session.serializer(), snapshot.session))
        assertNull(Diagnostics.readDeathSession(c, 7, 40, Diagnostics.deathProcessSummary(generation)))
    }

    @Test fun perPidSnapshotSurvivesActivePointerLossUpdatesStageAndNormalExitClearsIt() {
        val c = ApplicationProvider.getApplicationContext<Context>()
        File(c.filesDir, "diag/sessions").deleteRecursively()
        Diagnostics.begin(c, "libretro", "melondsds", "fake", "fixture", "NDS")
        Diagnostics.stage(c, "core-ready")
        val pid = Process.myPid()
        val snapshot = Json.decodeFromString(Diagnostics.DeathSnapshot.serializer(), File(c.filesDir, "diag/sessions/$pid.json").readText())
        val summary = Diagnostics.deathProcessSummary(snapshot.generation)
        File(c.filesDir, "diag/active-session.json").delete()
        val s = Diagnostics.readDeathSession(c, pid, System.currentTimeMillis() + 1000, summary)
        assertNotNull(s); assertEquals("melondsds", s!!.core); assertEquals("NDS", s.system); assertEquals("core-ready", s.stage)
        assertNull(Diagnostics.readDeathSession(c, pid + 1, System.currentTimeMillis() + 1000, summary))
        Diagnostics.end(c)
        assertNull(Diagnostics.readDeathSession(c, pid, System.currentTimeMillis() + 1000, summary))
    }
}
