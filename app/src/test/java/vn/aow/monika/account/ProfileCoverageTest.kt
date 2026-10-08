package vn.aow.monika.account

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
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
@LooperMode(LooperMode.Mode.LEGACY)
class ProfileCoverageTest {
    private val vietnam = ZoneId.of("Asia/Ho_Chi_Minh")

    @Before fun freezeClock() {
        val fixedNow = Instant.parse("2000-01-01T00:30:00Z").toEpochMilli()
        ShadowSystemClock.advanceBy(Duration.ofMillis(fixedNow - ShadowSystemClock.currentTimeMillis()))
    }

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

    @Test fun usesCalendarDateInProvidedZoneAcrossMidnight() {
        val checkin = Instant.parse("1999-12-31T17:30:00Z").toEpochMilli()
        assertTrue(Profile(0, 0, 0, checkin).checkedInToday(vietnam))
        assertFalse(Profile(0, 0, 0, checkin).checkedInToday(ZoneId.of("UTC")))
    }
}
