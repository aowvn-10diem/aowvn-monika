package vn.aow.monika.account

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class ProfileCoverageTest {
    /** Explicit clock keeps date checks independent of the host clock and timezone. */
    private val fixedNow = Instant.parse("2100-01-01T05:00:00Z")
    private val fixedClock = Clock.fixed(fixedNow, ZoneOffset.UTC)

    @Test fun emptyAndNonPositiveCheckinAreNeverToday() {
        val zone = ZoneOffset.ofHours(7)

        assertFalse(Profile(0, 0, 0, 0).checkedInToday(zone, fixedClock))
        assertFalse(Profile(0, 0, 0, -1).checkedInToday(zone, fixedClock))

        val twoDaysAgo = fixedNow.minus(Duration.ofDays(2)).toEpochMilli()
        assertFalse(Profile(0, 0, 0, twoDaysAgo).checkedInToday(zone, fixedClock))
    }

    @Test fun positiveTimestampUsesTheProvidedZoneAndCalendarDate() {
        val beforeMidnight = ZoneOffset.UTC
        val afterMidnight = ZoneOffset.ofHours(6)
        val recentCheckin = fixedNow.minus(Duration.ofMinutes(30)).toEpochMilli()
        val twoDaysAgo = fixedNow.minus(Duration.ofDays(2)).toEpochMilli()

        assertTrue(Profile(0, 0, 0, recentCheckin).checkedInToday(beforeMidnight, fixedClock))
        assertTrue(Profile(0, 0, 0, recentCheckin).checkedInToday(afterMidnight, fixedClock))
        assertFalse(Profile(0, 0, 0, twoDaysAgo).checkedInToday(beforeMidnight, fixedClock))
    }

    @Test fun theSameTimestampChangesDateAcrossTheProvidedZonesMidnight() {
        val beforeMidnight = ZoneOffset.UTC
        val afterMidnight = ZoneOffset.ofHours(6)
        val checkinSixHoursEarlier = fixedNow.minus(Duration.ofHours(6)).toEpochMilli()
        val profile = Profile(0, 0, 0, checkinSixHoursEarlier)

        assertFalse(
            "check-in must be yesterday before the zone's midnight",
            profile.checkedInToday(beforeMidnight, fixedClock),
        )
        assertTrue(
            "the same check-in must be today after the zone's midnight",
            profile.checkedInToday(afterMidnight, fixedClock),
        )
    }
}
