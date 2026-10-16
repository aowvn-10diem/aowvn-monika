package vn.aow.monika

import org.junit.Assert.assertEquals
import org.junit.Test
import vn.aow.monika.azahar.AzaharModule
import vn.aow.monika.config.ModuleDef

/** V118: không `System.load` thư viện lớp nạp driver (adrenotools) khi không dùng driver tùy biến. */
class AzaharLoadLibsTest {
    private val pack = listOf(
        "libcitra-android.so", "libSPIRV-Tools-shared.so", "libyuv.so", "libhook_impl.so",
        "libfile_redirect_hook.so", "libmain_hook.so", "libgsl_alloc_hook.so", "needed.txt", "version",
    )
    private val hooks = ModuleDef(version = "t").hookLibs

    @Test fun skipsHookLibsByDefault() {
        assertEquals(listOf("libSPIRV-Tools-shared.so", "libyuv.so"), AzaharModule.libsToLoad(pack, hooks, customGpuDriver = false))
    }

    @Test fun loadsHookLibsWhenCustomDriverOn() {
        val got = AzaharModule.libsToLoad(pack, hooks, customGpuDriver = true)
        assertEquals(setOf("libSPIRV-Tools-shared.so", "libyuv.so", "libhook_impl.so", "libfile_redirect_hook.so", "libmain_hook.so", "libgsl_alloc_hook.so"), got.toSet())
    }

    @Test fun defaultHookLibsCoverShippedPack() {
        assertEquals(setOf("libhook_impl.so", "libfile_redirect_hook.so", "libmain_hook.so", "libgsl_alloc_hook.so"), hooks.toSet())
    }
}
