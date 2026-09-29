package vn.aow.monika

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lớp Kotlin nhúng phải khớp đúng tên/chữ ký mà thư viện native Azahar tra cứu trong JNI_OnLoad
 * (id_cache.cpp). Sai một chữ = native sập ngay khi nạp → test này chặn từ lúc dựng.
 */
class AzaharContractTest {
    private fun cls(n: String) = Class.forName(n)

    @Test fun nativeLibraryCallbacks() {
        val c = cls("org.citra.citra_emu.NativeLibrary")
        val names = c.declaredMethods.map { it.name }.toSet()
        for (n in listOf("onCoreError", "isPortraitMode", "displayAlertMsg", "exitEmulationActivity", "createFile", "createDir", "openContentUri",
            "getFilesName", "getUserDirectory", "getSize", "getBuildFlavor", "fileExists", "isDirectory", "copyFile", "renameFile", "deleteDocument",
            "requestCameraPermission", "requestMicPermission", "onCompressProgress", "addNetPlayMessage", "clearChat", "isUsingAngleForOpenGL")) {
            assertTrue("thiếu NativeLibrary.$n", n in names)
        }
        for (n in listOf("run", "surfaceChanged", "surfaceDestroyed", "doFrame", "onGamePadEvent", "onGamePadMoveEvent", "onTouchEvent", "onTouchMoved",
            "setUserDirectory", "createConfigFile", "reloadSettings", "saveState", "loadState", "getSavestateInfo", "pauseEmulation", "unPauseEmulation",
            "stopEmulation", "isRunning", "updateFramebuffer", "initializeGpuDriver", "setTemporaryFrameLimit", "disableTemporaryFrameLimit")) {
            assertTrue("thiếu external NativeLibrary.$n", c.declaredMethods.any { it.name == n && java.lang.reflect.Modifier.isNative(it.modifiers) })
        }
    }

    @Test fun helperClassesExist() {
        for (n in listOf("org.citra.citra_emu.model.GameInfo", "org.citra.citra_emu.features.cheats.model.Cheat",
            "org.citra.citra_emu.applets.SoftwareKeyboard", "org.citra.citra_emu.applets.SoftwareKeyboard\$KeyboardConfig",
            "org.citra.citra_emu.applets.SoftwareKeyboard\$KeyboardData", "org.citra.citra_emu.applets.MiiSelector",
            "org.citra.citra_emu.applets.MiiSelector\$MiiSelectorConfig", "org.citra.citra_emu.applets.MiiSelector\$MiiSelectorData",
            "org.citra.citra_emu.utils.DiskShaderCacheProgress", "org.citra.citra_emu.utils.CiaInstallWorker",
            "org.citra.citra_emu.camera.StillImageCameraHelper", "org.citra.citra_emu.NativeLibrary\$CoreError",
            "org.citra.citra_emu.NativeLibrary\$SaveStateInfo")) assertNotNull(n, cls(n))
    }

    @Test fun coreErrorValuesMatchNative() {
        val e = cls("org.citra.citra_emu.NativeLibrary\$CoreError").enumConstants
        assertTrue(e.size == 18)
    }

    @Test fun cheatAndCiaHooks() {
        val engine = cls("org.citra.citra_emu.features.cheats.model.CheatEngine")
        for (n in listOf("loadCheatFile", "saveCheatFile", "getCheats", "addCheat", "removeCheat", "updateCheat"))
            assertTrue("thiếu CheatEngine.$n", engine.declaredMethods.any { it.name == n && java.lang.reflect.Modifier.isNative(it.modifiers) })
        val cia = cls("org.citra.citra_emu.utils.CiaInstallWorker")
        assertTrue(cia.declaredMethods.any { it.name == "installCIA" && java.lang.reflect.Modifier.isNative(it.modifiers) })
        assertNotNull(cia.getDeclaredMethod("setProgressCallback", Int::class.java, Int::class.java))
        val st = cls("org.citra.citra_emu.NativeLibrary\$InstallStatus").enumConstants.map { it.toString() }
        for (n in listOf("Success", "ErrorFailedToOpenFile", "ErrorFileNotFound", "ErrorAborted", "ErrorInvalid", "ErrorEncrypted")) assertTrue(n in st)
    }
}
