package vn.aow.monika.account

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import vn.aow.monika.ui.TestApp
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
@LooperMode(LooperMode.Mode.LEGACY)
class ProfileCoverageTest {
    /** Mốc lấy lúc chạy; offset thử được neo để giờ địa phương tránh sát nửa đêm. */
    private fun now(): Instant = Instant.now().truncatedTo(ChronoUnit.MINUTES)

    /** Tạo một múi giờ mà chính thời điểm thử đang là 05:00, không phụ thuộc giờ máy chạy CI. */
    private fun offsetAtLocalFive(now: Instant): ZoneOffset {
        val utcSeconds = now.atOffset(ZoneOffset.UTC).toLocalTime().toSecondOfDay()
        var offsetSeconds = 5 * 60 * 60 - utcSeconds
        if (offsetSeconds < -12 * 60 * 60) offsetSeconds += 24 * 60 * 60
        return ZoneOffset.ofTotalSeconds(offsetSeconds)
    }

    @Test fun emptyAndNonPositiveCheckinAreNeverToday() {
        val instant = now()
        val zone = offsetAtLocalFive(instant)

        assertFalse(Profile(0, 0, 0, 0).checkedInToday(zone))
        assertFalse(Profile(0, 0, 0, -1).checkedInToday(zone))

        val twoDaysAgo = instant.minus(Duration.ofDays(2)).toEpochMilli()
        assertFalse(Profile(0, 0, 0, twoDaysAgo).checkedInToday(zone))
    }

    @Test fun positiveTimestampUsesTheProvidedZoneAndCalendarDate() {
        val instant = now()
        val beforeMidnight = offsetAtLocalFive(instant)
        val afterMidnight = ZoneOffset.ofTotalSeconds(beforeMidnight.totalSeconds + 6 * 60 * 60)
        val recentCheckin = instant.minus(Duration.ofHours(1)).toEpochMilli()
        val twoDaysAgo = instant.minus(Duration.ofDays(2)).toEpochMilli()

        assertTrue(Profile(0, 0, 0, recentCheckin).checkedInToday(beforeMidnight))
        assertTrue(Profile(0, 0, 0, recentCheckin).checkedInToday(afterMidnight))
        assertFalse(Profile(0, 0, 0, twoDaysAgo).checkedInToday(beforeMidnight))
    }

    @Test fun theSameTimestampChangesDateAcrossTheProvidedZonesMidnight() {
        val instant = now()
        val beforeMidnight = offsetAtLocalFive(instant)
        val afterMidnight = ZoneOffset.ofTotalSeconds(beforeMidnight.totalSeconds + 6 * 60 * 60)
        val checkinSixHoursEarlier = instant.minus(Duration.ofHours(6)).toEpochMilli()
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
