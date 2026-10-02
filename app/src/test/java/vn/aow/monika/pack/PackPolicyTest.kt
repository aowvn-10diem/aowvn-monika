package vn.aow.monika.pack

import org.junit.Assert.assertEquals
import org.junit.Test

class PackPolicyTest {
    private val mb = 1024L * 1024
    private fun d(size: Long, unmetered: Boolean = false, saver: Boolean = false, c: PackChoice? = null) =
        PackPolicy.decide(size, unmetered, saver, c)

    @Test fun wifiAlwaysStarts() = assertEquals(PackAction.START, d(300 * mb, unmetered = true))
    @Test fun smallPackAutoOnCellular() = assertEquals(PackAction.START, d(15 * mb))
    @Test fun bigPackAsks() = assertEquals(PackAction.ASK, d(15 * mb + 1))
    @Test fun unknownSizeAsks() = assertEquals(PackAction.ASK, d(0))
    @Test fun dataSaverAsksEvenWhenSmall() = assertEquals(PackAction.ASK, d(5 * mb, saver = true))
    @Test fun chooseCellularStarts() = assertEquals(PackAction.START, d(50 * mb, c = PackChoice.USE_CELLULAR))
    @Test fun chooseWifiWaits() = assertEquals(PackAction.WAIT, d(50 * mb, c = PackChoice.WAIT_WIFI))
    @Test fun wifiOverridesWait() = assertEquals(PackAction.START, d(50 * mb, unmetered = true, c = PackChoice.WAIT_WIFI))
}
