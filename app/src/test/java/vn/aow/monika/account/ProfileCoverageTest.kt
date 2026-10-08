package vn.aow.monika.account

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class ProfileCoverageTest {
    private val vietnam = ZoneId.of("Asia/Ho_Chi_Minh")

    @Test fun emptyAndNonPositiveCheckinAreNeverToday() {
        assertFalse(Profile(0, 0, 0, 0).checkedInToday(vietnam))
        assertFalse(Profile(0, 0, 0, -1).checkedInToday(vietnam))
        val yesterday = System.currentTimeMillis() - 2L * 24 * 60 * 60 * 1000
        assertFalse(Profile(0, 0, 0, yesterday).checkedInToday(vietnam))
    }

    @Test fun positiveTimestampUsesCalendarDateInProvidedZone() {
        val now = System.currentTimeMillis()
        assertTrue(Profile(0, 0, 0, now).checkedInToday(vietnam))
        assertTrue(Profile(0, 0, 0, now).checkedInToday(ZoneId.of("UTC")))
        assertFalse(Profile(0, 0, 0, now - 2L * 24 * 60 * 60 * 1000).checkedInToday(vietnam))
    }
}
