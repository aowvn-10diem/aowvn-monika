package vn.aow.monika.account

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class ProfileClockTest {
    @Test fun fixedClockRespectsRequestedZoneAcrossMidnight() {
        val clock = Clock.fixed(Instant.parse("2020-01-02T17:01:00Z"), ZoneId.of("UTC"))
        val profile = Profile(0, 0, 0, Instant.parse("2020-01-02T16:59:00Z").toEpochMilli())
        assertTrue(profile.checkedInToday(ZoneId.of("UTC"), clock))
        assertFalse(profile.checkedInToday(ZoneId.of("Asia/Ho_Chi_Minh"), clock))
        assertFalse(Profile(0, 0, 0, 0).checkedInToday(ZoneId.of("UTC"), clock))
        assertFalse(Profile(0, 0, 0, -1).checkedInToday(ZoneId.of("UTC"), clock))
    }
}
