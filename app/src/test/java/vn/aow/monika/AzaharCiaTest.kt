package vn.aow.monika

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.aow.monika.azahar.AzaharCia
import java.io.File

/**
 * V205: `.cia` là gói cài đặt → màn chơi phải chuyển sang luồng cài thay vì `NativeLibrary.run`.
 * Màn cài chỉ được xoá bản sao Monika tự chép vào bộ đệm, không xoá `.cia` gốc của người chơi.
 */
class AzaharCiaTest {
    @Test fun recognizesCiaExtensionRegardlessOfCase() {
        assertTrue(AzaharCia.isCia("Pokemon.cia"))
        assertTrue(AzaharCia.isCia("/storage/emulated/0/games/POKEMON.CIA"))
        assertTrue(AzaharCia.isCia("C:\\games\\game.CiA"))
        for (path in listOf("game.3ds", "game.cci", "game.cxi", "game.3dsx", "game.cia.zip", "cia", "game.cia.txt")) {
            assertFalse(path, AzaharCia.isCia(path))
        }
    }

    @Test fun stagedNameKeepsOriginalNameAndAddsIndex() {
        assertEquals("0-Pokemon.cia", AzaharCia.stagedName(0, "Pokemon.cia"))
        assertEquals("3-a_b.cia", AzaharCia.stagedName(3, "a/b.cia"))
        assertEquals("1-a_b.cia", AzaharCia.stagedName(1, "a\\b.cia"))
    }

    @Test fun onlyFilesInsideStagingDirCountAsStagedCopies() {
        val staging = AzaharCia.stagingDir(File("/data/app/cache"))
        assertTrue(AzaharCia.isStagedCopy(staging, File(staging, "0-game.cia")))
        // Tệp người chơi trong thư viện — không phải bản sao nên không được xoá.
        assertFalse(AzaharCia.isStagedCopy(staging, File("/storage/emulated/0/roms/game.cia")))
        // Thư mục anh em trùng tiền tố ("cia2") không được tính là nằm trong bộ đệm.
        assertFalse(AzaharCia.isStagedCopy(staging, File("/data/app/cache/cia2/game.cia")))
        // Chính thư mục bộ đệm không phải tệp bên trong nó.
        assertFalse(AzaharCia.isStagedCopy(staging, staging))
    }
}
