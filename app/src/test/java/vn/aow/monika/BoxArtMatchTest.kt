package vn.aow.monika

import org.junit.Assert.assertNull
import org.junit.Test
import vn.aow.monika.library.BoxArts

/** Khớp tên game ↔ tên ảnh bìa libretro-thumbnails (danh sách thật NDS + GBA). */
class BoxArtMatchTest {
    private fun list(n: String) = javaClass.getResource("/boxarts-$n.txt")!!.readText().lines().filter { it.isNotBlank() }
    private val nds = list("nds")
    private val gba = list("gba")

    private fun check(expect: String, names: List<String>, arts: List<String>) {
        val hit = BoxArts.best(names, arts)
        assert(hit != null && hit.contains(expect, ignoreCase = true)) { "$names → $hit (mong: $expect)" }
    }

    @Test fun realNames() {
        check("HeartGold Version (USA)", listOf("0001", "POKEMON HeartGold Version"), nds)
        check("Mario Kart DS", listOf("Mario Kart DS"), nds)
        check("New Super Mario Bros", listOf("New Super Mario Bros"), nds)
        check("Chrono Trigger", listOf("chrono_trigger"), nds)
        check("Castlevania - Dawn of Sorrow", listOf("Castlevania Dawn of Sorrow"), nds)
        check("Emerald Version (USA, Europe)", listOf("Pokemon Emerald"), gba)
        check("Minish Cap", listOf("zelda minish cap"), gba)
        check("FireRed Version (USA)", listOf("Pokemon Fire Red"), gba)
    }

    @Test fun noGuessing() {
        assertNull(BoxArts.best(listOf("Game của tôi"), nds))
        assertNull(BoxArts.best(listOf("0001"), nds))
    }
}
