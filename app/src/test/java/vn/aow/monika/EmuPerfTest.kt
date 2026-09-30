package vn.aow.monika

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import vn.aow.monika.runner.EmuTier
import vn.aow.monika.runner.GamePreload
import java.io.File

class EmuPerfTest {
    @get:Rule val tmp = TemporaryFolder()
    private val gib = 1024L * 1024 * 1024

    @Test fun tierByRamAndCores() {
        assertEquals(EmuTier.LITE, EmuTier.classify(3 * gib, 8, false, false))        // RAM thấp
        assertEquals(EmuTier.LITE, EmuTier.classify(8 * gib, 4, false, false))        // ít nhân
        assertEquals(EmuTier.LITE, EmuTier.classify(8 * gib, 8, true, false))         // máy RAM thấp theo Android
        assertEquals(EmuTier.MID, EmuTier.classify((3.8 * gib).toLong(), 8, false, false)) // máy "4 GB" báo ~3,8 GiB
        assertEquals(EmuTier.MID, EmuTier.classify(6 * gib, 8, false, false))
        assertEquals(EmuTier.FULL, EmuTier.classify((7.4 * gib).toLong(), 8, false, false)) // máy "8 GB" báo ~7,4 GiB
    }

    @Test fun powerSaveLowersOneStep() {
        assertEquals(EmuTier.MID, EmuTier.classify(12 * gib, 8, false, true))
        assertEquals(EmuTier.LITE, EmuTier.classify(6 * gib, 8, false, true))
        assertEquals(EmuTier.LITE, EmuTier.classify(2 * gib, 8, false, true))
    }

    @Test fun shippedConfigPerfIsWellFormed() {
        val text = File("../config/monika-config.json").takeIf { it.exists() }?.readText()
            ?: File("config/monika-config.json").readText()
        val cfg = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString(vn.aow.monika.config.MonikaConfig.serializer(), text)
        assertTrue(cfg.cores.isNotEmpty())
        cfg.cores.forEach { (id, def) ->
            // Chỉ chấp nhận "lite"/"full": gõ sai tên bậc (vd. "low") thì tùy chọn sẽ không bao giờ được áp.
            assertTrue("lõi $id có bậc lạ: ${def.perf.keys}", def.perf.keys.all { it == "lite" || it == "full" })
            def.perf.values.forEach { opts -> assertTrue(opts.isNotEmpty() && opts.all { (k, v) -> k.isNotBlank() && v.isNotBlank() }) }
        }
        // Lõi nặng không dùng âm thanh độ trễ thấp.
        listOf("ppsspp", "flycast", "mupen64plus_next_gles3", "citra").forEach { assertEquals("$it", false, cfg.cores[it]?.lowLatencyAudio) }
    }

    @Test fun readaheadReadsAtMostLimit() {
        val f = tmp.newFile("rom.bin").apply { writeBytes(ByteArray(3_000_000) { 1 }) }
        assertEquals(3_000_000L, GamePreload.readahead(f))
        assertEquals(1_000_000L, GamePreload.readahead(f, max = 1_000_000L))
        assertEquals(0L, GamePreload.readahead(File(tmp.root, "khong-co")))
    }
}
