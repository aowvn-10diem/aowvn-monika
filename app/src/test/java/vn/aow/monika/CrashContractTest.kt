package vn.aow.monika

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import vn.aow.monika.diag.Diagnostics

/** Cùng fixture được gửi vào crash-worker/worker.test.js, không dùng hai bản JSON tự viết. */
class CrashContractTest {
    @Test fun appSerializerRoundTripsWorkerFixture() {
        val text = javaClass.classLoader!!.getResourceAsStream("app-report.json")!!.bufferedReader().use { it.readText() }
        val json = Json { encodeDefaults = true }
        val report = json.decodeFromString(Diagnostics.Report.serializer(), text)
        assertEquals(Json.parseToJsonElement(text), json.encodeToJsonElement(Diagnostics.Report.serializer(), report))
        assertEquals("engine:rgss", report.component)
        assertEquals(4242, report.session!!.pid)
        assertEquals(3, report.count)
    }
}
