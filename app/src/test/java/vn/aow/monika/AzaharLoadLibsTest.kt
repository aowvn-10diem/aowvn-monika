package vn.aow.monika

import org.junit.Assert.assertEquals
import org.junit.Test
import vn.aow.monika.azahar.AzaharModule
import vn.aow.monika.config.ModuleDef

/** V118: hook phải để initializer tự nạp, không được vào `System.load` trước `createLogFile`. */
class AzaharLoadLibsTest {
    private val pack = listOf(
        "libcitra-android.so", "libSPIRV-Tools-shared.so", "libyuv.so", "libhook_impl.so",
        "libfile_redirect_hook.so", "libmain_hook.so", "libgsl_alloc_hook.so", "needed.txt", "version",
    )
    private val hooks = ModuleDef(version = "t").hookLibs

    @Test fun skipsHookLibsByDefault() {
        assertEquals(listOf("libSPIRV-Tools-shared.so", "libyuv.so"), AzaharModule.libsToLoad(pack, hooks))
    }

    @Test fun customDriverMustNotLoadHooksBeforeCreateLogFile() {
        val module = ModuleDef(version = "t", customGpuDriver = true)
        assertEquals(
            "Bật driver tùy biến cũng phải để initializeGpuDriver tự nạp hook, sau createLogFile",
            listOf("libSPIRV-Tools-shared.so", "libyuv.so"),
            AzaharModule.libsToLoad(pack, module.hookLibs),
        )
    }

    @Test fun respectsConfiguredHookNamesAndKeepsOtherDependencies() {
        val configuredHooks = hooks + "libextra_driver_hook.so"
        assertEquals(
            listOf("libSPIRV-Tools-shared.so", "libaudio.so", "libyuv.so"),
            AzaharModule.libsToLoad(pack + listOf("libextra_driver_hook.so", "libaudio.so"), configuredHooks),
        )
    }

    @Test fun defaultHookLibsCoverShippedPack() {
        assertEquals(setOf("libhook_impl.so", "libfile_redirect_hook.so", "libmain_hook.so", "libgsl_alloc_hook.so"), hooks.toSet())
    }
}
