package vn.aow.monika

import org.junit.Assert.assertEquals
import org.junit.Test
import vn.aow.monika.library.Game
import vn.aow.monika.library.GameSearch
import java.io.File

class GameSearchTest {
    private fun g(name: String) = Game(File("/x/$name"), name, null, null)
    private val list = listOf(g("Pokémon Emerald"), g("Đấu Trường Sinh Tử"), g("Fire Emblem"), g("Pokemon Black"))

    @Test fun blankReturnsAll() = assertEquals(list, GameSearch.filter(list, "  "))
    @Test fun ignoresAccentAndCase() = assertEquals(listOf("Pokémon Emerald", "Pokemon Black"), GameSearch.filter(list, "POKEMON").map { it.name })
    @Test fun vietnameseD() = assertEquals(listOf("Đấu Trường Sinh Tử"), GameSearch.filter(list, "dau truong").map { it.name })
    @Test fun allWordsMustMatch() = assertEquals(listOf("Pokemon Black"), GameSearch.filter(list, "poke black").map { it.name })
    @Test fun noMatch() = assertEquals(emptyList<Game>(), GameSearch.filter(list, "zelda"))
}
