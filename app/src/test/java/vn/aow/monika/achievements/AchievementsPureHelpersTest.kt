package vn.aow.monika.achievements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.ui.TestApp

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class AchievementsPureHelpersTest {
    @Test fun translatesKnownAchievementTypesAndIgnoresUnknownTypes() {
        assertEquals("Cốt truyện", raTypeVi("progression"))
        assertEquals("Phá đảo", raTypeVi("win_condition"))
        assertEquals("Dễ lỡ", raTypeVi("missable"))
        assertNull(raTypeVi("unknown"))
        assertNull(raTypeVi(null))
    }

    @Test fun parsesSyntheticInGameSummaryAndAchievementFields() {
        val parsed = parseInGameAchievements(
            """{"title":"Synthetic game","hardcore":true,"unlocked":1,"points":20,"pointsUnlocked":5,"achievements":[{"title":"First","description":"Fixture","points":5,"unlocked":true,"bucket":"progression","progress":"1/1","badge":"fake-badge"}]}""",
        )
        assertNotNull(parsed)
        assertEquals("Synthetic game", parsed!!.title)
        assertTrue(parsed.hardcore)
        assertEquals(1, parsed.total)
        assertEquals(1, parsed.unlocked)
        assertEquals(20, parsed.points)
        assertEquals(5, parsed.pointsUnlocked)
        assertEquals("fake-badge", parsed.list.single().badge)
    }

    @Test fun malformedSummaryReturnsNullAndMissingTotalsUseListSize() {
        assertNull(parseInGameAchievements("not json"))
        val parsed = parseInGameAchievements("""{"achievements":[{},{}]}""")
        assertNotNull(parsed)
        assertEquals(2, parsed!!.total)
        assertEquals(2, parsed.list.size)
        assertEquals("", parsed.list.first().title)
    }
}
