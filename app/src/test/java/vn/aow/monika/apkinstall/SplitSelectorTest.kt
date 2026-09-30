package vn.aow.monika.apkinstall

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class SplitSelectorTest {
    private fun p(split: String?) = ApkPart(File("x_${split ?: "base"}.apk"), split)
    private fun names(l: List<ApkPart>) = l.map { it.splitName }.toSet()
    private val all = listOf(
        p(null), p("feature_pack"),
        p("config.arm64_v8a"), p("config.armeabi_v7a"), p("config.x86"),
        p("config.mdpi"), p("config.hdpi"), p("config.xhdpi"), p("config.xxhdpi"), p("config.xxxhdpi"), p("config.nodpi"),
        p("config.en"), p("config.vi"),
    )

    @Test fun picksOneAbiOneDensityAllLanguages() {
        val dev = DeviceInfo(34, listOf("arm64-v8a", "armeabi-v7a"), listOf("armeabi-v7a"), 420, 1L shl 30)
        val sel = names(SplitSelector.select(all, dev))
        assertEquals(setOf(null, "feature_pack", "config.arm64_v8a", "config.xxhdpi", "config.nodpi", "config.en", "config.vi"), sel)
    }

    @Test fun v7aDeviceGetsV7a() {
        val dev = DeviceInfo(30, listOf("armeabi-v7a", "armeabi"), listOf("armeabi-v7a"), 240, 1L shl 30)
        val sel = names(SplitSelector.select(all, dev))
        assertEquals(true, "config.armeabi_v7a" in sel)
        assertEquals(true, "config.hdpi" in sel)
        assertEquals(false, "config.arm64_v8a" in sel)
    }

    @Test fun classifyFeatureConfigSplit() {
        assertEquals(SplitSelector.Kind.DENSITY to "xhdpi", SplitSelector.classify("level1.config.xhdpi"))
        assertEquals(SplitSelector.Kind.FEATURE to null, SplitSelector.classify("level1"))
    }
}
