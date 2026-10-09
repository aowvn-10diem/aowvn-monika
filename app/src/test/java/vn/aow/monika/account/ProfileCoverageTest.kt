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
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
@LooperMode(LooperMode.Mode.LEGACY)
class ProfileCoverageTest {
    private val vietnam = ZoneId.of("Asia/Ho_Chi_Minh")
    private val utc = ZoneId.of("UTC")
    private val fixedNow = Instant.parse("2026-10-09T12:00:00Z")

    @Before
    fun fixSystemClock() {
        val fixedMillis = fixedNow.toEpochMilli()
        assertTrue("Robolectric must accept the fixed time", SystemClock.setCurrentTimeMillis(fixedMillis))
        assertEquals("System.currentTimeMillis must use the fixed time", fixedMillis, System.currentTimeMillis())
    }

    private fun todayAtNoon(zone: ZoneId): Long =
        fixedNow.atZone(zone).toLocalDate().atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    // Put the current instant at 05:00 locally; six hours earlier is 23:00 yesterday.
    private fun offsetAtLocalFive(now: Instant): ZoneOffset {
        val utcSeconds = now.atOffset(ZoneOffset.UTC).toLocalTime().toSecondOfDay()
        var offsetSeconds = 5 * 60 * 60 - utcSeconds
        if (offsetSeconds < -12 * 60 * 60) offsetSeconds += 24 * 60 * 60
        return ZoneOffset.ofTotalSeconds(offsetSeconds)
    }

    @Test fun emptyAndNonPositiveCheckinAreNeverToday() {
        assertFalse(Profile(0, 0, 0, 0).checkedInToday(vietnam))
        assertFalse(Profile(0, 0, 0, -1).checkedInToday(vietnam))
        val yesterday = fixedNow.atZone(vietnam).toLocalDate().minusDays(2).atTime(12, 0)
            .atZone(vietnam).toInstant().toEpochMilli()
        assertFalse(Profile(0, 0, 0, yesterday).checkedInToday(vietnam))
    }

    @Test fun positiveTimestampUsesCalendarDateInProvidedZone() {
        assertTrue(Profile(0, 0, 0, todayAtNoon(vietnam)).checkedInToday(vietnam))
        assertTrue(Profile(0, 0, 0, todayAtNoon(utc)).checkedInToday(utc))
        val twoDaysAgo = fixedNow.atZone(vietnam).toLocalDate().minusDays(2).atTime(12, 0)
            .atZone(vietnam).toInstant().toEpochMilli()
        assertFalse(Profile(0, 0, 0, twoDaysAgo).checkedInToday(vietnam))
    }

    @Test fun usesCalendarDateInProvidedZoneAcrossMidnight() {
        val now = fixedNow.truncatedTo(ChronoUnit.MINUTES)
        val beforeMidnight = offsetAtLocalFive(now)
        val afterMidnight = ZoneOffset.ofTotalSeconds(beforeMidnight.totalSeconds + 6 * 60 * 60)
        val checkinSixHoursEarlier = now.minus(Duration.ofHours(6)).toEpochMilli()
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
