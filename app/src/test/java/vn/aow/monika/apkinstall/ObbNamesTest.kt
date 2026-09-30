package vn.aow.monika.apkinstall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObbNamesTest {
    @Test fun standardKept() {
        assertTrue(ObbNames.isStandard("main.12.com.foo.game.obb"))
        assertTrue(ObbNames.isStandard("patch.3.com.foo.game.obb"))
        assertFalse(ObbNames.isStandard("data.obb"))
        assertEquals("patch.3.com.foo.game.obb", ObbNames.targetName("patch.3.com.foo.game.obb", "com.foo.game", 9, false))
    }

    @Test fun singleOddNameBecomesMain() {
        assertEquals("main.9.com.foo.game.obb", ObbNames.targetName("data.obb", "com.foo.game", 9, true))
    }

    @Test fun manyOddNamesKept() {
        assertEquals("a.obb", ObbNames.targetName("a.obb", "com.foo.game", 9, false))
    }
}
