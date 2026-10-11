package vn.aow.monika.account

import android.os.SystemClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import vn.aow.monika.ui.TestApp
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
@LooperMode(LooperMode.Mode.LEGACY)
class ProfileCoverageTest {
    /** Future fixed time lets Robolectric's monotonic legacy clock move forward to this exact instant. */
    private val fixedNow = Instant.parse("2100-01-01T05:00:00Z")

    @Before fun freezeClock() {
        val fixedMillis = fixedNow.toEpochMilli()
        assertTrue(
            "Robolectric should move its clock forward to the fixed test time",
            SystemClock.setCurrentTimeMillis(fixedMillis),
        )
        assertEquals(
            "java.time must observe the fixed Robolectric clock",
            fixedMillis,
            System.currentTimeMillis(),
        )
    }

    @Test fun emptyAndNonPositiveCheckinAreNeverToday() {
        val zone = ZoneOffset.ofHours(7)

        assertFalse(Profile(0, 0, 0, 0).checkedInToday(zone))
        assertFalse(Profile(0, 0, 0, -1).checkedInToday(zone))

        val twoDaysAgo = fixedNow.minus(Duration.ofDays(2)).toEpochMilli()
        assertFalse(Profile(0, 0, 0, twoDaysAgo).checkedInToday(zone))
    }

    @Test fun positiveTimestampUsesTheProvidedZoneAndCalendarDate() {
        val beforeMidnight = ZoneOffset.UTC
        val afterMidnight = ZoneOffset.ofHours(6)
        val recentCheckin = fixedNow.minus(Duration.ofMinutes(30)).toEpochMilli()
        val twoDaysAgo = fixedNow.minus(Duration.ofDays(2)).toEpochMilli()

        assertTrue(Profile(0, 0, 0, recentCheckin).checkedInToday(beforeMidnight))
        assertTrue(Profile(0, 0, 0, recentCheckin).checkedInToday(afterMidnight))
        assertFalse(Profile(0, 0, 0, twoDaysAgo).checkedInToday(beforeMidnight))
    }

    @Test fun theSameTimestampChangesDateAcrossTheProvidedZonesMidnight() {
        val beforeMidnight = ZoneOffset.UTC
        val afterMidnight = ZoneOffset.ofHours(6)
        val checkinSixHoursEarlier = fixedNow.minus(Duration.ofHours(6)).toEpochMilli()
        val profile = Profile(0, 0, 0, checkinSixHoursEarlier)

        assertFalse(
            "check-in must be yesterday before the zone's midnight",
            profile.checkedInToday(beforeMidnight),
        )
        assertTrue(
            "the same check-in must be today after the zone's midnight",
            profile.checkedInToday(afterMidnight),
        )
    }
}
