package vn.aow.monika.library

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** V26: đọc chỉ mục XP3 tự sinh (v1/v2, chỉ mục thô/nén, có/không startup.tjs). Không dùng file game thật. */
class Xp3IndexTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun xp3(names: List<String>, compressed: Boolean, v2: Boolean = false) = Xp3Fixture.xp3(tmp.root, names, compressed, v2)

    @Test fun indexThoVaNen_v1_docDuTen() {
        for (compressed in listOf(false, true)) {
            val r = Xp3Index.read(xp3(listOf("startup.tjs", "scn/main.ks", "Bản đồ.png"), compressed)) as Xp3Index.Result.Names
            assertEquals(listOf("startup.tjs", "scn/main.ks", "Bản đồ.png"), r.names)
            assertTrue(r.complete)
        }
    }

    @Test fun v2_vaTenHoaThuong() {
        for (compressed in listOf(false, true)) {
            val f = xp3(listOf("Startup.TJS", "a/b.txt"), compressed, v2 = true)
            assertEquals(true, Xp3Index.hasRootStartup(f))
        }
    }

    @Test fun coStartupOGocKhacOThuMucCon() {
        assertEquals(true, Xp3Index.hasRootStartup(xp3(listOf("startup.tjs"), true)))
        assertEquals(false, Xp3Index.hasRootStartup(xp3(listOf("data/startup.tjs", "other.tjs"), true)))
        assertEquals(false, Xp3Index.hasRootStartup(xp3(listOf("scn/main.ks"), false)))
    }

    @Test fun khongPhaiXp3HoacHong_traVeNullKhongVang() {
        val notXp3 = File(tmp.root, "x.xp3").also { it.writeBytes(ByteArray(500) { 1 }) }
        assertNull(Xp3Index.hasRootStartup(notXp3))
        assertTrue(Xp3Index.read(notXp3) is Xp3Index.Result.Unreadable)
        val cut = xp3(listOf("startup.tjs"), true).let { f -> File(tmp.root, "cut.xp3").also { it.writeBytes(f.readBytes().copyOf(f.length().toInt() - 10)) } }
        assertNull(Xp3Index.hasRootStartup(cut))
        assertTrue(Xp3Index.read(File(tmp.root, "khong-ton-tai.xp3")) is Xp3Index.Result.Unreadable)
    }

    @Test fun viTriChiMucNgoaiFileBiTuChoi() {
        val f = xp3(listOf("startup.tjs"), false)
        val bytes = f.readBytes()
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putLong(11, 10_000_000_000L)
        f.writeBytes(bytes)
        assertFalse(Xp3Index.read(f) is Xp3Index.Result.Names)
    }
}
