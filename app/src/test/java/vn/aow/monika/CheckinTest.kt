package vn.aow.monika

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.aow.monika.account.AowApi
import vn.aow.monika.account.Profile
import java.time.ZoneId
import java.time.ZonedDateTime

/** Điểm danh phải tính y hệt web aow.vn. */
class CheckinTest {
    private val vn = ZoneId.of("Asia/Ho_Chi_Minh")
    private fun t(d: Int, h: Int = 10) = ZonedDateTime.of(2026, 9, d, h, 0, 0, 0, vn).toInstant().toEpochMilli()

    @Test fun first() {
        val r = AowApi.nextCheckin(Profile(0, 0, 0, 0), t(28), vn)
        assertEquals(Profile(0, 1, 1, t(28)), r.profile); assertFalse(r.already)
    }
    @Test fun consecutive() = assertEquals(6, AowApi.nextCheckin(Profile(3, 5, 5, t(27, 23)), t(28, 0), vn).profile.streak)
    @Test fun missedDayResets() {
        val r = AowApi.nextCheckin(Profile(3, 9, 9, t(25)), t(28), vn).profile
        assertEquals(1, r.streak); assertEquals(1, r.progress); assertEquals(3, r.points)
    }
    @Test fun day14GivesPoint() {
        val r = AowApi.nextCheckin(Profile(3, 13, 13, t(27)), t(28), vn)
        assertTrue(r.pointAwarded); assertEquals(4, r.profile.points); assertEquals(0, r.profile.progress); assertEquals(14, r.profile.streak)
    }
    @Test fun sameDay() = assertTrue(AowApi.nextCheckin(Profile(0, 2, 2, t(28, 1)), t(28, 23), vn).already)
}
