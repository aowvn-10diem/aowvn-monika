package vn.aow.monika.diag

import org.junit.Assert.assertEquals
import org.junit.Test

class ComponentsTest {
    private fun phien(kind: String, core: String) =
        Diagnostics.Session(pid = 4242, startedAt = 1_700_000_000_000L, kind = kind, core = core)

    @Test fun renpyTheoVetBegin() {
        assertEquals("engine:renpy", Components.of(null, null, "begin: renpy · renpy8 · Ren'Py\nstage: playing"))
        assertEquals("engine:renpy", Components.of(null, null, "at org.renpy.android.PythonSDLActivity.onCreate"))
    }

    @Test fun rgssTheoVetBegin() {
        assertEquals("engine:rgss", Components.of(null, null, "begin: rgss · mkxp-z · RPG Maker XP/VX/Ace\nstage: playing"))
    }

    @Test fun kirikiriTheoVetBegin() {
        assertEquals("engine:kirikiri", Components.of(null, null, "begin: kirikiri · kirikiroid2-yuri · Kirikiri"))
    }

    @Test fun goiTaiThemVanLaPack() {
        assertEquals("pack:rgss", Components.of(null, null, "tải pack:rgss 45%"))
    }

    @Test fun khongNhanDienThiRong() {
        assertEquals("", Components.of(null, null, "không có gì liên quan"))
    }

    /** Lõi citra libretro từng bị gắn `engine:azahar` vì luật khớp chữ "citra" chạy trước luật libretro (#610). */
    @Test fun loiCitraLibretroKhongPhaiAzahar() {
        assertEquals(
            "engine:libretro:citra",
            Components.of(null, phien("libretro", "citra"), "at com.swordfish.libretrodroid.GLRetroView\$RenderLifecycleObserver.onResume"),
        )
        assertEquals("engine:libretro", Components.of(null, null, "abort message: citra_libretro_android.so"))
        assertEquals("engine:libretro:citra", Components.of(null, null, "citra"))
    }

    /** Engine Azahar nhúng vẫn phải là `engine:azahar`, kể cả khi stack chỉ có lớp JNI của engine. */
    @Test fun loiEngineAzaharVanLaAzahar() {
        assertEquals("engine:azahar", Components.of(null, phien("azahar", "azahar"), "stage: core-error: ErrorSystemFiles"))
        assertEquals("engine:azahar", Components.of(null, null, "at org.citra.citra_emu.NativeLibrary.run(Native method)"))
        assertEquals("engine:azahar", Components.of(null, null, "azahar · setUp: createConfigFile"))
    }

    /** Lõi khác chạy qua :libretrodroid giữ nguyên nhãn cũ. */
    @Test fun loiPpssppLibretroGiuNguyenNhan() {
        assertEquals(
            "engine:libretro:ppsspp",
            Components.of(null, phien("libretro", "ppsspp"), "at com.swordfish.libretrodroid.GLRetroView.onDrawFrame"),
        )
        assertEquals("engine:libretro", Components.of(null, null, "abort message: ppsspp_libretro_android.so"))
    }
}
