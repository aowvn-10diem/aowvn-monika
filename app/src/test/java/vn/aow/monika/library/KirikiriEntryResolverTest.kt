package vn.aow.monika.library

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** V26: chọn xp3 có startup.tjs ở gốc bằng chỉ mục XP3 tự sinh (không dùng file game thật). */
class KirikiriEntryResolverTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun put(dir: File, name: String, names: List<String>, compressed: Boolean = true, v2: Boolean = false, pad: Int = 0): File {
        val f = Xp3Fixture.xp3(dir, names, compressed, v2, filler = 100 + pad)
        return File(dir, name).also { f.renameTo(it) }
    }

    @Test fun xp3DangChonDaCoStartup_giuNguyen() {
        val dir = tmp.newFolder("a")
        val main = put(dir, "karanoshojo.xp3", listOf("startup.tjs", "x.ks"))
        put(dir, "patch.xp3", listOf("startup.tjs"))
        assertTrue(KirikiriEntryResolver.resolve(main) is EntryResolution.Keep)
    }

    @Test fun xp3ChonSaiKhongCoStartup_doiSangKhoThuongCoStartup() {
        val dir = tmp.newFolder("Kara no Shoujo - AowVN.org")
        val wrong = put(dir, "extra.xp3", listOf("img/a.png"))
        val good = put(dir, "karanoshojo.xp3", listOf("Startup.TJS", "scn/a.ks"), pad = 4000)
        put(dir, "patch.xp3", listOf("startup.tjs"))
        val r = KirikiriEntryResolver.resolve(wrong) as EntryResolution.Use
        assertEquals(good.name, r.entry.name)
    }

    @Test fun banVaChiDuocChonKhiLaKhoDuyNhatLoStartup() {
        val dir = tmp.newFolder("b")
        val main = put(dir, "game.xp3", listOf("image/a.png"))
        val patch = put(dir, "patch.xp3", listOf("startup.tjs"))
        val r = KirikiriEntryResolver.resolve(main) as EntryResolution.Use
        assertEquals(patch.name, r.entry.name)
        assertTrue(r.why.contains("bản vá"))
    }

    @Test fun nhieuBanVaCungCoStartup_khongTuChon() {
        val dir = tmp.newFolder("c")
        val main = put(dir, "game.xp3", listOf("image/a.png"))
        put(dir, "patch.xp3", listOf("startup.tjs")); put(dir, "patch2.xp3", listOf("startup.tjs"))
        assertTrue(KirikiriEntryResolver.resolve(main) is EntryResolution.Keep)
    }

    @Test fun khongXp3NaoCoStartup_coStartupRoiThiDungThuMuc() {
        val dir = tmp.newFolder("d")
        val main = put(dir, "game.xp3", listOf("image/a.png"))
        File(dir, "startup.tjs").writeText("//")
        val r = KirikiriEntryResolver.resolve(main) as EntryResolution.Use
        assertEquals(dir, r.entry)
    }

    @Test fun notFoundLietKeTungXp3_coCoBangNeuTenBam() {
        val dir = tmp.newFolder("Kara no Shoujo - AowVN.org")
        val plain = put(dir, "data.xp3", listOf("img/a.png", "scn/a.ks", "b.png"))
        put(dir, "hashed.xp3", listOf("3f9a1c", "77be02", "c0ffee", "a1b2c3", "d4e5f6", "0a0b0c", "1d1e1f"), pad = 2000)
        val r = KirikiriEntryResolver.resolve(plain) as EntryResolution.NotFound
        assertEquals(2, r.details.size)
        val d = r.details.associateBy { it.substringBefore(' ') }
        assertTrue(d.getValue("data.xp3").contains("3 mục") && d.getValue("data.xp3").contains("scn/a.ks"))
        assertTrue(!d.getValue("data.xp3").contains("băm"))
        val h = d.getValue("hashed.xp3")
        assertTrue(h.contains("7 mục") && h.contains("3f9a1c|77be02|c0ffee|a1b2c3|d4e5f6") && !h.contains("0a0b0c"))
        assertTrue(h.contains("[tên có vẻ băm/mã hóa]"))
        assertTrue(r.details.all { it.length <= 280 })
    }

    @Test fun notFoundDanhDauMaHoaChiKhiMoiXp3DeuCoCo() {
        val all = tmp.newFolder("enc")
        val a = put(all, "a.xp3", listOf("3f9a1c", "77be02"))
        put(all, "patch.xp3", listOf("c0ffee", "a1b2c3"), pad = 500)
        assertTrue((KirikiriEntryResolver.resolve(a) as EntryResolution.NotFound).encrypted)
        val mixed = tmp.newFolder("mix")
        val m = put(mixed, "a.xp3", listOf("3f9a1c", "77be02"))
        put(mixed, "patch.xp3", listOf("scn/b.ks"), pad = 500)
        assertTrue(!(KirikiriEntryResolver.resolve(m) as EntryResolution.NotFound).encrypted)
    }

    @Test fun looksEncrypted_nhanDienTheoTenMuc() {
        assertTrue(!KirikiriEntryResolver.looksEncrypted(null))
        assertTrue(!KirikiriEntryResolver.looksEncrypted(Xp3Index.Result.Names(emptyList(), true)))
        assertTrue(!KirikiriEntryResolver.looksEncrypted(Xp3Index.Result.Names(listOf("a.png", "Scn/B.KS"), true)))
        assertTrue(KirikiriEntryResolver.looksEncrypted(Xp3Index.Result.Names(listOf("a.png", "3f9a1c"), true)))
        assertTrue(!KirikiriEntryResolver.looksEncrypted(Xp3Index.Result.Names(listOf("a.png", "3f9a1c"), false))) // chỉ mục bị cắt: không kết luận
    }

    @Test fun resourceOnlyNamesDoNotClaimEncryption() {
        val plainNames = listOf("image/title.png", "sound/theme.ogg", "readme.txt", "abcdef.png", "123456", "abcdef", "abc12")
        assertTrue(!KirikiriEntryResolver.looksEncrypted(Xp3Index.Result.Names(plainNames, true)))
        val dir = tmp.newFolder("resources-only")
        val main = put(dir, "game.xp3", plainNames)
        val resolution = KirikiriEntryResolver.resolve(main) as EntryResolution.NotFound
        assertTrue(!resolution.encrypted)
        assertTrue(resolution.details.none { it.contains("[tên có vẻ băm/mã hóa]") })
    }

    @Test fun hashedNamesNeedCompleteIndexWithoutReadableScriptsInEveryArchive() {
        val hashed = listOf("folder/3F9A1C", "C:\\private\\77BE02")
        assertTrue(KirikiriEntryResolver.looksEncrypted(Xp3Index.Result.Names(hashed, true)))
        assertTrue(!KirikiriEntryResolver.looksEncrypted(Xp3Index.Result.Names(hashed, false)))
        assertTrue(!KirikiriEntryResolver.looksEncrypted(Xp3Index.Result.Names(hashed + "scenario/MAIN.KS", true)))
        val dir = tmp.newFolder("hash-and-resources")
        val main = put(dir, "main.xp3", hashed)
        put(dir, "patch.xp3", listOf("image/title.png", "sound/theme.ogg"))
        assertTrue(!(KirikiriEntryResolver.resolve(main) as EntryResolution.NotFound).encrypted)
    }

    @Test fun docDuocMoiXp3MaKhongCoStartup_baoNotFound() {
        val dir = tmp.newFolder("Game thieu")
        val main = put(dir, "game.xp3", listOf("image/a.png"), v2 = true)
        put(dir, "patch.xp3", listOf("scn/b.ks"), compressed = false)
        val r = KirikiriEntryResolver.resolve(main) as EntryResolution.NotFound
        assertEquals("Game thieu", r.dirName)
    }

    @Test fun coXp3KhongDocDuoc_giuNguyenKhongChanGame() {
        val dir = tmp.newFolder("e")
        val main = File(dir, "data.xp3").also { it.writeBytes(ByteArray(500) { 3 }) } // mã hóa/định dạng lạ
        put(dir, "patch.xp3", listOf("scn/b.ks"))
        assertTrue(KirikiriEntryResolver.resolve(main) is EntryResolution.Keep)
    }

    @Test fun exeVaKhongCoLoiVao_giuNguyen() {
        val dir = tmp.newFolder("f")
        val exe = File(dir, "game.exe").also { it.writeBytes(ByteArray(100) { 1 }) }
        assertTrue(KirikiriEntryResolver.resolve(exe) is EntryResolution.Keep)
        assertTrue(KirikiriEntryResolver.resolve(null) is EntryResolution.Keep)
    }

    @Test fun thuMucLoiVao_coXp3CoStartupThiChonXp3() {
        val dir = tmp.newFolder("g")
        val good = put(dir, "data.xp3", listOf("startup.tjs"))
        val r = KirikiriEntryResolver.resolve(dir) as EntryResolution.Use
        assertEquals(good.name, r.entry.name)
    }
    @Test fun nhieuKhoThuongCoStartup_chonKhoLonNhatTruBanVa() {
        val dir = tmp.newFolder("multiple-normal")
        val wrong = put(dir, "extra.xp3", listOf("image/a.png"))
        put(dir, "small.xp3", listOf("startup.tjs"), pad = 100)
        val largest = put(dir, "large.xp3", listOf("startup.tjs"), pad = 2000)
        put(dir, "patch.xp3", listOf("startup.tjs"), pad = 5000)
        assertEquals(largest, (KirikiriEntryResolver.resolve(wrong) as EntryResolution.Use).entry)
    }

    @Test fun khoKhongDocDuoc_khongCanKhoThuongKhacCoStartup() {
        val dir = tmp.newFolder("unreadable-with-startup")
        val wrong = File(dir, "broken.xp3").also { it.writeBytes(ByteArray(500) { 3 }) }
        val good = put(dir, "data.xp3", listOf("startup.tjs"))
        assertEquals(good, (KirikiriEntryResolver.resolve(wrong) as EntryResolution.Use).entry)
    }

}
