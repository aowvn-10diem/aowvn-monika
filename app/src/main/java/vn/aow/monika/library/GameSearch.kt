package vn.aow.monika.library

import java.text.Normalizer

/** Tìm game trong Thư viện theo tên: không phân biệt hoa thường, dấu (gõ "pokemon" ra "Pokémon"), nhiều từ = phải khớp đủ mọi từ. */
object GameSearch {
    fun fold(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace('đ', 'd')

    /** Chuỗi để so khớp của 1 game: tên hiển thị + tên bài viết + tên hệ máy. */
    private fun haystack(g: Game) = fold(listOfNotNull(g.name, g.meta?.title, g.system?.name).joinToString(" "))

    fun filter(games: List<Game>, query: String): List<Game> {
        val words = fold(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return games
        return games.filter { g -> val h = haystack(g); words.all { it in h } }
    }
}
