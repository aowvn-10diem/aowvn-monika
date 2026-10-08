package vn.aow.monika.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowSystemClock
import vn.aow.monika.ui.TestApp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
@LooperMode(LooperMode.Mode.LEGACY)
class ProfileCoverageTest {
    private val vietnam = ZoneId.of("Asia/Ho_Chi_Minh")
    private val utc = ZoneId.of("UTC")
    private val fixedNow = Instant.parse("2100-01-01T00:30:00Z").toEpochMilli()

    @Before fun freezeClock() {
        ShadowSystemClock.setCurrentTimeMillis(fixedNow)
        assertEquals("test clock must be fixed", LocalDate.of(2100, 1, 1), LocalDate.now(utc))
    }

    @Test fun emptyAndNonPositiveCheckinAreNeverToday() {
        assertFalse(Profile(0, 0, 0, 0).checkedInToday(vietnam))
        assertFalse(Profile(0, 0, 0, -1).checkedInToday(vietnam))
        val yesterday = fixedNow - 2L * 24 * 60 * 60 * 1000
        assertFalse(Profile(0, 0, 0, yesterday).checkedInToday(vietnam))
    }

    @Test fun positiveTimestampUsesCalendarDateInProvidedZone() {
        assertTrue(Profile(0, 0, 0, fixedNow).checkedInToday(vietnam))
        assertTrue(Profile(0, 0, 0, fixedNow).checkedInToday(utc))
        assertFalse(Profile(0, 0, 0, fixedNow - 2L * 24 * 60 * 60 * 1000).checkedInToday(vietnam))
    }

    @Test fun usesCalendarDateInProvidedZoneAcrossMidnight() {
        val checkinAt00_30VietnamOnNewYear = Instant.parse("2099-12-31T17:30:00Z").toEpochMilli()
        val profile = Profile(0, 0, 0, checkinAt00_30VietnamOnNewYear)

        assertTrue("check-in must use today's Vietnam date", profile.checkedInToday(vietnam))
        assertFalse("the same instant must be yesterday in UTC", profile.checkedInToday(utc))
    }
}
