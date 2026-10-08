package vn.aow.monika.account

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import vn.aow.monika.ui.TestApp
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
@LooperMode(LooperMode.Mode.LEGACY)
class ProfileCoverageTest {
    private val vietnam = ZoneId.of("Asia/Ho_Chi_Minh")
    private val utc = ZoneId.of("UTC")

    @Test fun emptyAndNonPositiveCheckinAreNeverToday() {
        assertFalse(Profile(0, 0, 0, 0).checkedInToday(vietnam))
        assertFalse(Profile(0, 0, 0, -1).checkedInToday(vietnam))
        val yesterday = System.currentTimeMillis() - 2L * 24 * 60 * 60 * 1000
        assertFalse(Profile(0, 0, 0, yesterday).checkedInToday(vietnam))
    }

    @Test fun positiveTimestampUsesCalendarDateInProvidedZone() {
        val now = System.currentTimeMillis()
        assertTrue(Profile(0, 0, 0, now).checkedInToday(vietnam))
        assertTrue(Profile(0, 0, 0, now).checkedInToday(utc))
        assertFalse(Profile(0, 0, 0, now - 2L * 24 * 60 * 60 * 1000).checkedInToday(vietnam))
    }

    @Test fun usesCalendarDateInProvidedZoneAcrossMidnight() {
        repeat(10) {
            assertVietnamTodayButNotUtc()
        }
    }

    private fun assertVietnamTodayButNotUtc() {
        for (attempt in 1..3) {
            val now = Instant.now()
            val vietnamToday = now.atZone(vietnam).toLocalDate()
            val utcToday = now.atZone(utc).toLocalDate()
            assertTrue(vietnamToday == utcToday || vietnamToday == utcToday.plusDays(1))

            // At 00:30 Vietnam is still the previous UTC date when both zones
            // share today's date. When Vietnam is one date ahead, 23:30 maps
            // to today's UTC date, which is yesterday in Vietnam.
            val localCheckin = if (vietnamToday == utcToday) {
                vietnamToday.atTime(0, 30)
            } else {
                vietnamToday.atTime(23, 30)
            }
            val profile = Profile(
                points = 0,
                streak = 0,
                progress = 0,
                lastCheckin = localCheckin.atZone(vietnam).toInstant().toEpochMilli(),
            )
            val checkedInInVietnam = profile.checkedInToday(vietnam)
            val checkedInInUtc = profile.checkedInToday(utc)

            val after = Instant.now()
            val datesStayedStable =
                vietnamToday == after.atZone(vietnam).toLocalDate() &&
                    utcToday == after.atZone(utc).toLocalDate()
            if (datesStayedStable) {
                assertTrue("check-in must use today's Vietnam date", checkedInInVietnam)
                assertFalse("the same instant must be yesterday in UTC", checkedInInUtc)
                return
            }
        }
        throw AssertionError("calendar date changed during all three cross-midnight checks")
    }
}
