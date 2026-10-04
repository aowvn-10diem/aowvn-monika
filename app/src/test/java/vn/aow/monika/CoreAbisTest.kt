package vn.aow.monika

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.aow.monika.config.ConfigRepository

/** V27: lõi chỉ có bản 64-bit trên buildbot phải khai `abis`, để máy 32-bit không tải URL armeabi-v7a (404). */
class CoreAbisTest {
    private val cfg = ConfigRepository.parse(File("../config/monika-config.json").readText())

    @Test fun citraChiChoArm64() {
        assertEquals(listOf("arm64-v8a"), cfg.cores.getValue("citra").abis)
    }

    @Test fun loiMelondsdsChiChoArm64() {
        assertTrue(cfg.cores.getValue("melondsds").abis == listOf("arm64-v8a"))
    }
}
