package vn.aow.monika.runner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PickCoreTest {
    private val configured = listOf("a", "b", "c")

    @Test fun userChoiceWinsWhenStillConfigured() {
        assertEquals("b", GameLauncher.pickCore("b", configured, "a", listOf("c")) { true })
    }

    @Test fun choiceNoLongerConfiguredFallsBackToDefault() {
        assertEquals("a", GameLauncher.pickCore("zz", configured, "a", emptyList()) { true })
    }

    @Test fun noChoiceAndNoDefaultIsNull() {
        assertNull(GameLauncher.pickCore(null, configured, null, listOf("a")) { true })
    }

    @Test fun unsupportedDefaultUsesFirstSupportedAlternative() {
        assertEquals("c", GameLauncher.pickCore(null, configured, "a", listOf("a", "b", "c")) { it == "c" })
    }

    @Test fun unsupportedChoiceUsesAlternative() {
        assertEquals("c", GameLauncher.pickCore("b", configured, "a", listOf("c")) { it == "c" })
    }

    @Test fun keepsDefaultWhenNoAlternativeRuns() {
        assertEquals("a", GameLauncher.pickCore(null, configured, "a", listOf("b")) { false })
    }
}
