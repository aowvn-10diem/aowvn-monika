package vn.aow.monika

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vn.aow.monika.feed.Post
import vn.aow.monika.library.GameInfoResolver

/** Khớp tên file game ↔ danh mục bài thật của aow.vn (ảnh chụp 299 bài, test/resources/aow-index.json). */
class GameMatchTest {
    @Serializable private data class Row(val id: String, val title: String, val labels: List<String>)

    private val posts: List<Post> = Json.decodeFromString(ListSerializer(Row.serializer()), javaClass.getResource("/aow-index.json")!!.readText())
        .map { Post(it.id, it.title, "", "", it.labels, null, "") }
    private val nds = listOf("Game NDS Việt Hóa")
    private val gba = listOf("Game GBA Việt Hóa")
    private val java = listOf("Game Java Việt Hóa")
    private val all = setOf("Game NDS Việt Hóa", "Game GBA Việt Hóa", "Game PS1", "Game PS & PSP Việt Hóa", "RPGmaker Việt Hoá", "Game Java Việt Hóa", "Visual Novel Việt Hóa")

    private fun find(file: String, labels: List<String>, romTitle: String? = null): String? =
        GameInfoResolver.match(listOfNotNull(GameInfoResolver.cleanName(file), romTitle?.let(GameInfoResolver::cleanName)), labels, posts, all - labels.toSet())?.title

    private fun assertMatch(expectPart: String, file: String, labels: List<String>, romTitle: String? = null) {
        val t = find(file, labels, romTitle)
        assert(t != null && t.contains(expectPart, ignoreCase = true)) { "$file → $t (mong: $expectPart)" }
    }

    @Test fun realRomNames() {
        assertMatch("HeartGold", "Pokemon - HeartGold Version (USA)", nds)
        assertMatch("Castlevania", "Castlevania - Dawn of Sorrow (USA)", nds)
        assertMatch("Plantium", "Pokemon Platinum (USA)", nds)
        assertMatch("Chrono Trigger", "Chrono Trigger (USA) (En,Fr)", nds)
        assertMatch("Megaman ZX", "Mega Man ZX (USA)", nds)
        assertMatch("Ghost Trick", "1234 - Ghost_Trick_Phantom_Detective", nds)
        assertMatch("Black 2", "Pokemon Black 2", nds)
        assertMatch("Rune Factory 3", "Rune Factory 3 - A Fantasy Harvest Moon (USA)", nds)
        assertMatch("Emerald", "Pokemon - Emerald Version (USA, Europe)", gba)
        assertMatch("Minish Cap", "Legend of Zelda, The - The Minish Cap (USA)", gba)
        assertMatch("Adventure Time", "Adventure_Time", java)
        // Tên file vô nghĩa → dùng tên trong ROM.
        assertMatch("Maple Story", "0421", nds, romTitle = "MapleStory DS")
    }

    @Test fun noFalseCovers() {
        assertNull(find("Super Mario 64 DS (USA)", nds))
        assertNull(find("Mario Kart DS", nds))
        assertNull(find("Tetris", gba))
        // Game NDS không được lấy ảnh bài GBA cùng tên.
        assertEquals(null, find("Pokemon - Emerald Version", nds)?.takeIf { it.contains("Emerald") })
    }
}
