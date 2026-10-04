package vn.aow.monika.diag

import org.junit.Assert.assertEquals
import org.junit.Test

class ComponentsTest {
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
}
