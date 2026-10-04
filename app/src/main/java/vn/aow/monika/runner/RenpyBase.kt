package vn.aow.monika.runner

import java.io.File

/** Thư mục gốc của game Ren'Py = thư mục chứa `game/`. Hàm thuần để test được. */
object RenpyBase {
    /**
     * [entry] có thể là thư mục game, thư mục `game/` bên trong, hoặc một file trong cây game (.sh, .exe, .py, .rpa…).
     * Đi lên tối đa [MAX_UP] cấp tìm thư mục có `game/`; không thấy → null.
     */
    fun resolve(entry: File?): File? {
        var cur: File? = entry?.let { if (it.isDirectory) it else it.parentFile }
        var up = 0
        while (cur != null && up <= MAX_UP) {
            if (File(cur, "game").isDirectory) return cur
            if (cur.name == "game" && cur.parentFile != null) return cur.parentFile // chọn thẳng thư mục game/
            cur = cur.parentFile
            up++
        }
        return null
    }

    private const val MAX_UP = 3
}
