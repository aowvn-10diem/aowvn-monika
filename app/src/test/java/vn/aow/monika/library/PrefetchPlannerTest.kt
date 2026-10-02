package vn.aow.monika.library

import org.junit.Assert.assertEquals
import org.junit.Test
import vn.aow.monika.config.ModuleDef
import vn.aow.monika.config.MonikaConfig
import vn.aow.monika.config.SystemDef

class PrefetchPlannerTest {
    private val cfg = MonikaConfig(
        systems = listOf(
            SystemDef("gba", "GBA", "libretro", core = "mgba", extensions = listOf("gba"), labels = listOf("Game GBA")),
            SystemDef("3ds", "3DS", "libretro", core = "citra", extensions = listOf("3ds"), labels = listOf("Game 3DS"), engine = "azahar"),
        ),
        modules = mapOf("azahar" to ModuleDef("1"), "archive" to ModuleDef("1")),
        prefetchByExtension = mapOf("7z" to listOf("archive"), "jar" to listOf("java")),
    )
    private fun core(id: String) = PackRef(PackRef.Kind.CORE, id)
    private fun pack(id: String) = PackRef(PackRef.Kind.PACK, id)

    @Test fun labelPicksCore() = assertEquals(listOf(core("mgba")), PrefetchPlanner.plan(cfg, listOf("game gba"), null))
    @Test fun engineWinsOverCoreWhenModuleExists() = assertEquals(listOf(pack("azahar")), PrefetchPlanner.plan(cfg, listOf("Game 3DS"), null))
    @Test fun extensionFallbackWhenNoLabel() = assertEquals(listOf(core("mgba")), PrefetchPlanner.plan(cfg, emptyList(), "x.GBA"))
    @Test fun archiveNeedsExtractorPack() = assertEquals(listOf(core("mgba"), pack("archive")), PrefetchPlanner.plan(cfg, listOf("Game GBA"), "a.7z"))
    @Test fun packMissingFromModulesIsIgnored() = assertEquals(emptyList<PackRef>(), PrefetchPlanner.plan(cfg, emptyList(), "a.jar"))
    @Test fun noSignalNoPlan() = assertEquals(emptyList<PackRef>(), PrefetchPlanner.plan(cfg, emptyList(), "readme.txt"))
}
