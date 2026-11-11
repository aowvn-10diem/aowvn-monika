package vn.aow.monika

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.aow.monika.azahar.AzaharFlow

/** V205: `.cia` phải đi luồng cài NAND, không đi luồng chạy ROM của `AzaharActivity`. */
class AzaharFlowTest {
    @Test
    fun nhanDienGoiCaiCia() {
        assertTrue(AzaharFlow.isInstallerPackage("game.cia"))
        assertTrue(AzaharFlow.isInstallerPackage("GAME.CIA"))
        assertTrue(AzaharFlow.isInstallerPackage("/sdcard/Download/update-1.2.cia"))
        assertTrue(AzaharFlow.isInstallerPackage("C:\\roms\\game.cia"))
    }

    @Test
    fun khongNhamRomHayThuMucThanhGoiCai() {
        assertFalse(AzaharFlow.isInstallerPackage("game.3ds"))
        assertFalse(AzaharFlow.isInstallerPackage("game.cci"))
        assertFalse(AzaharFlow.isInstallerPackage("game.cxi"))
        assertFalse(AzaharFlow.isInstallerPackage("game.3dsx"))
        assertFalse(AzaharFlow.isInstallerPackage("/sdcard/Download/cia"))
        assertFalse(AzaharFlow.isInstallerPackage("cias/game"))
        assertFalse(AzaharFlow.isInstallerPackage(""))
    }
}
