package vn.aow.monika

import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element
import vn.aow.monika.runner.J2ME_MENU_FALLBACK_ICON
import vn.aow.monika.runner.j2meMenuIcon
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * V77: hợp đồng màn chơi Java (J2ME). Robolectric không nạp được lớp `javax.*` của J2ME nên không mở được
 * `MicroActivity`; test đọc thẳng tài nguyên XML của module `j2me/`:
 *  - không còn thanh công cụ trên đầu, chỉ một nút menu nổi có mô tả;
 *  - mọi mục của menu J2ME gốc (kể cả chụp màn hình) đều có icon riêng trong menu Monika.
 */
class J2meScreenContractTest {
    private val android = "http://schemas.android.com/apk/res/android"
    private val j2meRes = File("../j2me/src/main/res")

    private fun parse(path: String): Element {
        val f = File(j2meRes, path)
        assertTrue("Thiếu ${f.path}", f.isFile)
        val dbf = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        return dbf.newDocumentBuilder().parse(f).documentElement
    }

    private fun Element.elements(tag: String): List<Element> =
        (0 until getElementsByTagName(tag).length).map { getElementsByTagName(tag).item(it) as Element }

    private fun Element.attr(name: String): String = getAttributeNS(android, name)

    @Test fun playScreenHasNoTopToolbarAndOneMenuButton() {
        val root = parse("layout/activity_micro.xml")
        val toolbar = root.elements("androidx.appcompat.widget.Toolbar").single()
        assertEquals("gone", toolbar.attr("visibility"))

        val buttons = root.elements("ImageButton")
        assertEquals(1, buttons.size)
        val menu = buttons.single()
        assertEquals("@+id/monika_menu_button", menu.attr("id"))
        assertTrue(menu.attr("contentDescription").isNotBlank())
        val gravity = menu.attr("layout_gravity")
        assertTrue(gravity.contains("top") && gravity.contains("end"))
        // Nút nằm sau lớp phủ của game để nhận chạm trước.
        val kids = (0 until root.childNodes.length).map { root.childNodes.item(it) }.filterIsInstance<Element>()
        assertTrue(kids.indexOf(menu) > kids.indexOfFirst { it.attr("id") == "@+id/overlay" })

        val strings = parse("values/strings.xml").elements("string")
        assertTrue(strings.any { it.getAttribute("name") == "monika_menu_button" && it.textContent.isNotBlank() })
    }

    @Test fun everyJ2meMenuItemHasMonikaIcon() {
        val leaves = parse("menu/midlet_displayable.xml").elements("item")
            .filter { it.elements("menu").isEmpty() }
            .map { it.attr("id").removePrefix("@+id/") }
        assertTrue(leaves.contains("action_take_screenshot"))
        assertTrue(leaves.contains("action_exit_midlet"))
        val missing = leaves.filter { j2meMenuIcon(it) == J2ME_MENU_FALLBACK_ICON }
        assertEquals("Mục menu J2ME chưa có icon trong menu Monika: $missing", emptyList<String>(), missing)
    }
}
